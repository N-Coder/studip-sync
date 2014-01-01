package de.ncoder.studipsync;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("static-access")
public class Values {
    public static final String PAGE_COVER = "http://intelec.uni-passau.de";
    public static final String PAGE_BASE = "http://studip.uni-passau.de";
    public static final String PAGE_LOGIN = "http://studip.uni-passau.de/studip/login.php";
    public static final String PAGE_DO_LOGIN = "https://studip.uni-passau.de/studip/index.php";
    public static final String PAGE_SEMINARS = PAGE_BASE + "/studip/meine_seminare.php";
    public static final String PAGE_SELECT_SEMINAR = PAGE_BASE + "/studip/seminar_main.php?auswahl=%s";
    public static final String PAGE_DOWNLOADS = PAGE_BASE + "/studip/plugins.php?cmd=show&id=19&view=seminarFolders&order=name";
    public static final String PAGE_DOWNLOADS_LATEST = PAGE_BASE + "/studip/plugins.php?cmd=show&id=19&view=onlyFiles&order=chdate";

    public static final String ENCODING = "Cp1252";
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy - HH:mm"); // 26.08.2013 - 20:38

    public static final Options OPTIONS;
    public static final String OPTION_UI = "ui";
    public static final String OPTION_OUT = "o";
    public static final String OPTION_RESET = "r";
    public static final String OPTION_CHECK_LEVEL = "c";
    public static final String OPTION_COOKIES = "l";
    public static final String OPTION_NO_COOKIES = "n";
    public static final String OPTION_EXCLUDE = "x";
    public static final String OPTION_TIMEOUT = "t";

    public static enum UIType {
        SWING, CMD
    }

    static {
        OPTIONS = new Options();
        OPTIONS.addOption(Option.builder(OPTION_UI)
                .hasArg()
                .argName(Arrays.toString(UIType.values()))
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

    public static Map<String, Object> zipFSOptions(boolean create) {
        Map<String, Object> options = new HashMap<>();
        options.put("create", create + "");
        options.put("encoding", ENCODING);
        return options;
    }
}
