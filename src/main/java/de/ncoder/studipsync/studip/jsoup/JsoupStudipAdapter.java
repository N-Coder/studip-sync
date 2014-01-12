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

package de.ncoder.studipsync.studip.jsoup;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.StudipException;
import de.ncoder.studipsync.ui.UIAdapter;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class JsoupStudipAdapter implements StudipAdapter {
    private static final Logger log = LoggerFactory.getLogger(JsoupStudipAdapter.class);

    private final UIAdapter ui;
    private final Path cookiesPath;
    private final int timeoutMs;

    private Seminar currentSeminar;

    public JsoupStudipAdapter(UIAdapter ui, Path cookiesPath, int timeoutMs) {
        this.ui = ui;
        this.cookiesPath = cookiesPath;
        this.timeoutMs = timeoutMs;
    }

    // --------------------------------LIFECYCLE-------------------------------

    @Override
    public void init() throws StudipException {
        con = new HttpConnection();
        navigate(PAGE_COVER);
    }

    @Override
    public void close() throws IOException {
        ui.close();
    }

    // --------------------------------BROWSER---------------------------------

    private HttpConnection con;
    private Document document;
    private final List<NavigationListener> listeners = new ArrayList<>();

    private void setDocument(Document document) throws StudipException {
        this.document = document;
        try {
            URL url = new URL(document.baseUri());
            log.trace("NAV: " + url);
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
            con.timeout(timeoutMs);
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

    public void displayWebsite() {
        try {
            Path tmp = Files.createTempFile("studip-dump", ".html");
            Files.copy(new ByteArrayInputStream(document.outerHtml().getBytes()), tmp, StandardCopyOption.REPLACE_EXISTING);
            log.info("Displaying " + con.url());
            ui.displayWebpage(tmp.toUri());
        } catch (IOException e) {
            log.warn("Can't write page dump", e);
        }
    }

    // --------------------------------LOG IN----------------------------------

    @Override
    public boolean doLogin() throws CancellationException, StudipException {
        try {
            navigate(PAGE_LOGIN);
            if (hasCookies()) {
                restoreCookies();
                navigate(PAGE_BASE);
            } else {
                log.info("Requesting login data.");
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
            StudipException ex = new StudipException(e);
            ex.put("studip.cookiesPath", cookiesPath);
            ex.put("studip.url", document == null ? "none" : document.baseUri());
            throw ex;
        }
    }

    protected void doLogin(LoginData login) throws StudipException {
        try {
            con.url(PAGE_DO_LOGIN);
            con.method(Connection.Method.POST);
            con.timeout(timeoutMs);

            con.header("Origin", "https://studip.uni-passau.de");
            con.header("Host", "studip.uni-passau.de");
            con.header("Content-Type", "application/x-www-form-urlencoded");
            con.header("Referer", "https://studip.uni-passau.de/studip/login.php");

            con.data("username", login.getUsername());
            con.data("challenge", document.getElementById("challenge").val());
            con.data("login_ticket", document.getElementById("login_ticket").val());
            con.data("response", document.getElementById("response").val());
            con.data("resolution", "1366x768");
            con.data("submitbtn", "");
            con.data("password", new String(login.getPassword()));
            login.clean();
            try {
                //FIXME check for password leaks
                setDocument(con.post());
            } catch (IOException e) {
                throw new StudipException("Can't login", e);
            } finally {
                con.data("password", "XXX");
                login.clean();
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
                log.debug("Selected " + seminar);
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
        if (cookiesPath != null) {
            log.info("Save Cookies");
            if (Files.exists(cookiesPath)) {
                Files.delete(cookiesPath);
            }
            try (Writer w = Files.newBufferedWriter(cookiesPath, Charset.defaultCharset())) {
                w.write(JSONValue.toJSONString(con.request().cookies()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void restoreCookies() throws IOException {
        if (cookiesPath != null) {
            log.info("Restore Cookies");
            try (Reader r = Files.newBufferedReader(cookiesPath, Charset.defaultCharset())) {
                Object o = new JSONParser().parse(r);
                con.request().cookies().putAll((Map) o);
            } catch (org.json.simple.parser.ParseException | ClassCastException e) {
                throw new IOException("Illegal data in cookies file " + cookiesPath, e);
            }
        }
    }

    public void deleteCookies() throws IOException {
        if (cookiesPath != null && Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
    }

    public boolean hasCookies() {
        try {
            return cookiesPath != null && Files.isRegularFile(cookiesPath) && Files.size(cookiesPath) > 0;
        } catch (IOException e) {
            log.warn("Couldn't read cookies from " + cookiesPath + ", prompting for password", e);
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
        log.debug("Parsed " + seminars.size() + " seminars.");
        log.trace(seminars.toString());
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
            log.debug("Parsed " + downloads.size() + " downloads.");
            log.trace(downloads.toString());
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
