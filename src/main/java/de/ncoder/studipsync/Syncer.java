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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static de.ncoder.studipsync.Loggers.LOG_EXECUTOR;
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
        final List<Seminar> seminars;
        final List<Future<Void>> executions = new ArrayList<>();

        //Access seminars
        browserLock.lock();
        adapter.init();
        adapter.doLogin();
        try {
            seminars = adapter.parseSeminars();
        } finally {
            browserLock.unlock();
        }

        //Find seminars
        for (final Seminar seminar : seminars) {
            executions.add(executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    LOG_EXECUTOR.info(this + " started");
                    List<Future<Void>> executions = executor.invokeAll(syncSeminar(seminar, false));
                    for (Future<Void> exec : executions) {
                        exec.get();
                    }
                    LOG_EXECUTOR.info(this + " finished");
                    return null;
                }

                @Override
                public String toString() {
                    return "[Parse " + seminar.getID() + "]";
                }
            }));
        }

        //Do sync
        ExecutionException exception = null;
        for (Future<Void> exec : executions) {
            try {
                exec.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        executor.shutdown();
        LOG_EXECUTOR.info("Tasks left " + executor);
        executor.awaitTermination(1, TimeUnit.MINUTES);

        if (exception != null) {
            //throw exception;
        }
    }

    public List<Callable<Void>> syncSeminar(final Seminar seminar, boolean forceAbsolute) throws ExecutionException, IOException, InterruptedException {
        List<Callable<Void>> executions = new LinkedList<>();
        final CountDownLatch counter;
        boolean hasDiff = false;

        //Find downloads
        browserLock.lock();
        try {
            adapter.selectSeminar(seminar);
            List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS, true);
            counter = new CountDownLatch(downloads.size());
            for (final Download download : downloads) {
                if (download.getLevel() == 0) {
                    final InputStream src;
                    final boolean downloadDiff;
                    if (forceAbsolute) {
                        //Absolute forced
                        downloadDiff = false;
                        src = adapter.startDownload(download, false);
                    } else if (download.isChanged()) {
                        //Changed data
                        downloadDiff = true;
                        src = adapter.startDownload(download, true);
                    } else {
                        //Nothing changed
                        downloadDiff = true;
                        src = null;
                    }
                    if (src != null) {
                        executions.add(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                LOG_EXECUTOR.info(this + " started");
                                try {
                                    storage.store(download, src, downloadDiff);
                                    LOG_EXECUTOR.info(this + " finished");
                                    return null;
                                } finally {
                                    counter.countDown();
                                }
                            }

                            @Override
                            public String toString() {
                                return "[Download " + (downloadDiff ? "DIF" : "ABS") + " " + download.getFileName() + "@" + seminar.getID() + "]";
                            }
                        });
                    }
                    if (downloadDiff) {
                        hasDiff = true;
                    }
                } else {
                    counter.countDown();
                }
            }
        } finally {
            browserLock.unlock();
        }

        //Check downloads
        final boolean isAbsolute = !hasDiff; //final Version of hasDiff
        executions.add(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                LOG_EXECUTOR.info(this + " started");
                counter.await();
                if (!isSeminarInSync(seminar)) {
                    if (isAbsolute) {
                        throw new IllegalStateException("Could not synchronize Seminar " + seminar + ". Local data is different from online data after full download.");
                    } else {
                        syncSeminar(seminar, true);
                    }
                }
                LOG_EXECUTOR.info(this + " finished");
                return null;
            }

            @Override
            public String toString() {
                return "[Check " + seminar.getID() + "]";
            }
        });

        return executions;
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
