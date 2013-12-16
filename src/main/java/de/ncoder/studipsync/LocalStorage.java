package de.ncoder.studipsync;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

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
    private static final String COOKIES_FILE = "cookies.json";

    private final FileSystem cache;
    private final List<StorageListener> listeners = new LinkedList<StorageListener>();

    private LocalStorage(FileSystem cache) {
        this.cache = cache;
    }

    public static LocalStorage open(URI uri) throws IOException {
        FileSystem cache = FileSystems.newFileSystem(uri, Values.zipFSOptions(true));
        return new LocalStorage(cache);
    }

    public void close() throws IOException {
        cache.close();
    }

    public Path getStoragePath(Download download) {
        return getStoragePath(download.getSeminar()).resolve(download.getPath());
    }

    public Path getStoragePath(Seminar seminar) {
        return cache.getPath(seminar.getID());
    }

    public boolean hasDownload(Download download) {
        return Files.exists(getStoragePath(download));
    }

    public void storeDownload(Download download, Path data) throws URISyntaxException, IOException {
        Path dst = getStoragePath(download);
        if (download.isFolder()) {
            unpackAndCacheDownload(download, data, dst);
        } else {
            cacheDownload(data, dst);
        }
    }

    public void deleteDownload(Download download) throws IOException {
        Path p = getStoragePath(download);
        if (Files.isDirectory(p)) {
            //TODO
        } else if (Files.exists(p)) {
            Files.delete(p);
        }
    }

    private void unpackAndCacheDownload(final Download parent, Path src, final Path dstRoot) throws IOException, URISyntaxException {
        if (Files.size(src) <= 0) {
            throw new IOException("Empty file");
        }
        try (FileSystem srcFS = FileSystems.newFileSystem(new URI("jar", src.toUri().toString(), ""), Values.zipFSOptions(false))) {
            for (final Path srcRoot : srcFS.getRootDirectories()) {
                Files.walkFileTree(srcRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path srcFile, BasicFileAttributes attr) throws IOException {
                        Path dstFile = dstRoot.getParent().resolve(srcRoot.relativize(srcFile));
                        cacheDownload(srcFile, dstFile);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    private void cacheDownload(Path src, final Path dst) throws IOException {
        if (dst.getParent() != null) {
            Files.createDirectories(dst.getParent());
        }
        if (!checkEqualContents(src, dst)) {
            Loggers.LOG_DOWNLOAD.debug("	" + src + " >> " + dst);
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(dst, FileTime.fromMillis(System.currentTimeMillis()));
            for (StorageListener l : listeners) {
                try {
                    l.fileUpdated(dst);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean checkEqualContents(Path src, Path dst) {
        try {
            if (!Files.exists(src) || !Files.exists(dst)) {
                return !Files.exists(src) && !Files.exists(dst);
            }
            if (Files.size(src) != Files.size(dst)) {
                return false;
            }
            byte[] srcBuf = new byte[1024];
            byte[] dstBuf = new byte[1024];
            try (InputStream srcIn = Files.newInputStream(src); InputStream dstIn = Files.newInputStream(dst);) {
                srcIn.read(srcBuf);
                dstIn.read(dstBuf);
                if (!Arrays.equals(srcBuf, dstBuf)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            Loggers.LOG_DOWNLOAD.warn("Could not compare files " + src + " and " + dst, e);
            return false;
        }
    }

    public FileSystem getCache() {
        return cache;
    }

    private Path cookiesPath;

    public Path getCookiesPath() {
        if (cookiesPath == null) {
            cookiesPath = cache.getPath(COOKIES_FILE);
        }
        return cookiesPath;
    }

    public boolean isOpen() {
        return cache.isOpen();
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
        public void fileUpdated(Path dst);
    }
}
