package de.ncoder.studipsync;

import de.ncoder.studipsync.storage.LocalStorage;
import de.ncoder.studipsync.storage.StorageLog;
import de.ncoder.studipsync.studip.jsoup.JsoupStudipAdapter;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static de.ncoder.studipsync.StarterOptions.*;

public class Starter {
    private static final Logger log = LoggerFactory.getLogger(Starter.class);

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
            StorageLog storeLog = new StorageLog();
            syncer.getStorage().registerListener(storeLog);
            try {
                log.info("Started");
                syncer.sync();
                log.info(storeLog.getStatusMessage(syncer.getStorage().getRoot()));
                log.info("Finished");
            } finally {
                syncer.close();
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
        //TODO AWT Event Queue blocks termination with modality level 1
        System.exit(0);
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
        log.info("Sync to " + options.getCachePath().toAbsolutePath());

        LocalStorage storage = LocalStorage.open(options.getCachePath());
        if (options.getPathResolver() != null) {
            storage.setPathResolverDelegate(options.getPathResolver());
        }
        JsoupStudipAdapter browser = new JsoupStudipAdapter(options.getUIAdapter(), options.getCookiesPath(), options.getTimeoutMs());

        Syncer syncer = new Syncer(
                browser,
                storage
        );
        syncer.setCheckLevel(options.getCheckLevel());
        return syncer;
    }
}
