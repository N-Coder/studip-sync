package de.ncoder.studipsync;

import de.ncoder.studipsync.storage.StandardPathResolver;
import de.ncoder.studipsync.ui.StandardUIAdapter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("static-access")
public class Values {
    public static final Logger LOG_MAIN = LoggerFactory.getLogger("MAIN");
    public static final Logger LOG_SYNCER = LoggerFactory.getLogger("Sync");
    public static final Logger LOG_NAVIGATE = LoggerFactory.getLogger("Navigate");
    public static final Logger LOG_SEMINARS = LoggerFactory.getLogger("Seminar");
    public static final Logger LOG_DOWNLOAD = LoggerFactory.getLogger("Download");

    public static final int DEFAULT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(2);
    public static final Path DEFAULT_CACHE_PATH = Paths.get(System.getProperty("user.dir"), "studip.zip");
    public static final Path DEFAULT_COOKIES_PATH = Paths.get(System.getProperty("user.dir"), "cookies.json");

    public static final Options OPTIONS;
    public static final String OPTION_UI = "ui";
    public static final String OPTION_OUT = "o";
    public static final String OPTION_RESET = "r";
    public static final String OPTION_CHECK_LEVEL = "c";
    public static final String OPTION_COOKIES = "l";
    public static final String OPTION_NO_COOKIES = "k";
    public static final String OPTION_PATH_REOLVER = "n";
    public static final String OPTION_EXCLUDE = "x";
    public static final String OPTION_TIMEOUT = "t";

    static {
        OPTIONS = new Options();
        OPTIONS.addOption(Option.builder(OPTION_UI)
                .hasArg()
                .argName(Arrays.toString(StandardUIAdapter.values()) + " or Java Classname")
                .desc("how to prompt for login data")
                        //.type(UIOption.class)
                .build());
        OPTIONS.addOption(Option.builder(OPTION_OUT)
                .hasArg()
                .argName("directory|zip")
                .type(File.class)
                .longOpt("out")
                .desc("directory or zip file used for storing the downloads")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_NO_COOKIES)
                .longOpt("noCookies")
                .desc("disable cookies")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_COOKIES)
                .hasArg()
                .argName("file")
                .type(File.class)
                .longOpt("cookies")
                .desc("file used for storing cookies")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_PATH_REOLVER)
                .hasArg()
                .argName(Arrays.toString(StandardPathResolver.values()) + " or Java Classname")
                .longOpt("naming")
                .desc("how to name downloaded files")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_RESET)
                .desc("delete all local data and resynchronize")
                .longOpt("reset")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_CHECK_LEVEL) //TODO
                .hasArg()
                .argName("[1-10]")
                .type(Number.class)
                .longOpt("check")
                .desc("check strictness:")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_EXCLUDE) //TODO
                .hasArg()
                .argName("REGEX")
                .longOpt("exclude")
                .desc("exclude certain seminars")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_TIMEOUT)
                .hasArg()
                .argName("ms")
                .type(Number.class)
                .longOpt("timeout")
                .desc("timeout in milliseconds for accessing studip")
                .build());
    }

    private Values() {
    }
}
