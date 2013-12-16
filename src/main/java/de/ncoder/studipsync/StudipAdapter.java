package de.ncoder.studipsync;

import static de.ncoder.studipsync.Loggers.LOG_DOWNLOAD;
import static de.ncoder.studipsync.Loggers.LOG_NAVIGATE;
import static de.ncoder.studipsync.Loggers.LOG_SEMINARS;
import static de.ncoder.studipsync.Values.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.util.concurrent.SettableFuture;

import de.ncoder.studipsync.adapter.BrowserAdapter;
import de.ncoder.studipsync.adapter.BrowserException;
import de.ncoder.studipsync.adapter.NotifyListener;
import de.ncoder.studipsync.adapter.PageListener;
import de.ncoder.studipsync.adapter.UIAdapter;
import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.data.Seminar;

public class StudipAdapter {
	private final BrowserAdapter browser;
	private final UIAdapter ui;
	private final LocalStorage storage;

	private Seminar currentSeminar;

	public StudipAdapter(BrowserAdapter browser, UIAdapter ui, Path cachePath) throws IOException, URISyntaxException {
		this.browser = browser;
		this.ui = ui;
		storage = LocalStorage.open(new URI("jar", cachePath.toUri().toString(), ""));
		browser.addPageListener(new PageListener() {
			@Override
			public void pageLoaded(String url) {
				try {
					StudipAdapter.this.browser.execute(JS_LIB);
				} catch (BrowserException e) {
					LOG_NAVIGATE.error("Could not inject lib", e);
				}
				LOG_NAVIGATE.info(url);
				try {
					LOG_NAVIGATE.debug("loggedIn: " + isLoggedIn());
				} catch (BrowserException e) {
					LOG_NAVIGATE.error("Could not query isLoggedIn", e);
				}
				// Files.copy(new ByteArrayInputStream(StudipAdapter.this.browser.getText().getBytes()), live);
			}
		});
	}

	// --------------------------------SET BROWSER STATE-----------------------

	public void init() throws BrowserException, TimeoutException {
		browser.navigate(PAGE_COVER, NAVIGATE_TIMEOUT);
	}

	public boolean doLogin() throws CancellationException, TimeoutException, InterruptedException, ExecutionException, IOException {
		final SettableFuture<Boolean> success = SettableFuture.create();
		PageListener listener = new PageListener() {
			@Override
			public void pageLoaded(String url) {
				try {
					success.set(isLoggedIn());
				} catch (BrowserException e) {
					success.setException(e);
				}
				browser.removePageListener(this);
			}
		};

		browser.navigate(PAGE_LOGIN, NAVIGATE_TIMEOUT);
		if (hasCookies()) {
			restoreCookies();
			browser.addPageListener(listener);
			browser.navigate(PAGE_INDEXES[0], NAVIGATE_TIMEOUT);
		} else {
			LoginData login = ui.requestLoginData();
			if (login != null) {
				browser.execute(String.format(JS_DO_LOGIN, login.getUsername(), login.getPassword()));
				browser.addPageListener(listener);
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
	}

	public void ensureLoggedIn() throws CancellationException, TimeoutException, InterruptedException, ExecutionException, IOException {
		while (!isLoggedIn()) {
			doLogin();
		}
	}

	public void selectSeminar(Seminar seminar) throws BrowserException, InterruptedException {
		NotifyListener notifyListener = browser.allocateNotifyListener();
		try {
			browser.navigate(String.format(PAGE_SELECT_SEMINAR, seminar.getHash()));

			long end = System.currentTimeMillis() + NAVIGATE_TIMEOUT;
			while (System.currentTimeMillis() < end && !isSeminarSelected(seminar)) {
				notifyListener.awaitNotify(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			}

			if (!isSeminarSelected(seminar)) {
				throw new BrowserException("Could not select Seminar " + seminar);
			}
			if (currentSeminar != seminar) {
				LOG_SEMINARS.info("Selected " + seminar);
			}
			currentSeminar = seminar;
		} finally {
			notifyListener.release();
		}
	}

	public void ensureCurrentSeminarSelected() throws BrowserException, InterruptedException {
		while (!isSeminarSelected(currentSeminar)) {
			selectSeminar(currentSeminar);
		}
	}

	public void saveCookies() throws BrowserException, IOException {
		Object raw = browser.evaluateJSON("return JSON.stringify(getCookies())");
		LOG_NAVIGATE.info("Save Cookies");
		if (Files.exists(storage.getCookiesPath())) {
			Files.delete(storage.getCookiesPath());
		}
		Files.write(storage.getCookiesPath(), raw.toString().getBytes());
	}

	public void restoreCookies() throws IOException, BrowserException {
		String raw = new String(Files.readAllBytes(storage.getCookiesPath()));
		LOG_NAVIGATE.info("Restore Cookies");
		browser.execute("setCookies(" + raw + ")");
	}

	public void deleteCookies() throws IOException {
		if (Files.exists(storage.getCookiesPath())) {
			Files.delete(storage.getCookiesPath());
		}
	}

	public void close() throws IOException {
		storage.close();
		ui.close();
	}

	// --------------------------------GET BROWSER STATE-----------------------

	public boolean isLoggedIn() throws BrowserException {
		return (boolean) browser.evaluate(JS_IS_LOGGED_IN);
	}

	public boolean isSeminarSelected(Seminar seminar) throws BrowserException {
		return (boolean) browser.evaluate(String.format(JS_CHECK_SEMINAR, seminar.getFullName()));
	}

	public boolean hasCookies() throws IOException {
		return Files.isRegularFile(storage.getCookiesPath()) && Files.size(storage.getCookiesPath()) > 0;
	}

	public LocalStorage getStorage() {
		return storage;
	}

	public Seminar getCurrentSeminar() {
		return currentSeminar;
	}

	// --------------------------------PARSERS---------------------------------

	public List<Seminar> parseSeminars() throws CancellationException, TimeoutException, InterruptedException, ExecutionException, IOException {
		ensureLoggedIn();

		browser.navigate(PAGE_SEMINARS, NAVIGATE_TIMEOUT);
		JSONArray raw = (JSONArray) browser.evaluateJSON(JS_PARSE_SEMINARS);
		List<Seminar> seminars = new ArrayList<Seminar>(raw.size());
		for (Object it : raw) {
			Seminar seminar = Seminar.getSeminar((JSONObject) it);
			seminar.update((JSONObject) it);
			seminars.add(seminar);
		}
		LOG_SEMINARS.info("Updated " + seminars.size() + " seminars.");
		LOG_SEMINARS.debug(seminars.toString());
		return seminars;
	}

	public List<Download> parseDownloads(String downloadsUrl, boolean structured) throws TimeoutException, CancellationException, InterruptedException, ExecutionException, IOException {
		ensureLoggedIn();
		ensureCurrentSeminarSelected();

		browser.navigate(downloadsUrl, NAVIGATE_TIMEOUT);
		JSONArray raw = (JSONArray) browser.evaluateJSON(JS_PARSE_DOWNLOADS);
		Map<Integer, Download> stack = new HashMap<Integer, Download>();
		List<Download> downloads = new ArrayList<Download>(raw.size());
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
	}

	// --------------------------------DOWNLOAD--------------------------------

	public Path download(Download download, boolean quickDiff) throws IOException, BrowserException, URISyntaxException {
		URL src;
		if (quickDiff) {
			src = download.getUrlNewest();
		} else {
			src = download.getUrl();
		}
		Path dst = Files.createTempFile(download.getFileName() + ".", ".tmp");

		LOG_DOWNLOAD.info(src.toURI() + " >> " + dst.toUri());
		try (InputStream is = browser.download(src.toString());//
				OutputStream os = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);) {
			// Files.copy(is, dst, StandardCopyOption.REPLACE_EXISTING);
			byte[] buf = new byte[1024];
			int read;
			while ((read = is.read(buf)) >= 0) {
				os.write(buf, 0, read);
				// ThreadLocalProgress.increment(read);
			}
		}
		LOG_DOWNLOAD.info("Downloaded " + Files.size(dst) + "B");

		return dst;
	}
}