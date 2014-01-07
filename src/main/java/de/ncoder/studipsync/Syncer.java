package de.ncoder.studipsync;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static de.ncoder.studipsync.studip.StudipAdapter.PAGE_DOWNLOADS;
import static de.ncoder.studipsync.studip.StudipAdapter.PAGE_DOWNLOADS_LATEST;

public class Syncer {
    private static final Logger log = LoggerFactory.getLogger(Syncer.class);

    private final StudipAdapter adapter;
    private final Storage storage;
    private final ReentrantLock browserLock = new ReentrantLock();
    private Marker marker;
    private CheckLevel checkLevel;

    public Syncer(StudipAdapter adapter, Storage storage) {
        this.adapter = adapter;
        this.storage = storage;
    }

    public synchronized void sync() throws StudipException, InterruptedException {
        final List<Seminar> seminars;

        //Access seminars
        browserLock.lock();
        adapter.init();
        adapter.doLogin();
        try {
            seminars = adapter.parseSeminars();
        } finally {
            browserLock.unlock();
        }
        log.info(seminars.size() + " seminars");

        //Find seminars
        List<StudipException> exceptions = new ArrayList<>();
        for (Seminar seminar : seminars) {
            marker = MarkerFactory.getMarker(seminar.getID());
            try {
                syncSeminar(seminar, false);
            } catch (StudipException e) {
                log.error(marker, "Couldn't synchronize", e);
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

    public void syncSeminar(final Seminar seminar, boolean forceAbsolute) throws StudipException {
        try {
            //Find downloads
            boolean hasDiff = false;
            browserLock.lock();
            try {
                log.info(marker, seminar.getFullName() + (forceAbsolute ? ", absolute" : ""));
                adapter.selectSeminar(seminar);
                List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS, true);
                log.info(marker, "  Found " + downloads.size() + " downloadable files");
                for (final Download download : downloads) {
                    if (download.getLevel() == 0) {
                        try {
                            final InputStream src;
                            final boolean downloadDiff;
                            if (forceAbsolute) {
                                //Absolute forced
                                downloadDiff = false;
                                src = adapter.startDownload(download, false);
                                log.info(marker, "  abs: " + download.getFileName());
                            } else if (download.isChanged()) {
                                //Changed data
                                downloadDiff = true;
                                src = adapter.startDownload(download, true);
                                log.info(marker, "  par: " + download.getFileName());
                            } else {
                                //Nothing changed
                                downloadDiff = true;
                                src = null;
                                log.info(marker, "  ign: " + download.getFileName());
                            }
                            if (src != null) {
                                storage.store(download, src, downloadDiff);
                            }
                            if (downloadDiff) {
                                hasDiff = true;
                            }
                        } catch (IOException e) {
                            log.warn(marker, "Couldn't download " + download, e);
                            hasDiff = true;
                        }
                    }
                }
            } finally {
                browserLock.unlock();
            }
            final boolean isAbsolute = !hasDiff || forceAbsolute; //final Version of hasDiff

            //Check downloads
            if (!isSeminarInSync(seminar)) {
                log.info(marker, "NOT IN-SYNC");
                if (isAbsolute) {
                    throw new StudipException("Could not synchronize Seminar " + seminar + ". Local data is different from online data after full download.");
                } else {
                    syncSeminar(seminar, true);
                }
            } else {
                log.info(marker, "FINISHED " + seminar.getName());
            }
        } catch (IOException e) {
            throw new StudipException("Could not synchronize Seminar " + seminar + ".", e);
        } catch (StudipException ex) {
            ex.put("download.seminar", seminar);
            ex.put("download.forceAbsolute", forceAbsolute);
            throw ex;
        }
    }

    public boolean isSeminarInSync(Seminar seminar) throws IOException, StudipException {
        if (!checkLevel.includes(CheckLevel.Count)) {
            return true;
        }

        //List downloads
        final List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS_LATEST, false);
        if (downloads.isEmpty()) {
            //No downloads - nothing to do
            return true;
        }

        //List local files
        final List<Path> localFiles = new LinkedList<>();
        final Path storagePath = storage.resolve(seminar);
        if (!Files.exists(storagePath)) {
            //No local files despite available downloads
            log.info(marker, "Seminar is empty!");
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
        if (localFiles.size() < downloads.size()) {
            // Missing files
            log.warn(marker, "Seminar has only " + localFiles.size() + " local files of " + downloads.size() + " online files.");
            return false;
        } else {
            // Ignore surplus files
            log.debug(marker, "Seminar has deleted files left! " + localFiles.size() + " local files and " + downloads.size() + " online files.");
        }

        //Check local files
        if (!areFilesInSync(downloads, localFiles)) {
            return false;
        }

        return true;
    }

    public boolean areFilesInSync(List<Download> downloads, List<Path> localFiles) throws IOException {
        if (!checkLevel.includes(CheckLevel.Files)) {
            return true;
        }
        for (Download download : downloads) {
            //Find matching candidates
            List<Path> localCandidates = new LinkedList<>();
            for (Path local : localFiles) {
                // Check candidate name
                if (local.getName(local.getNameCount() - 1).toString().equals(download.getFileName())) {
                    if (!localCandidates.isEmpty()) {
                        //Already found a candidate
                        log.debug(marker, "Local files " + localCandidates + " and " + local + " match " + download + "!");
                    }
                    localCandidates.add(local);

                    //Check LastModifiedTime
                    if (!checkLevel.includes(CheckLevel.ModTime)) {
                        continue;
                    }
                    Date localLastMod = new Date(Files.getLastModifiedTime(local).toMillis());
                    if (!localLastMod.after(download.getLastModified())) {
                        //Candidate *potentially* outdated
                        log.warn(marker, "Local file " + local + "(" + localLastMod + ") older than online Version " + download + "(" + download.getLastModified() + ")!");
                        return false;
                    }
                }
            }

            //Require at least one candidate
            if (localCandidates.isEmpty()) {
                //No candidates found
                log.warn(marker, "No local file matching " + download + " (~" + download.getFileName() + ")!");
                return false;
            }
        }
        return true;
    }

    public void close() throws IOException {
        adapter.close();
        storage.close();
    }

    public StudipAdapter getAdapter() {
        return adapter;
    }

    public Storage getStorage() {
        return storage;
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
}
