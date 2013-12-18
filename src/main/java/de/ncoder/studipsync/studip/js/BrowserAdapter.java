package de.ncoder.studipsync.studip.js;

import de.ncoder.studipsync.data.NotifyListener;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface BrowserAdapter {
    public abstract String getText();

    public abstract String getUrl();

    public abstract void execute(String js) throws ExecutionException;

    public abstract Object evaluate(String js) throws ExecutionException;

    public abstract Object evaluateJSON(String js) throws ExecutionException;

    public abstract void navigate(String url) throws ExecutionException;

    public abstract void navigate(String url, long waitMs) throws ExecutionException, TimeoutException;

    public abstract InputStream download(String url) throws ExecutionException;

    public abstract boolean removeBrowserListener(Object o);

    public abstract boolean addBrowserListener(BrowserListener e);

    public abstract NotifyListener allocateNotifyListener();

    public abstract void setCookies(String cookies) throws ExecutionException;

    public abstract Object getCookies() throws ExecutionException;
}
