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

package de.ncoder.studipsync;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.storage.LocalStorage;
import de.ncoder.studipsync.storage.Storage;
import de.ncoder.studipsync.storage.StorageLog;
import de.ncoder.studipsync.studip.jsoup.JsoupStudipAdapter;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

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
                log.info("Started " + getImplementationTitle() + " " + getImplementationVersion());
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

    public static String getImplementationTitle() {
        String title = Starter.class.getPackage().getImplementationTitle();
        return title == null ? "StudIP-Sync DEV" : title;
    }

    public static String getImplementationVersion() {
        String ver = Starter.class.getPackage().getImplementationVersion();
        return ver == null ? "SNAPSHOT" : ver;
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
        if (options.isPersitent()) {
            storage.registerListener(new Storage.StorageListener() {
                @Override
                public void onDelete(Download download, Path child) throws Storage.OperationVeto {
                    throw new Storage.OperationVeto();
                }

                @Override
                public void onUpdate(Download download, Path child, Path replacement) {
                }
            });
        }
        return syncer;
    }
}
