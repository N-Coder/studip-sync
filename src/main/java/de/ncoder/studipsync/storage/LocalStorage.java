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

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;
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

import static de.ncoder.studipsync.studip.StudipAdapter.ZIP_ENCODING;

public class LocalStorage implements Storage {
    private static final Logger log = LoggerFactory.getLogger(LocalStorage.class);

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
        return new LocalStorage(root);
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

    // --------------------------------STORAGE---------------------------------

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
    public Path resolve(Download download) {
        return getPathResolverDelegate().resolve(getRoot(), download);
    }

    @Override
    public Path resolve(Download download, Path srcFile) {
        return getPathResolverDelegate().resolve(getRoot(), download, srcFile);
    }

    @Override
    public void store(Download download, InputStream dataSrc, boolean isDiff) throws IOException {
        //TODO downloading should be handled externally
        Path tmp = Files.createTempFile(
                download.getSeminar().getID().replace("[^A-Za-z0-9]+", "") + "-",
                "-" + download.getFileName().replace("[^A-Za-z0-9]+", "")
        );
        Files.copy(dataSrc, tmp, StandardCopyOption.REPLACE_EXISTING);
        store(download, tmp, isDiff);
        Files.delete(tmp);
    }

    @Override
    public void store(Download download, Path dataSrc, boolean isDiff) throws IOException {
        Path dstPath = resolve(download);
        log.debug("NEW:\t" + download + " <<" + (isDiff ? "DIF" : "ABS") + "<< " + dataSrc);
        if (!isDiff) {
            delete(download, dstPath);
        }
        if (download.isFolder()) {
            storeZipped(download, dataSrc);
        } else {
            storeFile(download, dataSrc, dstPath);
        }
    }


    private void storeFile(final Download download, Path src, Path dst) throws IOException {
        if (dst.getParent() != null) {
            Files.createDirectories(dst.getParent());
        }
        if (!checkFilesEqual(src, dst)) {
            log.trace("\t\t" + src + " >> " + dst);
            Files.setLastModifiedTime(src, FileTime.fromMillis(System.currentTimeMillis()));
            try {
                for (StorageListener l : listeners) {
                    l.onUpdate(download, dst, src);
                }
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (OperationVeto e) {
                log.trace("\t\t" + e);
            }
        }
    }

    private void storeZipped(final Download download, Path srcZip) throws IOException {
        if (Files.size(srcZip) <= 0) {
            throw new IOException("Empty file");
        }
        try (FileSystem srcFS = FileSystems.newFileSystem(new URI("jar", srcZip.toUri().toString(), ""), zipFSOptions(false))) {
            for (final Path srcRoot : srcFS.getRootDirectories()) {
                Files.walkFileTree(srcRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path srcFile, BasicFileAttributes attr) throws IOException {
                        storeFile(download, srcFile, resolve(download, srcFile));
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (URISyntaxException e) {
            throw new IOException("Can't open zip file " + srcZip, e);
        }
    }

    @Override
    public void delete(Download download) throws IOException {
        delete(download, resolve(download));
    }

    private void delete(final Download download, Path path) throws IOException {
        final Path parent = resolve(download);
        log.debug("DEL:\t" + path);
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString().startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path subpath, BasicFileAttributes attrs) throws IOException {
                    deleteFile(download, subpath.relativize(parent));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (DirectoryNotEmptyException e) {
                        log.trace("\tDirectory " + dir + " not emptied: " + e.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (Files.exists(path)) {
            deleteFile(download, path.relativize(parent));
        }
    }

    private void deleteFile(Download download, Path relativePath) throws IOException {
        log.trace("DEL:\t\t" + relativePath);
        try {
            for (StorageListener l : listeners) {
                l.onDelete(download, relativePath);
            }
            Files.delete(relativePath);
        } catch (OperationVeto e) {
            log.trace("\t\t" + e);
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

    // ------------------------------------------------------------------------

    @Override
    public Path getRoot() {
        return root;
    }

    @Override
    public boolean hasListener(StorageListener o) {
        return listeners.contains(o);
    }

    @Override
    public boolean registerListener(StorageListener e) {
        return listeners.add(e);
    }

    @Override
    public boolean unregisterListener(StorageListener o) {
        return listeners.remove(o);
    }

    public static Map<String, Object> zipFSOptions(boolean create) {
        Map<String, Object> options = new HashMap<>();
        options.put("create", create + "");
        options.put("encoding", ZIP_ENCODING);
        return options;
    }

    // --------------------------------RESET-----------------------------------

    public static void reset(Path cachePath, Path cookiesPath) throws IOException {
        log.debug("Reset started");
        if (cookiesPath != null && Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
        if (Files.isDirectory(cachePath)) {
            Files.walkFileTree(cachePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString().startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path subpath, BasicFileAttributes attrs) throws IOException {
                    Files.delete(subpath);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (DirectoryNotEmptyException e) {
                        log.trace("\tDirectory " + dir + " not emptied: " + e.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (Files.exists(cachePath)) {
            Files.delete(cachePath);
        }
        log.info("Reset completed");
    }
}
