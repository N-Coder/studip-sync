package de.ncoder.studipsync.adapter;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractBrowserAdapter implements BrowserAdapter {
	protected List<PageListener> listeners = new LinkedList<PageListener>();

	public AbstractBrowserAdapter() {
		super();
	}

	@Override
	public boolean addPageListener(PageListener e) {
		return listeners.add(e);
	}

	@Override
	public boolean removePageListener(Object o) {
		return listeners.remove(o);
	}

	@Override
	public void navigate(String url, long waitMs) throws BrowserException, TimeoutException {
		NotifyListener notifyListener = allocateNotifyListener();
		try {
			navigate(url);
			long end = System.currentTimeMillis() + waitMs;
			while (System.currentTimeMillis() < end && !getUrl().contains(url)) {
				notifyListener.awaitNotify(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			}
			if (!getUrl().contains(url)) {
				throw new TimeoutException();
			}
		} catch (InterruptedException e) {
			throw new BrowserException(e);
		} finally {
			notifyListener.release();
		}
	}

	@Override
	public NotifyListener allocateNotifyListener() {
		final PageListener listener = new PageListener() {
			@Override
			public void pageLoaded(String url) {
				synchronized (this) {
					notifyAll();
				}
			}
		};
		addPageListener(listener);
		return new NotifyListener() {
			@Override
			public void release() {
				removePageListener(listener);
			}

			@Override
			public void awaitNotify(long timeout, TimeUnit unit) throws InterruptedException {
				synchronized (listener) {
					unit.timedWait(listener, timeout);
				}
			}

			@Override
			public void awaitNotify() throws InterruptedException {
				synchronized (listener) {
					listener.wait();
				}
			}
		};
	}

	@Override
	public Object evaluateJSON(String js) throws BrowserException {
		Object data = evaluate(js);
		String string = data == null ? "null" : data.toString();
		try {
			return new JSONParser().parse(string);
		} catch (ParseException e) {
			throw new BrowserException(e);
		}
	}

	protected void rethrow(Exception e) throws BrowserException, RuntimeException {
		if (e instanceof RuntimeException) {
			throw (RuntimeException) e;
		} else if (e instanceof ExecutionException) {
			throw new BrowserException(e.getCause());
		} else {
			throw new BrowserException(e);
		}
	}

	protected void rethrowSneaky(Exception e) throws RuntimeException {
		if (e instanceof RuntimeException) {
			throw (RuntimeException) e;
		} else if (e instanceof ExecutionException) {
			throw new RuntimeException(e.getCause());
		} else {
			throw new RuntimeException(e);
		}
	}
}
