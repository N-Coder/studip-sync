package de.ncoder.studipsync.studip;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.CancellationException;

public interface StudipAdapter {
    public static final String PAGE_COVER = "http://intelec.uni-passau.de";
    public static final String PAGE_BASE = "http://studip.uni-passau.de";
    public static final String PAGE_DOWNLOADS_LATEST = PAGE_BASE + "/studip/plugins.php?cmd=show&id=19&view=onlyFiles&order=chdate";
    public static final String PAGE_DOWNLOADS = PAGE_BASE + "/studip/plugins.php?cmd=show&id=19&view=seminarFolders&order=name";
    public static final String PAGE_SELECT_SEMINAR = PAGE_BASE + "/studip/seminar_main.php?auswahl=%s";
    public static final String PAGE_SEMINARS = PAGE_BASE + "/studip/meine_seminare.php";
    public static final String PAGE_LOGIN = "http://studip.uni-passau.de/studip/login.php";
    public static final String PAGE_DO_LOGIN = "https://studip.uni-passau.de/studip/index.php";

    public static final String PARAM_NEWEST_ONLY = "newestOnly";
    public static final String PARAM_FILE_ID = "file_id";
    public static final String PARAM_FOLDER_ID = "folder_id";
    public static final String PARAM_FILE_NAME = "file_name";
    public static final String PARAM_SEMINAR_SELECTION = "auswahl";

    public static final String[] URI_ILLEGAL_CHARS = new String[]{" ", ":", "ä", "ö", "ü", "Ä", "Ö", "Ü", "ß", "(", ")"};
    public static final String[] URI_REPLACE_CHARS = new String[]{"_", "", "ae", "oe", "ue", "Ae", "Oe", "Ue", "ss", "", ""};
    public static final String URI_ENCODING = "ISO-8859-1";
    public static final String ZIP_ENCODING = "Cp1252";

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy - HH:mm"); // 26.08.2013 - 20:38

    // ------------------------------------------------------------------------

    public void init() throws StudipException;

    public void close() throws IOException;

    public void displayWebsite();

    public boolean doLogin() throws CancellationException, StudipException;

    public boolean isLoggedIn();

    public void selectSeminar(Seminar seminar) throws StudipException;

    public boolean isSeminarSelected(Seminar seminar);

    public Seminar getSelectedSeminar();

    public List<Seminar> parseSeminars() throws StudipException;

    public List<Download> parseDownloads(String downloadsUrl, boolean structured) throws StudipException;

    public InputStream startDownload(Download download, boolean diffOnly) throws StudipException, IOException;
}
