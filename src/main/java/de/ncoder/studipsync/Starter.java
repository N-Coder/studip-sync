package de.ncoder.studipsync;

import de.ncoder.studipsync.storage.LocalStorage;
import de.ncoder.studipsync.studip.jsoup.JsoupStudipAdapter;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.net.URL;

import static de.ncoder.studipsync.StarterOptions.*;
import static de.ncoder.studipsync.Values.LOG_MAIN;
import static de.ncoder.studipsync.Values.LOG_NAVIGATE;

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
        StarterOptions options = new StarterOptions();
        options.set(cmd);
        if (cmd.hasOption(OPTION_RESET)) {
            LocalStorage.reset(options.getCachePath(), options.getCookiesPath());
        }
        return createSyncer(options);
    }

    public static Syncer createSyncer(StarterOptions options) throws IOException {
        LOG_MAIN.info("Sync to " + options.getCachePath().toAbsolutePath());

        LocalStorage storage = LocalStorage.open(options.getCachePath());
        if (options.getPathResolver() != null) {
            storage.setPathResolverDelegate(options.getPathResolver());
        }
        JsoupStudipAdapter browser = new JsoupStudipAdapter(options.getUIAdapter(), options.getCookiesPath(), options.getTimeoutMs());
        browser.addNavigationListener(new JsoupStudipAdapter.NavigationListener() {
            @Override
            public void navigated(URL url) {
                LOG_NAVIGATE.debug(url.toString());
            }
        });

        Syncer syncer = new Syncer(
                browser,
                storage
        );
        syncer.setCheckLevel(options.getCheckLevel());
        return syncer;
    }
}
