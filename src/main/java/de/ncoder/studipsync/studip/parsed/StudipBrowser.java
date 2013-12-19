package de.ncoder.studipsync.studip.parsed;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.UIAdapter;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import static de.ncoder.studipsync.Loggers.LOG_NAVIGATE;
import static de.ncoder.studipsync.Loggers.LOG_SEMINARS;
import static de.ncoder.studipsync.Values.*;

public class StudipBrowser implements StudipAdapter {
    private final UIAdapter ui;
    private Path cookiesPath;

    private Seminar currentSeminar;

    public StudipBrowser(UIAdapter ui, Path cookiesPath) throws IOException, URISyntaxException {
        this.ui = ui;
        this.cookiesPath = cookiesPath;
    }

    // --------------------------------LIFECYCLE-------------------------------

    @Override
    public void init() throws ExecutionException {
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
    private List<NavigationListener> listeners = new ArrayList<>();

    private int loadCount = 0;

    private void setDocument(Document document) {
        this.document = document;
        try {
            URL url = new URL(document.baseUri());
            for (NavigationListener listener : listeners) {
                listener.navigated(url);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    protected void navigate(String url) throws ExecutionException {
        con.url(url);
        con.timeout((int) NAVIGATE_TIMEOUT);
        try {
            setDocument(con.get());
            //TODO store cookies
            //cookies.putAll(con.request().cookies());
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public InputStream startDownload(Download download, boolean diffOnly) throws IOException, ExecutionException {
        URL url;
        if (diffOnly) {
            url = download.getDiffUrl();
        } else {
            url = download.getFullUrl();
        }
        URLConnection urlCon = url.openConnection();
        if (!(urlCon instanceof HttpURLConnection)) {
            throw new ExecutionException(new IllegalStateException("Can only download via http"));
        }
        HttpURLConnection con = (HttpURLConnection) urlCon;
        con.setRequestProperty("Cookie", HttpConnection.Response.getRequestCookieString(this.con.request().cookies()));
        return con.getInputStream();
    }

    // --------------------------------LOG IN----------------------------------

    @Override
    public boolean doLogin() throws CancellationException, ExecutionException {
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
            throw new ExecutionException(e);
        }
    }

    protected void doLogin(LoginData login) throws ExecutionException {
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
            setDocument(con.post());
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public boolean isLoggedIn() throws ExecutionException {
        Elements selected = document.select("#toolbar .toolbar_menu li:last-of-type a");
        return selected.size() == 1 && "Logout".equals(selected.get(0).text().trim());
    }

    public void ensureLoggedIn() throws ExecutionException {
        while (!isLoggedIn()) {
            doLogin();
        }
    }

    // --------------------------------SELECTED SEMINAR------------------------

    @Override
    public void selectSeminar(Seminar seminar) throws ExecutionException {
        navigate(String.format(PAGE_SELECT_SEMINAR, seminar.getHash()));
        if (!isSeminarSelected(seminar)) {
            throw new ExecutionException(new IllegalStateException("Could not select Seminar " + seminar));
        }
        if (currentSeminar != seminar) {
            LOG_SEMINARS.info("Selected " + seminar);
        }
        currentSeminar = seminar;
    }

    @Override
    public Seminar getSelectedSeminar() {
        return currentSeminar;
    }

    @Override
    public boolean isSeminarSelected(Seminar seminar) throws ExecutionException {
        Elements selected = document.select("#register");
        return selected.size() == 1 && seminar.getFullName().equals(selected.get(0).text().trim());
    }

    public void ensureCurrentSeminarSelected() throws ExecutionException {
        while (!isSeminarSelected(currentSeminar)) {
            selectSeminar(currentSeminar);
        }
    }

    // --------------------------------COOKIES---------------------------------

    public void saveCookies() throws ExecutionException, IOException {
        LOG_NAVIGATE.info("Save Cookies");
        if (Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(cookiesPath))) {
            oos.writeObject(con.request().cookies());
        }
    }

    public void restoreCookies() throws ExecutionException, IOException {
        LOG_NAVIGATE.info("Restore Cookies");
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(cookiesPath))) {
            con.request().cookies().putAll((Map<String, String>) ois.readObject());
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new ExecutionException(e);
        }
    }

    public void deleteCookies() throws IOException {
        if (Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
    }

    public boolean hasCookies() throws IOException {
        return Files.isRegularFile(cookiesPath) && Files.size(cookiesPath) > 0;
    }

    // --------------------------------PARSERS---------------------------------

    @Override
    public List<Seminar> parseSeminars() throws ExecutionException {
        try {
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
            LOG_SEMINARS.info("Updated " + seminars.size() + " seminars.");
            LOG_SEMINARS.debug(seminars.toString());
            return seminars;
        } catch (MalformedURLException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public List<Download> parseDownloads(String downloadsUrl, boolean structured) throws ExecutionException {
        try {
            ensureLoggedIn();
            ensureCurrentSeminarSelected();

            navigate(downloadsUrl);
            Map<Integer, Download> stack = new HashMap<>();
            List<Download> downloads = new ArrayList<>();

            Elements rows = document.select("#content>table>tbody>tr:nth-of-type(2)>td:nth-of-type(2)>table>tbody>tr>td>table");
            for (org.jsoup.nodes.Element row : rows) {
                Elements content = row.select(">tbody>tr>td.printhead");
                Elements insets = row.select(">tbody>tr>td.blank img[src=\"https://studip.uni-passau.de/studip/pictures/forumleer.gif\"]");
                if (content.size() >= 2) {
                    Elements info = content.get(1).select("a");
                    Elements link = content.get(2).select("span a");
                    Elements time = content.get(2).select("span a~span");
                    if (info.size() > 0 && link.size() > 0 && time.size() > 0) {
                        Download download = Download.getDownload(
                                link.get(0).absUrl("href"),
                                info.get(0).text().trim(),
                                time.get(0).text().trim(),
                                "");
                        download.setSeminar(currentSeminar);
                        int level = insets.size() - 2;
                        if (level >= 0) {
                            download.setParent(stack.get(level - 1));
                            stack.put(download.getLevel(), download);
                        }
                        downloads.add(download);
                        //TODO read size, description
                    }
                }
            }
            return downloads;
        } catch (MalformedURLException e) {
            throw new ExecutionException(e);
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
