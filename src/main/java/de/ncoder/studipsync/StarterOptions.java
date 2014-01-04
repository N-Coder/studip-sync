package de.ncoder.studipsync;

import de.ncoder.studipsync.storage.PathResolver;
import de.ncoder.studipsync.storage.StandardPathResolver;
import de.ncoder.studipsync.ui.StandardUIAdapter;
import de.ncoder.studipsync.ui.UIAdapter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static de.ncoder.studipsync.Syncer.CheckLevel.*;

public class StarterOptions {
    public static final int DEFAULT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(2);
    public static final Path DEFAULT_CACHE_PATH = Paths.get(System.getProperty("user.dir"), "studip.zip");
    public static final Path DEFAULT_COOKIES_PATH = Paths.get(System.getProperty("user.dir"), "cookies.json");

    public static final Options OPTIONS;
    public static final String OPTION_HELP = "h";
    public static final String OPTION_UI = "ui";
    public static final String OPTION_OUT = "o";
    public static final String OPTION_RESET = "r";
    public static final String OPTION_CHECK_LEVEL = "c";
    public static final String OPTION_COOKIES = "l";
    public static final String OPTION_NO_COOKIES = "k";
    public static final String OPTION_PATH_RESOLVER = "n";
    public static final String OPTION_EXCLUDE = "x";
    public static final String OPTION_TIMEOUT = "t";

    static {
        OPTIONS = new Options();
        OPTIONS.addOption(Option.builder(OPTION_HELP)
                .longOpt("help")
                .desc("Print this message.")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_UI)
                .hasArg()
                .desc("How to prompt for login data.\n" +
                        "Available standard values: " + Arrays.toString(StandardUIAdapter.values()) + "\n" +
                        "Alternatively, the name of a Java class implementing " + UIAdapter.class.getSimpleName()
                        + " can be passed (the class must be accessible through the Java classpath).")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_OUT)
                .hasArg()
                .argName("file")
                .type(File.class)
                .longOpt("out")
                .desc("Directory or zip file used for storing downloads.")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_NO_COOKIES)
                .longOpt("noCookies")
                .desc("Disable cookies.")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_COOKIES)
                .hasArg()
                .argName("file")
                .type(File.class)
                .longOpt("cookies")
                .desc("File used for storing cookies.")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_PATH_RESOLVER)
                .hasArg()
                .longOpt("naming")
                .desc("Naming Strategy for downloaded files.\n" +
                        "Available standard values: " + Arrays.toString(StandardPathResolver.values()) + "\n" +
                        "Alternatively, the name of a Java class implementing " + PathResolver.class.getSimpleName()
                        + " can be passed (the class must be accessible through the Java classpath).")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_RESET)
                .desc("Delete all local data and resynchronize everything.")
                .longOpt("reset")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_CHECK_LEVEL) //TODO implement check levels
                .hasArg()
                .argName("level")
                .type(Number.class)
                .longOpt("check")
                .desc("Synchronicity check strictness:\n" +
                        None.ordinal() + ". " + None + ":\tDon't check at all\n" +
                        Count.ordinal() + ". " + Count + ":\tOnly check the number of files\n" +
                        Files.ordinal() + ". " + Files + ":\tCheck for matching filenames\n" +
                        ModTime.ordinal() + ". " + ModTime + ":\tCheck for matching last modified times\n" +
                        "X. " + All + ":\tPerform all checks"
                )
                .build());
        OPTIONS.addOption(Option.builder(OPTION_EXCLUDE) //TODO implement filtering
                .hasArg()
                .argName("REGEX")
                .longOpt("exclude")
                .desc("Exclude certain seminars.\n" +
                        "Currently not implemented.")
                .build());
        OPTIONS.addOption(Option.builder(OPTION_TIMEOUT)
                .hasArg()
                .argName("ms")
                .type(Number.class)
                .longOpt("timeout")
                .desc("Timeout in milliseconds for accessing studip.")
                .build());
    }

    // ------------------------------------------------------------------------

    private Path cachePath;
    private Path cookiesPath;
    private int timeoutMs;
    private Syncer.CheckLevel checkLevel;
    private UIAdapter uiAdapter;
    private PathResolver pathResolver;

    public StarterOptions() {
        this(
                DEFAULT_CACHE_PATH,
                DEFAULT_COOKIES_PATH,
                DEFAULT_TIMEOUT,
                Syncer.CheckLevel.Default,
                StandardUIAdapter.getDefaultUIAdapter(),
                StandardPathResolver.getDefaultPathResolver()
        );
    }

    public StarterOptions(Path cachePath, Path cookiesPath, int timeoutMs, Syncer.CheckLevel checkLevel, UIAdapter uiAdapter, PathResolver pathResolver) {
        this.cachePath = cachePath;
        this.cookiesPath = cookiesPath;
        this.timeoutMs = timeoutMs;
        this.checkLevel = checkLevel;
        this.uiAdapter = uiAdapter;
        this.pathResolver = pathResolver;
    }

    public void set(CommandLine cmd) throws ParseException {
        if (cmd.hasOption(OPTION_OUT)) {
            setCachePath(Paths.get(cmd.getOptionValue(OPTION_OUT)));
        }
        if (cmd.hasOption(OPTION_COOKIES)) {
            setCookiesPath(Paths.get(cmd.getOptionValue(OPTION_COOKIES)));
        }
        if (cmd.hasOption(OPTION_NO_COOKIES)) {
            setCookiesPath(null);
        }
        if (cmd.hasOption(OPTION_TIMEOUT)) {
            try {
                setTimeoutMs(Integer.parseInt(cmd.getOptionValue(OPTION_TIMEOUT)));
            } catch (NumberFormatException e) {
                throw new ParseException(e.getMessage());
            }
        }
        if (cmd.hasOption(OPTION_CHECK_LEVEL)) {
            setCheckLevel(Syncer.CheckLevel.get(cmd.getOptionValue(OPTION_CHECK_LEVEL)));
        }
        if (cmd.hasOption(OPTION_UI)) {
            setUIAdapter(StandardUIAdapter.getUIAdapter(cmd.getOptionValue(OPTION_UI)));
        }
        if (cmd.hasOption(OPTION_PATH_RESOLVER)) {
            setPathResolver(StandardPathResolver.getPathResolver(cmd.getOptionValue(OPTION_PATH_RESOLVER)));
        }
    }

    public Path getCachePath() {
        return cachePath;
    }

    public void setCachePath(Path cachePath) {
        this.cachePath = cachePath;
    }

    public Path getCookiesPath() {
        return cookiesPath;
    }

    public void setCookiesPath(Path cookiesPath) {
        this.cookiesPath = cookiesPath;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Syncer.CheckLevel getCheckLevel() {
        return checkLevel;
    }

    public void setCheckLevel(Syncer.CheckLevel checkLevel) {
        this.checkLevel = checkLevel;
    }

    public UIAdapter getUIAdapter() {
        return uiAdapter;
    }

    public void setUIAdapter(UIAdapter uiAdapter) {
        this.uiAdapter = uiAdapter;
    }

    public PathResolver getPathResolver() {
        return pathResolver;
    }

    public void setPathResolver(PathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    @Override
    public String toString() {
        return "Options{\n" +
                "\tcachePath=" + cachePath + ",\n" +
                "\tcookiesPath=" + cookiesPath + ",\n" +
                "\ttimeoutMs=" + timeoutMs + ",\n" +
                "\tcheckLevel=" + checkLevel + ",\n" +
                "\tuiAdapter=" + uiAdapter + ",\n" +
                "\tpathResolver=" + pathResolver + ",\n" +
                '}';
    }
}
