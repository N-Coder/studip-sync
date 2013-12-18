package de.ncoder.studipsync;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LocalStorage;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.studip.StudipAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static de.ncoder.studipsync.Loggers.LOG_SYNCER;
import static de.ncoder.studipsync.Values.PAGE_DOWNLOADS;
import static de.ncoder.studipsync.Values.PAGE_DOWNLOADS_LATEST;

public class Syncer {
    private final StudipAdapter adapter;
    private final LocalStorage storage;
    private final ExecutorService executor;
    private final ReentrantLock browserLock = new ReentrantLock();

    public Syncer(StudipAdapter adapter, LocalStorage storage, ExecutorService executor) {
        this.adapter = adapter;
        this.storage = storage;
        this.executor = executor;
    }

    public synchronized void sync() throws ExecutionException, InterruptedException {
        browserLock.lock();
        adapter.init();
        adapter.doLogin();
        List<Seminar> seminars;
        try {
            seminars = adapter.parseSeminars();
        } finally {
            browserLock.unlock();
        }
        List<Callable<Void>> tasks = new LinkedList<>();

        //Find seminars
        for (final Seminar seminar : seminars) {
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    syncSeminar(seminar, false);
                    return null;
                }
            });
        }

        //Do sync
        ExecutionException exception = null;
        List<Future<Void>> executions = executor.invokeAll(tasks);
        for (Future<Void> exec : executions) {
            try {
                exec.get();
            } catch (ExecutionException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    public void syncSeminar(Seminar seminar, boolean forceAbsolute) throws ExecutionException, IOException, InterruptedException {
        boolean isAbsolute = true;
        List<Callable<Void>> tasks = new LinkedList<>();

        //Find downloads
        browserLock.lock();
        try {
            adapter.selectSeminar(seminar);
            List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS, true);
            for (final Download download : downloads) {
                if (download.getLevel() == 0) {
                    final InputStream src;
                    final boolean isDiff;
                    if (forceAbsolute) {
                        //Absolute forced
                        isDiff = false;
                        src = adapter.startDownload(download, isDiff);
                    } else if (download.isChanged()) {
                        //Changed data
                        isDiff = true;
                        src = adapter.startDownload(download, isDiff);
                    } else {
                        //Nothing changed
                        isDiff = true;
                        src = null;
                    }
                    if (src != null) {
                        tasks.add(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                storage.store(download, src, isDiff);
                                return null;
                            }
                        });
                    }
                    if (isDiff) {
                        isAbsolute = false;
                    }
                }
            }
        } finally {
            browserLock.unlock();
        }

        //Do download
        //TODO pull up to sync()
        List<Future<Void>> executions = executor.invokeAll(tasks);
        for (Future<Void> e : executions) {
            e.get();
        }

        //Check downloads
        if (!isSeminarInSync(seminar)) {
            if (isAbsolute) {
                throw new IllegalStateException("Could not synchronize Seminar " + seminar + ". Local data is different from online data after full download.");
            } else {
                syncSeminar(seminar, true);
            }
        }
    }

    public boolean isSeminarInSync(Seminar seminar) throws ExecutionException, IOException {
        final List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS_LATEST, false);

        if (downloads.isEmpty()) {
            //No downloads - nothing to do
            return true;
        }

        final List<Path> localFiles = new LinkedList<>();
        Path storagePath = storage.resolve(seminar);
        if (!Files.exists(storagePath)) {
            //No local data despite available downloads
            LOG_SYNCER.info("Seminar " + seminar + " is empty!");
            return false;
        }

        Files.walkFileTree(storagePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                localFiles.add(file);
                if (localFiles.size() > downloads.size()) {
                    return FileVisitResult.TERMINATE; // to many files, will throw after walking
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (localFiles.size() < downloads.size()) {
            // Missing files
            LOG_SYNCER.warn("Seminar " + seminar + " has only " + localFiles.size() + " local files of " + downloads.size() + " online files.");
            return false;
        } else {
            // Ignore surplus files
            LOG_SYNCER.debug("Seminar " + seminar + " has deleted files left! " + localFiles.size() + " local files and " + downloads.size() + " online files.");
        }

        for (Download download : downloads) {
            List<Path> localCandidates = new LinkedList<>();
            for (Path local : localFiles) {
                // TODO check path instead name (names may be equal in different folders)
                if (local.getName(local.getNameCount() - 1).toString().equals(download.getFileName())) {
                    if (!localCandidates.isEmpty()) {
                        //Already found a candidate
                        LOG_SYNCER.debug("Local files " + localCandidates + " and " + local + " match " + download + "!");
                        //return false; // TODO add assume clean for conflicting files
                    }
                    localCandidates.add(local);

                    Date localLastMod = new Date(Files.getLastModifiedTime(local).toMillis());
                    if (!localLastMod.after(download.getLastModified())) {
                        //Candidate *potentially* outdated
                        LOG_SYNCER.warn("Local file " + local + "(" + localLastMod + ") older than online Version " + download + "(" + download.getLastModified() + ")!");
                        return false;
                    }
                }
            }
            if (localCandidates.isEmpty()) {
                //No candidates found
                LOG_SYNCER.warn("No local file matching " + download + " (~" + download.getFileName() + ")!");
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

    public LocalStorage getStorage() {
        return storage;
    }
}
