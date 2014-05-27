/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Niko Fink
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.data.StudipFile;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.StudipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class LocalStorage implements Storage {
	private static final Logger log = LoggerFactory.getLogger(LocalStorage.class);

	public static final String ZIP_ENCODING = "Cp1252";

	private PathResolver resolverDelegate = StandardPathResolver.ByHash;
	private final Path root;
	private final List<StorageListener> listeners = new LinkedList<>();
	private transient FileSystem underlyingFS;

	private LocalStorage(Path root) {
		this.root = root;
	}

	public static LocalStorage openZip(Path zip) throws IOException {
		try {
			return openZip(new URI("jar", zip.toUri().toString(), ""));
		} catch (URISyntaxException e) {
			throw new IOException("Can't open zip file " + zip, e);
		}
	}

	public static LocalStorage openZip(URI uri) throws IOException {
		FileSystem cache = FileSystems.newFileSystem(uri, zipFSOptions(true));
		LocalStorage storage = new LocalStorage(cache.getPath("/"));
		storage.underlyingFS = cache;
		return storage;
	}

	public static LocalStorage openDir(Path root) throws IOException {
		if (!Files.isDirectory(root)) {
			Files.createDirectories(root);
		}
		FileSystem cache = FileSystems.newFileSystem(root, null);
		LocalStorage storage = new LocalStorage(root);
		storage.underlyingFS = cache;
		return storage;
	}

	public static LocalStorage open(Path root) throws IOException {
		if (root.toString().endsWith(".zip")) {
			return openZip(root);
		} else {
			return openDir(root);
		}
	}

	@Override
	public void close() throws IOException {
		if (underlyingFS != null) {
			underlyingFS.close();
		}
	}

	// --------------------------------PUBLIC STORAGE--------------------------

	@Override
	public void store(StudipAdapter adapter, Seminar seminar) throws IOException, StudipException {
		store(seminar, adapter.startDownload(seminar.getDownloadURL()));
	}

	@Override
	public void store(StudipAdapter adapter, Seminar seminar, long changesAfter) throws IOException, StudipException {
		store(seminar, adapter.startDownload(seminar.getDownloadURL(changesAfter)));
	}

	private void store(final Seminar seminar, InputStream in) throws IOException {
		Path tmp = Files.createTempFile("studip-seminar-" + seminar.getHash(), ".zip");
		Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
		doStore(seminar, tmp);
		Files.delete(tmp);
	}

	@Override
	public void store(StudipAdapter adapter, StudipFile file) throws IOException, StudipException {
		store(file, adapter.startDownload(file.getDownloadURL()));
	}

	@Override
	public void store(StudipAdapter adapter, StudipFile file, long changesAfter) throws IOException, StudipException {
		store(file, adapter.startDownload(file.getDownloadURL(changesAfter)));
	}

	private void store(StudipFile file, InputStream in) throws IOException {
		Path tmp = Files.createTempFile("studip-file-" + file.getHash(), '.' + file.getFileExtension());
		Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
		doStore(file, tmp);
		Files.delete(tmp);
	}

	// --------------------------------INTERNAL STORAGE------------------------

	private void doStore(final Seminar seminar, Path srcZip) throws IOException {
		if (Files.size(srcZip) <= 0) {
			throw new IOException("Empty file");
		}
		iterateZip(srcZip, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
				try {
					doStore(findStudipFile(seminar, path), path);
				} catch (StudipException e) {
					throw new IOException(e);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void doStore(final StudipFile file, Path src) throws IOException {
		Path dst = resolve(file);
		if (dst.getParent() != null) {
			Files.createDirectories(dst.getParent());
		}
		if (file.isFolder()) {
			iterateZip(src, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
					try {
						doStore(findStudipFile(file, path), path);
					} catch (StudipException e) {
						throw new IOException(e);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			Files.setLastModifiedTime(src, FileTime.fromMillis(System.currentTimeMillis()));
			for (StorageListener l : listeners) {
				l.onUpdate(file, dst, src);
			}
			Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

	private StudipFile findStudipFile(StudipFile parent, Path filePath) throws StudipException {
		String path = parent.getPath() + filePath.toString();
		for (StudipFile studipFile : parent.getChildren()) {
			if (studipFile.getPath().endsWith(path)) {
				return studipFile;
			}
		}
		return null;
	}

	private StudipFile findStudipFile(Seminar seminar, Path filePath) throws StudipException {
		String path = filePath.toString().substring(1);
		for (StudipFile studipFile : seminar.getFiles()) {
			if (studipFile.getPath().endsWith(path)) {
				return studipFile;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------

	public void setPathResolverDelegate(PathResolver resolverDelegate) {
		Objects.requireNonNull(resolverDelegate);
		this.resolverDelegate = resolverDelegate;
	}

	public PathResolver getPathResolverDelegate() {
		return resolverDelegate;
	}

	@Override
	public Path resolve(Seminar seminar) {
		return getPathResolverDelegate().resolve(getRoot(), seminar);
	}

	@Override
	public Path resolve(StudipFile studipFile) {
		return getPathResolverDelegate().resolve(getRoot(), studipFile);
	}

	// ------------------------------------------------------------------------

	@Override
	public Path getRoot() {
		return root;
	}

	@Override
	public boolean registerListener(StorageListener e) {
		return listeners.add(e);
	}

	@Override
	public boolean unregisterListener(StorageListener o) {
		return listeners.remove(o);
	}

	private void iterateZip(Path zip, FileVisitor<Path> visitor) throws IOException {
		//try (FileSystem srcFS = FileSystems.newFileSystem(new URI("jar", zip.toUri().toString(), ""), zipFSOptions(false))) {
		try (FileSystem srcFS = FileSystems.newFileSystem(zip, null)) {
			for (final Path srcRoot : srcFS.getRootDirectories()) {
				Files.walkFileTree(srcRoot, visitor);
			}
		}// catch (URISyntaxException e) {
		//	throw new IOException("Can't open zip file " + zip, e);
		//}
	}

	private static Map<String, Object> zipFSOptions(boolean create) {
		Map<String, Object> options = new HashMap<>();
		options.put("create", create + "");
		options.put("encoding", ZIP_ENCODING);
		return options;
	}
}
