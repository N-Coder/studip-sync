package de.ncoder.studipsync.data;

import de.ncoder.studipsync.Loggers;
import de.ncoder.studipsync.Values;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class LocalStorage {
    private final Path root;
    private final List<StorageListener> listeners = new LinkedList<StorageListener>();
    private transient FileSystem underlyingFS;

    private LocalStorage(Path root) {
        this.root = root;
    }

    public static LocalStorage openZip(Path zip) throws IOException, URISyntaxException {
        return openZip(new URI("jar", zip.toUri().toString(), ""));
    }

    public static LocalStorage openZip(URI uri) throws IOException {
        FileSystem cache = FileSystems.newFileSystem(uri, Values.zipFSOptions(true));
        LocalStorage storage = new LocalStorage(cache.getPath("/"));
        storage.underlyingFS = cache;
        return storage;
    }

    public static LocalStorage open(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            Files.createDirectories(root);
        }
        return new LocalStorage(root);
    }

    public void close() throws IOException {
        if (underlyingFS != null) {
            underlyingFS.close();
        }
    }

    // --------------------------------STORAGE---------------------------------

    public Path resolve(Download download) {
        return resolve(download.getSeminar()).resolve(download.getPath());
    }

    public Path resolve(Seminar seminar) {
        return root.resolve(seminar.getID());
    }

    public void store(Download download, InputStream dataSrc, boolean isDiff) throws URISyntaxException, IOException {
        Path tmp = Files.createTempFile(root, download.getSeminar().getID() + "-" + download.getFileName(), "");
        Files.copy(dataSrc, tmp);
        store(download, tmp, isDiff);
    }

    public void store(Download download, Path dataSrc, boolean isDiff) throws URISyntaxException, IOException {
        Path dstPath = resolve(download);
        Loggers.LOG_DOWNLOAD.info(download + " <<" + (isDiff ? "DIF" : "NEW") + "<< " + dataSrc);
        if (!isDiff) {
            delete(download, dstPath);
        }
        if (download.isFolder()) {
            storeZipped(download, dataSrc, dstPath);
        } else {
            storeFile(download, dataSrc, dstPath);
        }
    }

    private void delete(final Download download, Path path) throws IOException {
        final Path parent = resolve(download);
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path subpath, BasicFileAttributes attrs) throws IOException {
                    Path relativeSubpath = subpath.relativize(parent);
                    for (StorageListener l : listeners) {
                        l.fileDeleted(download, relativeSubpath);
                    }
                    Files.delete(subpath);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (Files.exists(path)) {
            Path relativePath = path.relativize(parent);
            for (StorageListener l : listeners) {
                l.fileDeleted(download, relativePath);
            }
            Files.delete(path);
        }
    }

    private void storeFile(final Download download, Path src, final Path dst) throws IOException {
        if (dst.getParent() != null) {
            Files.createDirectories(dst.getParent());
        }
        if (!checkFilesEqual(src, dst)) {
            Loggers.LOG_DOWNLOAD.debug("	" + src + " >> " + dst);
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(dst, FileTime.fromMillis(System.currentTimeMillis()));
            for (StorageListener l : listeners) {
                l.fileUpdated(download, dst);
            }
        }
    }

    private void storeZipped(final Download download, Path src, final Path dstRoot) throws IOException, URISyntaxException {
        if (Files.size(src) <= 0) {
            throw new IOException("Empty file");
        }
        try (FileSystem srcFS = FileSystems.newFileSystem(new URI("jar", src.toUri().toString(), ""), Values.zipFSOptions(false))) {
            for (final Path srcRoot : srcFS.getRootDirectories()) {
                Files.walkFileTree(srcRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path srcFile, BasicFileAttributes attr) throws IOException {
                        Path dstFile = dstRoot.getParent().resolve(srcRoot.relativize(srcFile));
                        storeFile(download, srcFile, dstFile);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------------------

    public static boolean checkFilesEqual(Path fileA, Path fileB) {
        try {
            if (!Files.exists(fileA) || !Files.exists(fileB)) {
                return !Files.exists(fileA) && !Files.exists(fileB);
            }
            if (Files.size(fileA) != Files.size(fileB)) {
                return false;
            }
            byte[] srcBuf = new byte[1024];
            byte[] dstBuf = new byte[1024];
            try (InputStream srcIn = Files.newInputStream(fileA); InputStream dstIn = Files.newInputStream(fileB);) {
                srcIn.read(srcBuf);
                dstIn.read(dstBuf);
                if (!Arrays.equals(srcBuf, dstBuf)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            Loggers.LOG_DOWNLOAD.warn("Could not compare files " + fileA + " and " + fileB, e);
            return false;
        }
    }

    // ------------------------------------------------------------------------

    public Path getRoot() {
        return root;
    }

    public boolean hasListener(StorageListener o) {
        return listeners.contains(o);
    }

    public boolean registerListener(StorageListener e) {
        return listeners.add(e);
    }

    public boolean unregisterListener(StorageListener o) {
        return listeners.remove(o);
    }

    public static interface StorageListener {
        public void fileDeleted(Download download, Path child);

        public void fileUpdated(Download download, Path child);
    }
}
