package de.ncoder.studipsync;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LocalStorage;
import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.studip.UIAdapter;
import de.ncoder.studipsync.studip.parsed.StudipBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class Starter {
    private static final Path cachePath = new File(System.getProperty("user.dir") + "/cache.zip").toPath();
    private static final Path latestPath = new File(System.getProperty("user.dir") + "/latest/").toPath();
    private static final Path cookiesPath = new File(System.getProperty("user.dir") + "/cookies.json").toPath();
    private static final Logger LOG_MAIN = LoggerFactory.getLogger("MAIN");

    public static void main(String[] args) throws Exception {
        final Syncer syncer = getEnvJSSyncer();
        try {
            syncer.getStorage().registerListener(new LocalStorage.StorageListener() {
                @Override
                public void fileDeleted(Download download, Path child) {
                }

                @Override
                public void fileUpdated(Download download, Path child) {
                    try {
                        Path latest = latestPath.resolve(child.toString());
                        Files.createDirectories(latest.getParent());
                        Files.copy(child, latest);
                    } catch (IOException e) {
                        LOG_MAIN.error("Couldn't publish updated file", e);
                    }
                }
            });
            LOG_MAIN.info("Started");
            syncer.sync();
            LOG_MAIN.info("Finished");
        } catch (Exception e) {
            LOG_MAIN.error("Uncaught exception", e);
        } finally {
            syncer.close();
        }
    }

    public static Syncer getEnvJSSyncer() throws IOException, URISyntaxException {
        LocalStorage storage = LocalStorage.openZip(cachePath);
        UIAdapter ui = new UIAdapter() {
            @Override
            public LoginData requestLoginData() {
                Console console = System.console();
                if (console != null) {
                    console.printf("Username:");
                    String username = console.readLine();
                    char password[] = console.readPassword("Password: ");
                    return new LoginData(username, password);
                } else {
                    JPasswordField passwordField = new JPasswordField(10);
                    passwordField.setEchoChar('#');
                    JOptionPane.showMessageDialog(
                            null,
                            passwordField,
                            "Password: ",
                            JOptionPane.OK_OPTION);
                    return new LoginData("fink13", passwordField.getPassword());
                }
            }

            @Override
            public void close() {
            }
        };
        StudipBrowser browser = new StudipBrowser(ui, cookiesPath);
        browser.addNavigationListener(new StudipBrowser.NavigationListener() {
            @Override
            public void navigated(URL url) {
                //Files.copy(new ByteArrayInputStream((document.baseUri() + "\n" + document.toString()).getBytes()), new File("history/history" + (loadCount++) + ".html").toPath(), StandardCopyOption.REPLACE_EXISTING);
                //LOG_NAVIGATE.info(url.toString());
            }
        });

        return new Syncer(
                browser,
                storage,
                Executors.newCachedThreadPool()
        );
    }


//    public static Syncer getSWTSyncer() throws ExecutionException, InterruptedException {
//        final SettableFuture<Syncer> syncerFuture = SettableFuture.create();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                Display display = new Display();
//                try {
//                    BrowserShell shell = new BrowserShell(display);
//                    LocalStorage storage = LocalStorage.openZip(cachePath);
//                    BrowserAdapter browser = new SWTBrowserAdapter(shell.getBrowser());
//                    syncerFuture.set(
//                            new Syncer(
//                                    new StudipBrowser(
//                                            browser,
//                                            shell,
//                                            cookiesPath
//                                    ),
//                                    storage,
//                                    Executors.newCachedThreadPool()
//                            )
//                    );
//                    shell.open();
//                    LOG_MAIN.info("Opened");
//                    while (!shell.isDisposed()) {
//                        if (!display.readAndDispatch()) {
//                            display.sleep();
//                        }
//                    }
//                } catch (IOException | URISyntaxException e) {
//                    syncerFuture.setException(e);
//                } finally {
//                    display.dispose();
//                }
//            }
//        }, "UI").start();
//        return syncerFuture.get();
//    }
}
