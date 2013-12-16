package de.ncoder.studipsync;

import com.google.common.util.concurrent.SettableFuture;
import de.ncoder.studipsync.LocalStorage.StorageListener;
import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.swt.BrowserShell;
import de.ncoder.studipsync.swt.SWTBrowserAdapter;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static de.ncoder.studipsync.Values.PAGE_DOWNLOADS;
import static de.ncoder.studipsync.Values.PAGE_DOWNLOADS_LATEST;

public class Starter {

    private static final Path cachePath = new File(System.getProperty("user.dir") + "/cache.zip").toPath();
    private static final Path latestPath = cachePath.resolve("latest");
    private static final Logger log = LoggerFactory.getLogger("MAIN");

    public static void main(String[] args) throws Exception {
        final SettableFuture<StudipAdapter> adapterFuture = SettableFuture.create();
        log.info("Started");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Display display = new Display();
                try {
                    BrowserShell shell = new BrowserShell(display);
                    adapterFuture.set(new StudipAdapter(new SWTBrowserAdapter(shell.getBrowser()), shell, cachePath));
                    log.info("Initiated");
                    shell.open();
                    log.info("Opened");
                    while (!shell.isDisposed()) {
                        if (!display.readAndDispatch()) {
                            display.sleep();
                        }
                    }
                } catch (IOException | URISyntaxException e) {
                    adapterFuture.setException(e);
                } finally {
                    display.dispose();
                }
            }
        }, "UI").start();

        final StudipAdapter adapter = adapterFuture.get();
        try {
            adapter.getStorage().registerListener(new StorageListener() {
                @Override
                public void fileUpdated(Path updated) {
                    // FIXME resolve latest (updated.getRoot()==null)
                    // latestPath.resolve(updated.getName(0).relativize(updated));
                    Path latest = latestPath.resolve(updated.toString());
                    try {
                        Files.createDirectories(latest.getParent());
                        Files.copy(updated, latest);
                    } catch (IOException e) {
                        log.error("Couldn't cache updated", e);
                    }
                }
            });
            adapter.init();
            run(adapter);
            log.info("Finished");
        } catch (Exception e) {
            log.error("Uncaught exception", e);
        } finally {
            adapter.close();
        }
    }

    // TODO parallelize

    public static void run(StudipAdapter adapter) throws Exception {
        List<Seminar> seminars = adapter.parseSeminars();
        for (Seminar seminar : seminars) {
            try {
                handleSeminar(adapter, seminar, false);
            } catch (Exception e) {
                log.error("Could not work on Seminar " + seminar, e);
            }
        }
        // System.out.println(adapter.getStorage().printInfo());
    }

    public static void handleSeminar(StudipAdapter adapter, Seminar seminar, boolean forceComplete) throws Exception {
        adapter.selectSeminar(seminar);
        boolean maybeUnclean = false;
        List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS, true);
        for (Download download : downloads) {
            if (download.getLevel() == 0) {
                Path file;
                if (forceComplete) {
                    adapter.getStorage().deleteDownload(download);
                    file = adapter.download(download, false);
                } else if (download.hasNewest()) {
                    maybeUnclean = true;
                    file = adapter.download(download, true);
                } else {
                    maybeUnclean = true;
                    file = null;
                }
                if (file != null) {
                    adapter.getStorage().storeDownload(download, file);
                }
            }
        }
        if (checkDownloadsUnclean(adapter, seminar)) {
            if (maybeUnclean) {
                handleSeminar(adapter, seminar, true);
            } else {
                throw new IllegalStateException("Could not synchronize Seminar " + seminar + ". Local data is different from online data after full download.");
            }
        }
    }

    public static boolean checkDownloadsUnclean(StudipAdapter adapter, Seminar seminar) throws CancellationException, TimeoutException, InterruptedException, ExecutionException, IOException {
        final List<Download> downloads = adapter.parseDownloads(PAGE_DOWNLOADS_LATEST, false);
        if (downloads.isEmpty()) {
            return false;
        }
        final List<Path> locals = new LinkedList<>();
        Path storagePath = adapter.getStorage().getStoragePath(seminar);
        if (!Files.exists(storagePath)) {
            log.warn("Seminar " + seminar + " has no local data in " + storagePath);
            return true;
        }
        Files.walkFileTree(storagePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                locals.add(file);
                if (locals.size() > downloads.size()) {
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (locals.size() != downloads.size()) {
            log.warn("Seminar " + seminar + " has " + locals.size() + " local files != " + downloads.size() + " online files.");
            return true;
        }

        for (Download download : downloads) {
            // String name = download.getPath();
            List<Path> local = new LinkedList<>();
            for (Path l : locals) {
                // FIXME check path instead name (names may be equal in different folders)
                if (l.getName(l.getNameCount() - 1).toString().equals(download.getFileName())) {
                    if (!local.isEmpty()) {
                        log.debug("Local files " + local + " and " + l + " match " + download + "!");
                        //return true; // TODO add assume clean for conflicting files
                    }
                    local.add(l);

                    Date localLastMod = new Date(Files.getLastModifiedTime(l).toMillis());
                    if (!localLastMod.after(download.getLastModified())) {
                        log.warn("Local file " + l + "(" + localLastMod + ") older than online Version " + download + "(" + download.getLastModified() + ")!");
                        return true;
                    }
                }
            }
            if (local.isEmpty()) {
                log.warn("No local file matching " + download + " (~" + download.getFileName() + ")!");
                return true;
            }
        }
        return false;
    }
}
