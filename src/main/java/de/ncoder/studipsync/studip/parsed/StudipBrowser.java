package de.ncoder.studipsync.studip.parsed;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.StudipException;
import de.ncoder.studipsync.studip.UIAdapter;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static de.ncoder.studipsync.Loggers.LOG_NAVIGATE;
import static de.ncoder.studipsync.Loggers.LOG_SEMINARS;
import static de.ncoder.studipsync.Values.*;

public class StudipBrowser implements StudipAdapter {
    private final UIAdapter ui;
    private final Path cookiesPath;

    private Seminar currentSeminar;

    public StudipBrowser(UIAdapter ui, Path cookiesPath) {
        this.ui = ui;
        this.cookiesPath = cookiesPath;
    }

    // --------------------------------LIFECYCLE-------------------------------

    @Override
    public void init() throws StudipException {
        con = HttpConnection.connect(PAGE_COVER);
        navigate(PAGE_COVER);
    }

    @Override
    public void close() throws IOException {
        ui.close();
    }

    // --------------------------------BROWSER---------------------------------

    private Connection con;
    private Document document;
    private final List<NavigationListener> listeners = new ArrayList<>();

    private void setDocument(Document document) throws StudipException {
        this.document = document;
        try {
            URL url = new URL(document.baseUri());
            for (NavigationListener listener : listeners) {
                listener.navigated(url);
            }
        } catch (MalformedURLException e) {
            StudipException ex = new StudipException("Illegal URL " + document.baseUri(), e);
            ex.put("studip.url", document.baseUri());
            ex.put("studip.document", document);
            throw ex;
        }
    }

    protected void navigate(String url) throws StudipException {
        try {
            con.url(url);
            con.timeout((int) NAVIGATE_TIMEOUT);
            try {
                setDocument(con.get());
            } catch (IOException e) {
                throw new StudipException("Can't navigate to " + url, e);
            }
        } catch (StudipException ex) {
            ex.put("navigate.url", url);
            ex.put("navigate.connection", con);
            ex.put("navigate.document", document);
            throw ex;
        }
    }

    @Override
    public InputStream startDownload(Download download, boolean diffOnly) throws IOException, StudipException {
        try {
            URL url;
            if (diffOnly) {
                url = download.getDiffUrl();
            } else {
                url = download.getFullUrl();
            }
            URLConnection urlCon = url.openConnection();
            if (!(urlCon instanceof HttpURLConnection)) {
                StudipException ex = new StudipException(new IllegalArgumentException("Can only download via http"));
                ex.put("download.url", url);
                ex.put("download.urlConnection", urlCon);
                throw ex;
            }
            HttpURLConnection con = (HttpURLConnection) urlCon;
            con.setRequestProperty("Cookie", HttpConnection.Response.getRequestCookieString(this.con.request().cookies()));
            return con.getInputStream();
        } catch (StudipException ex) {
            ex.put("download.diffOnly", diffOnly);
            ex.put("download.download", download);
            throw ex;
        }
    }

    // --------------------------------LOG IN----------------------------------

    @Override
    public boolean doLogin() throws CancellationException, StudipException {
        try {
            navigate(PAGE_LOGIN);
            if (hasCookies()) {
                restoreCookies();
                navigate(PAGE_INDEXES[0]);
            } else {
                LoginData login = ui.requestLoginData();
                if (login != null) {
                    doLogin(login);
                    login.clean();
                } else {
                    throw new CancellationException("Login cancelled by user");
                }
            }

            if (isLoggedIn()) {
                saveCookies();
                return true;
            } else {
                deleteCookies();
                return false;
            }
        } catch (IOException e) {
            //TODO Should cookie exceptions be suppressed?
            StudipException ex = new StudipException(e);
            ex.put("studip.cookiesPath", cookiesPath);
            ex.put("studip.url", document == null ? "none" : document.baseUri());
            throw ex;
        }
    }

    protected void doLogin(LoginData login) throws StudipException {
        try {
            con.url(
                    "https://studip.uni-passau.de/studip/index.php"
            );
            con.method(Connection.Method.POST);
            con.timeout((int) NAVIGATE_TIMEOUT);

            con.header("Origin", "https://studip.uni-passau.de");
            con.header("Host", "studip.uni-passau.de");
            con.header("Content-Type", "application/x-www-form-urlencoded");
            con.header("Referer", "https://studip.uni-passau.de/studip/login.php");

            con.data("username", login.getUsername());
            con.data("password", new String(login.getPassword()));
            con.data("challenge", document.getElementById("challenge").val());
            con.data("login_ticket", document.getElementById("login_ticket").val());
            con.data("response", document.getElementById("response").val());
            con.data("resolution", "1366x768");
            con.data("submitbtn", "");
            try {
                //FIXME check for password leaks
                setDocument(con.post());
            } catch (IOException e) {
                throw new StudipException("Can't login", e);
            } finally {
                login.clean();
                con.data("password", "XXX");
            }
        } catch (StudipException ex) {
            ex.put("navigate.url", con == null || con.request() == null ? "none" : con.request().url());
            ex.put("navigate.connection", con);
            ex.put("navigate.document", document);
            throw ex;
        }
    }

    @Override
    public boolean isLoggedIn() {
        Elements selected = document.select("#toolbar .toolbar_menu li:last-of-type a");
        return selected.size() == 1 && "Logout".equals(selected.get(0).text().trim());
    }

    public void ensureLoggedIn() throws StudipException {
        while (!isLoggedIn()) {
            doLogin();
        }
    }

    // --------------------------------SELECTED SEMINAR------------------------

    @Override
    public void selectSeminar(Seminar seminar) throws StudipException {
        try {
            navigate(String.format(PAGE_SELECT_SEMINAR, seminar.getHash()));
            if (!isSeminarSelected(seminar)) {
                StudipException ex = new StudipException("Could not select Seminar " + seminar);
                ex.put("studip.url", document == null ? "none" : document.baseUri());
                ex.put("studip.document", document);
                throw ex;
            }
            if (currentSeminar != seminar) {
                LOG_SEMINARS.info("Selected " + seminar);
            }
            currentSeminar = seminar;
        } catch (StudipException ex) {
            ex.put("studip.newSeminar", seminar);
            ex.put("studip.currentSeminar", currentSeminar);
            throw ex;
        }
    }

    @Override
    public Seminar getSelectedSeminar() {
        return currentSeminar;
    }

    @Override
    public boolean isSeminarSelected(Seminar seminar) {
        Elements selected = document.select("#register");
        return selected.size() == 1 && seminar.getFullName().equals(selected.get(0).text().trim());
    }

    public void ensureCurrentSeminarSelected() throws StudipException {
        while (!isSeminarSelected(currentSeminar)) {
            selectSeminar(currentSeminar);
        }
    }

    // --------------------------------COOKIES---------------------------------

    public void saveCookies() throws IOException {
        LOG_NAVIGATE.info("Save Cookies");
        if (Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
        //TODO user JSON serialization
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(cookiesPath))) {
            oos.writeObject(con.request().cookies());
        }
    }

    public void restoreCookies() throws IOException {
        LOG_NAVIGATE.info("Restore Cookies");
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(cookiesPath))) {
            con.request().cookies().putAll((Map<String, String>) ois.readObject());
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IOException("Illegal data in cookies file " + cookiesPath, e);
        }
    }

    public void deleteCookies() throws IOException {
        if (Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
    }

    public boolean hasCookies() {
        try {
            return Files.isRegularFile(cookiesPath) && Files.size(cookiesPath) > 0;
        } catch (IOException e) {
            LOG_NAVIGATE.warn("Couldn't read cookies from " + cookiesPath + ", prompting for password", e);
            return false;
        }
    }

    // --------------------------------PARSERS---------------------------------

    @Override
    public List<Seminar> parseSeminars() throws StudipException {
        ensureLoggedIn();

        navigate(PAGE_SEMINARS);

        Elements events = document.select("#content>table:first-of-type>tbody>tr");
        List<Seminar> seminars = new ArrayList<>();
        for (org.jsoup.nodes.Element event : events) {
            if (event.select(">td").size() > 4) {
                Elements info = event.select(">td:nth-of-type(4)>a:first-of-type");
                Elements font = info.select("font");
                if (info.size() >= 1 && font.size() >= 2) {
                    Seminar seminar = Seminar.getSeminar(info.get(0).absUrl("href"), font.get(0).text().trim(), font.get(1).text().trim());
                    seminars.add(seminar);
                }
            }
        }
        LOG_SEMINARS.info("Parsed " + seminars.size() + " seminars.");
        LOG_SEMINARS.debug(seminars.toString());
        return seminars;
    }

    @Override
    public List<Download> parseDownloads(String downloadsUrl, boolean structured) throws StudipException {
        try {
            ensureLoggedIn();
            ensureCurrentSeminarSelected();

            navigate(downloadsUrl);
            Map<Integer, Download> stack = new HashMap<>();
            List<Download> downloads = new ArrayList<>();

            Elements rows = document.select("#content>table>tbody>tr:nth-of-type(2)>td:nth-of-type(2)>table>tbody>tr>td>table");
            for (org.jsoup.nodes.Element row : rows) {
                Elements content = row.select(">tbody>tr>td.printhead");
                Elements insets = row.select(">tbody>tr>td.blank img");
                if (content.size() >= 2) {
                    Elements info = content.get(1).select("a");
                    Elements link = content.get(2).select("a[title]");
                    List<TextNode> time = content.get(2).textNodes();
                    if (info.size() > 0 && link.size() > 0 && time.size() > 0) {
                        Download download = Download.getDownload(
                                link.get(0).absUrl("href"),
                                info.get(0).text().trim(),
                                time.get(time.size() - 1).text().trim().replace("\u00a0", ""),
                                "");
                        download.setSeminar(currentSeminar);
                        int level = insets.size() - 3;
                        if (level > 0) {
                            download.setParent(stack.get(level - 1));
                        } else {
                            download.setLevel(level);
                        }
                        stack.put(download.getLevel(), download);
                        downloads.add(download);
                        //TODO read size, description
                    }
                }
            }
            return downloads;
        } catch (StudipException ex) {
            ex.put("studip.seminar", currentSeminar);
            ex.put("parseDownloads.listUrl", downloadsUrl);
            ex.put("parseDownloads.structured", structured);
            throw ex;
        }
    }

    // --------------------------------LISTENERS-------------------------------

    public static interface NavigationListener {
        public abstract void navigated(URL url);
    }

    public boolean addNavigationListener(NavigationListener navigationListener) {
        return listeners.add(navigationListener);
    }

    public boolean removeNavigationListener(NavigationListener o) {
        return listeners.remove(o);
    }
}
