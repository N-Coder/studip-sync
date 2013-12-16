package de.ncoder.studipsync.adapter;

import java.io.InputStream;
import java.util.concurrent.TimeoutException;

public interface BrowserAdapter {
	public abstract String getText();

	public abstract String getUrl();

	public abstract void execute(String js) throws BrowserException;

	public abstract Object evaluate(String js) throws BrowserException;

	public abstract Object evaluateJSON(String js) throws BrowserException;

	public abstract void navigate(String url) throws BrowserException;

	public abstract void navigate(String url, long waitMs) throws BrowserException, TimeoutException;

	public abstract InputStream download(String url) throws BrowserException;

	public abstract boolean removePageListener(Object o);

	public abstract boolean addPageListener(PageListener e);

	public abstract NotifyListener allocateNotifyListener();
}