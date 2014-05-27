package de.ncoder.studipsync;

import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.data.StudipFile;
import de.ncoder.studipsync.storage.Storage;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.StudipException;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Syncer {
	private static final Logger log = LoggerFactory.getLogger(Syncer.class);

	private final StudipAdapter adapter;
	private final Storage storage;
	private Marker marker;
	private CheckLevel checkLevel;

	public Syncer(StudipAdapter adapter, Storage storage) {
		this.adapter = adapter;
		this.storage = storage;
	}

	public void init() throws StudipException {
		adapter.init();
		adapter.doLogin();
	}

	public void close() throws IOException {
		adapter.close();
		storage.close();
	}

	public synchronized void sync() throws StudipException, IOException {
		final List<Seminar> seminars;

		//Load seminars
		init();
		seminars = adapter.getSeminars();
		log.info(seminars.size() + " seminar" + (seminars.size() != 1 ? "s" : ""));

		//Sync seminars
		sync(seminars);
	}

	public synchronized void sync(List<Seminar> seminars) throws StudipException, IOException {
		List<StudipException> exceptions = new ArrayList<>();
		for (ListIterator<Seminar> iterator = seminars.listIterator(); iterator.hasNext(); ) {
			Seminar seminar = iterator.next();
			marker = MarkerFactory.getMarker(seminar.getID());
			log.info(marker, (iterator.previousIndex() + 1) + "/" + seminars.size() + " " + seminar.getName());
			try {
				syncSeminar(seminar);
			} catch (StudipException e) {
				log.error(marker, "\t", e);
				exceptions.add(e);
			}
		}

		if (!exceptions.isEmpty()) {
			StudipException ex = new StudipException("Not all seminars are in sync");
			for (StudipException suppressed : exceptions) {
				ex.addSuppressed(suppressed);
			}
			throw ex;
		}
	}

	public void syncSeminar(final Seminar seminar) throws StudipException, IOException {
		try {
			//Check for changes
			if (seminar.hasChangedFiles()) {
				log.info(marker, "\tDownloading changes");
				long changesAfter = 0; //TODO
				storage.store(adapter, seminar, changesAfter);
			}
			//Check if seminar is in sync
			log.info("\tChecking if seminar is in sync...");
			if (!isSeminarInSync(seminar)) {
				log.info(marker, "\tSeminar is not in sync " + (seminar.hasChangedFiles() ?
						"after downloading only changes" : "but StudIP didn't report any changes"));
				//Download all files
				log.info(marker, "\tDownloading complete .zip file");
				storage.store(adapter, seminar);

				if (!isSeminarInSync(seminar)) {
					throw new StudipException("IllegalState: Seminar not synchronized after downloading every file!");
				}
			}
		} catch (StudipException ex) {
			ex.put("download.seminar", seminar);
			throw ex;
		}
	}

	public boolean isSeminarInSync(Seminar seminar) throws IOException, StudipException {
		if (!checkLevel.includes(CheckLevel.Count)) {
			return true;
		}

		//List downloads
		final List<StudipFile> studipFiles = new ArrayList<>(seminar.getFiles());
		if (studipFiles.isEmpty()) {
			//No downloads - nothing to do
			return true;
		}

		//List local files
		final List<Path> localFiles = new LinkedList<>();
		final Path storagePath = storage.resolve(seminar);
		if (!Files.exists(storagePath)) {
			//No local files despite available downloads
			log.info(marker, "\tSeminar is empty!");
			return false;
		}
		Files.walkFileTree(storagePath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				localFiles.add(file);
				return FileVisitResult.CONTINUE;
			}
		});

		//Count local files
		if (localFiles.size() < studipFiles.size()) {
			// Missing files
			log.warn(marker, "\tSeminar has only " + localFiles.size() + " local file(s) of " + studipFiles.size() + " online file(s).");
			return false;
		} else if (localFiles.size() > studipFiles.size()) {
			// Ignore surplus files
			log.debug(marker, "\tSeminar has deleted file(s) left! " + localFiles.size() + " local file(s) and " + studipFiles.size() + " online file(s).");
		}

		//Check local files
		return areFilesInSync(studipFiles, localFiles);
	}

	public boolean areFilesInSync(List<StudipFile> studipFiles, List<Path> localFiles) throws IOException, StudipException {
		if (!checkLevel.includes(CheckLevel.Files)) {
			return true;
		}
		for (StudipFile studipFile : studipFiles) {
			//Find matching candidates
			List<Path> localCandidates = new LinkedList<>();
			for (Path local : localFiles) {
				// Check candidate name
				if (local.getName(local.getNameCount() - 1).toString().equals(studipFile.getFileName())) {
					if (!localCandidates.isEmpty()) {
						//Already found a candidate
						//TODO check candidate count matches nr of downloads with same name
						log.debug(marker, "\tLocal files " + localCandidates + " and " + local + " match " + studipFile + "!");
					}
					localCandidates.add(local);

					//Check LastModifiedTime
					if (!checkLevel.includes(CheckLevel.ModTime)) {
						continue;
					}
					Date localLastMod = new Date(Files.getLastModifiedTime(local).toMillis());
					if (!localLastMod.after(studipFile.getLastModified())) {
						//Candidate *potentially* outdated
						log.warn(marker, "\tLocal file " + local + "(" + localLastMod + ") older than online Version " + studipFile + "(" + studipFile.getLastModified() + ")!");
						return false;
					}
				}
			}

			//Require at least one candidate
			if (localCandidates.isEmpty()) {
				//No candidates found
				log.warn(marker, "\tNo local file matching " + studipFile + " (~" + studipFile.getFileName() + ")!");
				return false;
			}
		}
		return true;
	}

	public static boolean checkFilesEqual(Path fileA, Path fileB) {
		try {
			if (!Files.exists(fileA) || !Files.exists(fileB)) {
				return Files.exists(fileA) == Files.exists(fileB);
			}
			if (Files.size(fileA) != Files.size(fileB)) {
				return false;
			}
			byte[] srcBuf = new byte[1024];
			byte[] dstBuf = new byte[1024];
			try (InputStream srcIn = Files.newInputStream(fileA); InputStream dstIn = Files.newInputStream(fileB)) {
				if (srcIn.read(srcBuf) != dstIn.read(dstBuf)) {
					return false;
				}
				if (!Arrays.equals(srcBuf, dstBuf)) {
					return false;
				}
			}
			return true;
		} catch (IOException e) {
			log.warn("Could not compare files " + fileA + " and " + fileB, e);
			return false;
		}
	}

	public CheckLevel getCheckLevel() {
		return checkLevel;
	}

	public void setCheckLevel(CheckLevel checkLevel) {
		this.checkLevel = checkLevel;
	}

	public static enum CheckLevel implements Comparable<CheckLevel> {
		None,
		Count,
		Files,
		ModTime,
		All;

		public static CheckLevel Default = All;

		public static CheckLevel get(String level) throws ParseException {
			if (level != null) {
				try {
					return valueOf(level);
				} catch (IllegalArgumentException earg) {
					try {
						return CheckLevel.values()[Integer.parseInt(level)];
					} catch (NumberFormatException enumb) {
						ParseException pe = new ParseException(level + " is not a CheckLevel.");
						pe.initCause(earg);
						pe.addSuppressed(enumb);
						throw pe;
					}
				}
			} else {
				return Default;
			}
		}

		public boolean includes(CheckLevel other) {
			return compareTo(other) >= 0;
		}
	}

	public StudipAdapter getAdapter() {
		return adapter;
	}

	public Storage getStorage() {
		return storage;
	}
}
