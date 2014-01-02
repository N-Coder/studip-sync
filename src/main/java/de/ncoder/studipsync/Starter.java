package de.ncoder.studipsync;

import de.ncoder.studipsync.storage.LocalStorage;
import de.ncoder.studipsync.storage.PathResolver;
import de.ncoder.studipsync.storage.StandardPathResolver;
import de.ncoder.studipsync.studip.jsoup.JsoupStudipAdapter;
import de.ncoder.studipsync.ui.StandardUIAdapter;
import de.ncoder.studipsync.ui.UIAdapter;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static de.ncoder.studipsync.Values.*;

public class Starter {

    public static void main(String[] args) throws Exception {
        boolean displayHelp = false;
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(OPTIONS, args);
            if (cmd.hasOption(OPTION_HELP)) {
                displayHelp = true;
                return;
            }

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
        } catch (ParseException e) {
            System.out.println("Illegal arguments passed. " + e.getMessage());
            displayHelp = true;
        } finally {
            if (displayHelp) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("studip-sync", OPTIONS);
            }
        }
    }

    public static Syncer createSyncer(CommandLine cmd) throws IOException, ParseException {
        int timeoutMs = DEFAULT_TIMEOUT;
        if (cmd.hasOption(OPTION_TIMEOUT)) {
            try {
                timeoutMs = Integer.parseInt(cmd.getOptionValue(OPTION_TIMEOUT));
            } catch (NumberFormatException e) {
                throw new ParseException(e.getMessage());
            }
        }
        Path cachePath = DEFAULT_CACHE_PATH;
        if (cmd.hasOption(OPTION_OUT)) {
            cachePath = Paths.get(cmd.getOptionValue(OPTION_OUT));
        }
        Path cookiesPath = DEFAULT_COOKIES_PATH;
        if (cmd.hasOption(OPTION_COOKIES)) {
            cookiesPath = Paths.get(cmd.getOptionValue(OPTION_COOKIES));
        }
        if (cmd.hasOption(OPTION_NO_COOKIES)) {
            cookiesPath = null;
        }
        if (cmd.hasOption(OPTION_RESET)) {
            reset(cachePath, cookiesPath);
        }
        UIAdapter ui = getUIAdapter(cmd.getOptionValue(OPTION_UI));
        PathResolver naming = getPathResolver(cmd.getOptionValue(OPTION_PATH_REOLVER));

        return createSyncer(timeoutMs, cachePath, cookiesPath, ui, naming);
    }

    public static Syncer createSyncer(int timeoutMs, Path cachePath, Path cookiesPath, UIAdapter ui, PathResolver pathResolver) throws IOException {
        LOG_MAIN.info("Sync to " + cachePath.toAbsolutePath());

        LocalStorage storage = LocalStorage.open(cachePath);
        if (pathResolver != null) {
            storage.setPathResolverDelegate(pathResolver);
        }
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

    // --------------------------------PATH RESOLVER---------------------------

    public static PathResolver getPathResolver(String type) throws ParseException {
        if (type != null) {
            try {
                return StandardPathResolver.valueOf(type);
            } catch (IllegalArgumentException earg) {
                try {
                    return loadPathResolver(type);
                } catch (ClassNotFoundException eclass) {
                    ParseException pe = new ParseException(type + " is neither an UIType nor can it be resolved to a Java class.");
                    pe.initCause(eclass);
                    pe.addSuppressed(earg);
                    throw pe;
                }
            }
        } else {
            return null;
        }
    }

    public static PathResolver loadPathResolver(String classname) throws ParseException, ClassNotFoundException {
        try {
            Class<?> clazz = Class.forName(classname);
            Object instance = clazz.newInstance();
            if (!(instance instanceof PathResolver)) {
                throw new ParseException(instance + " is not a PathResolver");
            }
            return (PathResolver) instance;
        } catch (InstantiationException | IllegalAccessException e) {
            ParseException pe = new ParseException("Could not instantiate class " + classname + ". " + e.getMessage());
            pe.initCause(e);
            throw pe;
        }
    }

    // --------------------------------UI ADAPTER------------------------------

    public static UIAdapter getUIAdapter(String type) throws ParseException {
        if (type != null) {
            try {
                StandardUIAdapter ui = StandardUIAdapter.valueOf(type);
                ui.init();
                return ui;
            } catch (IllegalArgumentException earg) {
                try {
                    return loadUIAdapter(type);
                } catch (ClassNotFoundException eclass) {
                    ParseException pe = new ParseException(type + " is neither an UIType nor can it be resolved to a Java class.");
                    pe.initCause(eclass);
                    pe.addSuppressed(earg);
                    throw pe;
                }
            }
        } else {
            if (System.console() != null) {
                return StandardUIAdapter.CMD;
            } else {
                return StandardUIAdapter.SWING;
            }
        }
    }

    public static UIAdapter loadUIAdapter(String classname) throws ParseException, ClassNotFoundException {
        try {
            Class<?> clazz = Class.forName(classname);
            Object instance = clazz.newInstance();
            if (!(instance instanceof UIAdapter)) {
                throw new ParseException(instance + " is not an UI adapter");
            }
            return (UIAdapter) instance;
        } catch (InstantiationException | IllegalAccessException e) {
            ParseException pe = new ParseException("Could not instantiate class " + classname + ". " + e.getMessage());
            pe.initCause(e);
            throw pe;
        }
    }

    // --------------------------------RESET-----------------------------------

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
}
