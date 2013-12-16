package de.ncoder.studipsync.swt;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutionException;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;

import com.google.common.util.concurrent.SettableFuture;

import de.ncoder.studipsync.adapter.AbstractBrowserAdapter;
import de.ncoder.studipsync.adapter.BrowserAdapter;
import de.ncoder.studipsync.adapter.BrowserException;
import de.ncoder.studipsync.adapter.PageListener;

public class SWTBrowserAdapter extends AbstractBrowserAdapter implements BrowserAdapter {
	private Browser browser;
	private String url = "about:blank";

	public SWTBrowserAdapter(final Browser browser) {
		super();
		this.browser = browser;
		browser.setCapture(true);
		browser.setJavascriptEnabled(true);
		browser.addProgressListener(new ProgressAdapter() {
			@Override
			public void changed(ProgressEvent event) {
				// ThreadLocalProgress.set(event.current, event.total);
			}

			@Override
			public void completed(ProgressEvent event) {
				// ThreadLocalProgress.set(event.current, event.total);
				url = browser.getUrl();
				for (PageListener l : listeners) {
					l.pageLoaded(url);
				}
			}
		});
	}

	@Override
	public String getText() {
		final SettableFuture<String> value = SettableFuture.create();
		browser.getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					value.set((String) browser.evaluate("return (document.documentElement!=null ? document.documentElement.innerHTML : null)+'';"));
				} catch (Exception e) {
					value.setException(e);
				}
			}
		});
		try {
			return value.get();
		} catch (InterruptedException | ExecutionException e) {
			rethrowSneaky(e);
			throw new IllegalStateException("Couldn't rethrow " + e);
		}
	}

	@Override
	public String getUrl() {
		return url;
		// final SettableFuture<String> value = SettableFuture.create();
		// browser.getDisplay().syncExec(new Runnable() {
		// @Override
		// public void run() {
		// try {
		// value.set(browser.getUrl());
		// } catch (Exception e) {
		// value.setException(e);
		// }
		// }
		// });
		// try {
		// return value.get();
		// } catch (InterruptedException | ExecutionException e) {
		// rethrowSneaky(e);
		// throw new IllegalStateException("Couldn't rethrow " + e);
		// }
	}

	@Override
	public void execute(final String js) throws BrowserException {
		final SettableFuture<Boolean> value = SettableFuture.create();
		browser.getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					value.set(browser.execute(js));
				} catch (Exception e) {
					value.setException(e);
				}
			}
		});
		try {
			if (!value.get()) {
				throw new BrowserException("Script has not been executed");
			}
		} catch (ExecutionException | InterruptedException e) {
			rethrow(e);
			throw new IllegalStateException("Couldn't rethrow " + e);
		}
	}

	@Override
	public Object evaluate(final String js) throws BrowserException {
		final SettableFuture<Object> value = SettableFuture.create();
		browser.getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					value.set(browser.evaluate(js));
				} catch (Exception e) {
					value.setException(e);
				}
			}
		});
		try {
			return value.get();
		} catch (InterruptedException | ExecutionException e) {
			rethrow(e);
			throw new IllegalStateException("Couldn't rethrow " + e);
		}
	}

	@Override
	public void navigate(final String url) throws BrowserException {
		final SettableFuture<Boolean> value = SettableFuture.create();
		browser.getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					value.set(browser.setUrl(url));
				} catch (Exception e) {
					value.setException(e);
				}
			}
		});
		try {
			if (!value.get()) {
				throw new BrowserException("Navigating to " + url + " has failed");
			}
		} catch (ExecutionException | InterruptedException e) {
			rethrow(e);
			throw new IllegalStateException("Couldn't rethrow " + e);
		}
	}

	@Override
	public InputStream download(String urlString) throws BrowserException {
		try {
			URL url = new URL(urlString);
			URLConnection urlCon = url.openConnection();
			if (!(urlCon instanceof HttpURLConnection)) {
				throw new BrowserException("Can only download via http");
			}
			HttpURLConnection con = (HttpURLConnection) urlCon;
			con.setRequestProperty("Cookie", (String) evaluate("return document.cookie;"));
			// ThreadLocalProgress.set(0, con.getContentLengthLong());
			return con.getInputStream();
		} catch (IOException e) {
			rethrow(e);
			throw new IllegalStateException("Couldn't rethrow " + e);
		}
	}
}
