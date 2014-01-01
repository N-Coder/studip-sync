package de.ncoder.studipsync;

import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.storage.LocalStorage;
import de.ncoder.studipsync.studip.jsoup.JsoupStudipAdapter;
import de.ncoder.studipsync.ui.UIAdapter;
import de.ncoder.studipsync.ui.swing.LoginDialog;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;

import java.awt.*;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import static de.ncoder.studipsync.Values.*;

public class Starter {

    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(OPTIONS, args);

        Syncer syncer = createSyncer(cmd);
        try {
            LOG_MAIN.info("Started");
            syncer.sync();
            LOG_MAIN.info("Finished");
        } catch (Exception e) {
            LOG_MAIN.error("Uncaught exception", e);
        } finally {
            syncer.close();
            //FIXME AWT Event Queue blocks termination with modality level 1
        }
    }

    public static Syncer createSyncer(CommandLine cmd) throws IOException, ParseException {
        int timeoutMs = (int) TimeUnit.SECONDS.toMillis(2);
        if (cmd.hasOption(OPTION_TIMEOUT)) {
            try {
                timeoutMs = Integer.parseInt(cmd.getOptionValue(OPTION_TIMEOUT));
            } catch (NumberFormatException e) {
                throw new ParseException(e.getMessage());
            }
        }
        Path cachePath = new File(System.getProperty("user.dir"), "studip.zip").toPath();
        if (cmd.hasOption(OPTION_OUT)) {
            cachePath = new File(cmd.getOptionValue(OPTION_OUT)).toPath();
        }
        Path cookiesPath = new File(System.getProperty("user.dir"), "cookies.json").toPath();
        if (cmd.hasOption(OPTION_COOKIES)) {
            cookiesPath = new File(cmd.getOptionValue(OPTION_COOKIES)).toPath();
        }
        if (cmd.hasOption(OPTION_NO_COOKIES)) {
            cookiesPath = null;
        }
        if (cmd.hasOption(OPTION_RESET)) {
            reset(cachePath, cookiesPath);
        }
        UIType uiType = null;
        if (cmd.hasOption(OPTION_UI)) {
            try {
                uiType = UIType.valueOf(cmd.getOptionValue(OPTION_UI));
            } catch (IllegalArgumentException e) {
                throw new ParseException("Illegal value for '" + OPTION_UI + "': " + e.getMessage());
            }
        }

        return createSyncer(timeoutMs, cachePath, cookiesPath, uiType);
    }

    public static Syncer createSyncer(int timeoutMs, Path cachePath, Path cookiesPath, UIType uiType) throws ParseException, IOException {
        LOG_MAIN.info("Sync to " + cachePath.toAbsolutePath());

        UIAdapter ui = getUI(uiType);
        LocalStorage storage = LocalStorage.open(cachePath);
        JsoupStudipAdapter browser = new JsoupStudipAdapter(ui, cookiesPath, timeoutMs);
        browser.addNavigationListener(new JsoupStudipAdapter.NavigationListener() {
            @Override
            public void navigated(URL url) {
                LOG_NAVIGATE.debug(url.toString());
            }
        });

        return new Syncer(
                browser,
                storage
        );
    }

    public static void reset(Path cachePath, Path cookiesPath) throws IOException {
        LOG_MAIN.info("Resetting");
        if (cookiesPath != null && Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
        if (Files.isDirectory(cachePath)) {
            Files.walkFileTree(cachePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path subpath, BasicFileAttributes attrs) throws IOException {
                    Files.delete(subpath);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (Files.exists(cachePath)) {
            Files.delete(cachePath);
        }
        LOG_MAIN.info("Reset completed");
    }

    public static UIAdapter getUI(UIType uiType) throws ParseException {
        if (uiType != null) {
            switch (uiType) {
                case CMD:
                    return getTextUI();
                case SWING:
                    return getSwingUI();
                default:
                    throw new ParseException("Illegal value for '" + OPTION_UI + "': " + uiType);
            }
        } else {
            if (System.console() != null) {
                return getTextUI();
            } else {
                return getSwingUI();
            }
        }
    }

    public static UIAdapter getSwingUI() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOG_MAIN.warn("Can't set Look and Feel for Swing UI", e);
        }
        return new UIAdapter() {
            private int loginTries = -1;

            @Override
            public LoginData requestLoginData() {
                loginTries++;
                return new LoginDialog(loginTries > 0).requestLoginData();
            }

            public void displayWebpage(URI uri) {
                Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
                if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                    try {
                        desktop.browse(uri);
                    } catch (Exception e) {
                        LOG_MAIN.warn("Can't open browser", e);
                    }
                } else {
                    LOG_MAIN.warn("No Browser available");
                }
            }

            @Override
            public void close() {
            }
        };
    }

    public static UIAdapter getTextUI() {
        if (System.console() == null) {
            LOG_MAIN.warn("Won't be able to access console for reading LoginData", new NullPointerException("System.console()==null"));
        }
        return new UIAdapter() {
            @Override
            public LoginData requestLoginData() {
                Console console = System.console();
                console.printf("Username: ");
                String username = console.readLine();
                char password[] = console.readPassword("Password: ");
                return new LoginData(username, password);
            }

            @Override
            public void displayWebpage(URI uri) {
                LOG_MAIN.info("Page dump written to\n" + uri);
            }

            @Override
            public void close() {
            }
        };
    }
}
