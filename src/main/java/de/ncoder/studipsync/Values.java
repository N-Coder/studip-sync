package de.ncoder.studipsync;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Values {
    public static final String PAGE_COVER = "http://intelec.uni-passau.de";
    public static final String PAGE_BASE = "http://studip.uni-passau.de";
    public static final String[] PAGE_INDEXES = new String[]{PAGE_BASE, PAGE_BASE + "/studip", PAGE_BASE + "/studip/index.php"};
    public static final String PAGE_LOGIN = PAGE_BASE + "/studip/login.php";
    public static final String PAGE_DO_LOGIN = "https://studip.uni-passau.de/studip/index.php";
    public static final String PAGE_LOGOUT = PAGE_BASE + "/studip/logout.php";
    public static final String PAGE_SEMINARS = PAGE_BASE + "/studip/meine_seminare.php";

    public static final String PAGE_SELECT_SEMINAR = PAGE_BASE + "/studip/seminar_main.php?auswahl=%s";
    public static final String PAGE_DOWNLOADS = PAGE_BASE + "/studip/plugins.php?cmd=show&id=19&view=seminarFolders&order=name";
    public static final String PAGE_DOWNLOADS_LATEST = PAGE_BASE + "/studip/plugins.php?cmd=show&id=19&view=onlyFiles&order=chdate";

    public static final String ENCODING = "Cp1252";
    public static final long NAVIGATE_TIMEOUT = TimeUnit.SECONDS.toMillis(100);
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy - HH:mm"); // 26.08.2013 - 20:38

//    public static final String JS_ENV = readResource("/env.rhino.1.2.js");
//    public static final String JS_LIB = readResource("/lib.js");
//    public static final String JS_IS_LOGGED_IN = readResource("/isLoggedIn.js");
//    public static final String JS_DO_LOGIN = readResource("/doLogin.js");
//    public static final String JS_CHECK_SEMINAR = readResource("/checkSeminar.js");
//    public static final String JS_PARSE_SEMINARS = readResource("/parseSeminars.js");
//    public static final String JS_PARSE_DOWNLOADS = readResource("/parseDownloads.js");

    private Values() {
    }

//    private static String readResource(String resName) {
//        StringBuilder bob = new StringBuilder();
//        try (BufferedReader r = new BufferedReader(new InputStreamReader(StudipBrowser.class.getResourceAsStream(resName)))) {
//            String line;
//            while ((line = r.readLine()) != null) {
//                bob.append(line);
//                bob.append(System.lineSeparator());
//            }
//            return bob.toString();
//        } catch (IOException e) {
//            System.err.println("Could not read static resource " + resName);
//            e.printStackTrace();
//        }
//        return "";
//    }

    public static Map<String, Object> zipFSOptions(boolean create) {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("create", create + "");
        options.put("encoding", ENCODING);
        return options;
    }
}
