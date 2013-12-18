package de.ncoder.studipsync.studip.js;

import com.google.common.util.concurrent.SettableFuture;
import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.data.NotifyListener;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.UIAdapter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.ncoder.studipsync.Loggers.LOG_NAVIGATE;
import static de.ncoder.studipsync.Loggers.LOG_SEMINARS;
import static de.ncoder.studipsync.Values.*;

//TODO extract interface, abstract class; rename to StudipBrowserAdapter
public class StudipBrowser implements StudipAdapter {
    private final BrowserAdapter browser;
    private final UIAdapter ui;
    private Path cookiesPath;

    private Seminar currentSeminar;

    public StudipBrowser(BrowserAdapter browser, UIAdapter ui, Path cookiesPath) throws IOException, URISyntaxException {
        this.browser = browser;
        this.ui = ui;
        this.cookiesPath = cookiesPath;
    }

    // --------------------------------BROWSER LIFECYCLE-----------------------

    @Override
    public void init() throws ExecutionException {
        try {
            browser.navigate(PAGE_COVER, NAVIGATE_TIMEOUT);
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
//        } catch (IOException e) {
//            LOG_NAVIGATE.debug("Init could not restore Cookies", e);
        }
    }

    @Override
    public void close() throws IOException {
        ui.close();
    }

    // --------------------------------LOG IN----------------------------------

    @Override
    public boolean doLogin() throws CancellationException, ExecutionException {
        try {
            final SettableFuture<Boolean> success = SettableFuture.create();
            BrowserListener listener = new BrowserListener() {
                @Override
                public void pageLoaded(String url) {
                    try {
                        success.set(isLoggedIn());
                    } catch (ExecutionException e) {
                        success.setException(e);
                    }
                    browser.removeBrowserListener(this);
                }
            };

            browser.navigate(PAGE_LOGIN, NAVIGATE_TIMEOUT);
            if (hasCookies()) {
                browser.addBrowserListener(listener);
                restoreCookies();
                browser.navigate(PAGE_INDEXES[0], NAVIGATE_TIMEOUT);
            } else {
                LoginData login = ui.requestLoginData();
                if (login != null) {
                    //FIXME don't put password in String
                    browser.addBrowserListener(listener);
                    _doLogin(login);
                    login.clean();
                } else {
                    throw new CancellationException("Login cancelled by user");
                }
            }

            if (success.get()) {
                saveCookies();
                return true;
            } else {
                deleteCookies();
                return false;
            }
        } catch (InterruptedException | IOException | TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public boolean isLoggedIn() throws ExecutionException {
        return _isLoggedIn();
    }

    public void ensureLoggedIn() throws ExecutionException {
        while (!isLoggedIn()) {
            doLogin();
        }
    }

    // --------------------------------SELECTED SEMINAR------------------------

    @Override
    public void selectSeminar(Seminar seminar) throws ExecutionException {
        NotifyListener notifyListener = browser.allocateNotifyListener();
        try {
            browser.navigate(String.format(PAGE_SELECT_SEMINAR, seminar.getHash()));

            long end = System.currentTimeMillis() + NAVIGATE_TIMEOUT;
            while (System.currentTimeMillis() < end && !isSeminarSelected(seminar)) {
                notifyListener.awaitNotify(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            }

            if (!isSeminarSelected(seminar)) {
                throw new ExecutionException(new IllegalStateException("Could not select Seminar " + seminar));
            }
            if (currentSeminar != seminar) {
                LOG_SEMINARS.info("Selected " + seminar);
            }
            currentSeminar = seminar;
        } catch (InterruptedException e) {
            throw new ExecutionException(e);
        } finally {
            notifyListener.release();
        }
    }

    @Override
    public boolean isSeminarSelected(Seminar seminar) throws ExecutionException {
        return _isSeminarSelected(seminar);
    }

    @Override
    public Seminar getSelectedSeminar() {
        return currentSeminar;
    }

    public void ensureCurrentSeminarSelected() throws ExecutionException {
        while (!isSeminarSelected(currentSeminar)) {
            selectSeminar(currentSeminar);
        }
    }

    // --------------------------------COOKIES---------------------------------

    public void saveCookies() throws ExecutionException, IOException {
        Object raw = browser.getCookies();
        LOG_NAVIGATE.info("Save Cookies");
        if (Files.exists(cookiesPath)) {
            Files.delete(cookiesPath);
        }
        Files.write(cookiesPath, raw.toString().getBytes());
    }

    public void restoreCookies() throws ExecutionException, IOException {
        String raw = new String(Files.readAllBytes(cookiesPath));
        LOG_NAVIGATE.info("Restore Cookies");
        browser.setCookies(raw);
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

            browser.navigate(PAGE_SEMINARS, NAVIGATE_TIMEOUT);
            JSONArray raw = _parseSeminars();
            List<Seminar> seminars = new ArrayList<>(raw.size());
            for (Object it : raw) {
                Seminar seminar = Seminar.getSeminar((JSONObject) it);
                seminar.update((JSONObject) it);
                seminars.add(seminar);
            }
            LOG_SEMINARS.info("Updated " + seminars.size() + " seminars.");
            LOG_SEMINARS.debug(seminars.toString());
            return seminars;
        } catch (TimeoutException | MalformedURLException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public List<Download> parseDownloads(String downloadsUrl, boolean structured) throws ExecutionException {
        try {
            ensureLoggedIn();
            ensureCurrentSeminarSelected();

            browser.navigate(downloadsUrl, NAVIGATE_TIMEOUT);
            JSONArray raw = _parseDownloads();
            Map<Integer, Download> stack = new HashMap<>();
            List<Download> downloads = new ArrayList<>(raw.size());
            for (Object r : raw) {
                JSONObject it = (JSONObject) r;
                Download download = Download.getDownload(it);
                download.setSeminar(currentSeminar);
                download.update(it);

                if (download.getLevel() >= 0) {
                    download.setParent(stack.get(download.getLevel() - 1));
                    stack.put(download.getLevel(), download);
                }

                downloads.add(download);
            }
            return downloads;
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    // --------------------------------DOWNLOAD--------------------------------

    @Override
    public InputStream startDownload(Download download, boolean diffOnly) throws IOException, ExecutionException {
        URL src;
        if (diffOnly) {
            src = download.getDiffUrl();
        } else {
            src = download.getFullUrl();
        }
        return browser.download(src.toString());
    }

    // --------------------------------JS BINDINGS-----------------------------

    protected JSONArray _parseDownloads() throws ExecutionException {
        return (JSONArray) browser.evaluateJSON(JS_PARSE_DOWNLOADS);
    }

    protected JSONArray _parseSeminars() throws ExecutionException {
        return (JSONArray) browser.evaluateJSON(JS_PARSE_SEMINARS);
    }

    protected boolean _isSeminarSelected(Seminar seminar) throws ExecutionException {
        return (boolean) browser.evaluate(String.format(JS_CHECK_SEMINAR, seminar.getFullName()));
    }

    protected void _doLogin(LoginData login) throws ExecutionException {
        browser.execute(String.format(JS_DO_LOGIN, login.getUsername(), new String(login.getPassword())));
    }

    protected boolean _isLoggedIn() throws ExecutionException {
        return (boolean) browser.evaluate(JS_IS_LOGGED_IN);
    }
}
