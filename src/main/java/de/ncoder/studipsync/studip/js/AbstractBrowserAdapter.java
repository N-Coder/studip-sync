package de.ncoder.studipsync.studip.js;

import de.ncoder.studipsync.data.NotifyListener;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractBrowserAdapter implements BrowserAdapter {
    protected List<BrowserListener> listeners = new LinkedList<BrowserListener>();

    public AbstractBrowserAdapter() {
        super();
    }

    @Override
    public boolean addBrowserListener(BrowserListener e) {
        return listeners.add(e);
    }

    @Override
    public boolean removeBrowserListener(Object o) {
        return listeners.remove(o);
    }

    @Override
    public String getText() {
        try {
            return String.valueOf(
                    evaluate("(document.documentElement!=null ? document.documentElement.innerHTML : null)+'';")
            );
        } catch (ExecutionException e) {
            rethrowSneaky(e);
            throw new IllegalStateException("Couldn't rethrow " + e);
        }
    }

    @Override
    public String getUrl() {
        try {
            return String.valueOf(
                    evaluate("document.location.href;")
            );
        } catch (ExecutionException e) {
            rethrowSneaky(e);
            throw new IllegalStateException("Couldn't rethrow " + e);
        }
    }

    @Override
    public void navigate(String url, long waitMs) throws ExecutionException, TimeoutException {
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
            throw new ExecutionException(e);
        } finally {
            notifyListener.release();
        }
    }

    @Override
    public void navigate(String urlString) throws ExecutionException {
        try {
            URL url = new URL("http://" + urlString);
            evaluate("window.location = \"" + url + "\";");
        } catch (MalformedURLException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public NotifyListener allocateNotifyListener() {
        final BrowserListener listener = new BrowserListener() {
            @Override
            public void pageLoaded(String url) {
                synchronized (this) {
                    notifyAll();
                }
            }
        };
        addBrowserListener(listener);
        return new NotifyListener() {
            @Override
            public void release() {
                removeBrowserListener(listener);
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
    public void execute(String js) throws ExecutionException {
        evaluate(js);
    }

    @Override
    public Object evaluateJSON(String js) throws ExecutionException {
        Object data = evaluate(js);
        String string = data == null ? "null" : data.toString();
        try {
            return new JSONParser().parse(string);
        } catch (ParseException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public InputStream download(String urlString) throws ExecutionException {
        try {
            URL url = new URL(urlString);
            URLConnection urlCon = url.openConnection();
            if (!(urlCon instanceof HttpURLConnection)) {
                throw new ExecutionException(new IllegalStateException("Can only download via http"));
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

    @Override
    public void setCookies(String cookies) throws ExecutionException {
        execute("setCookies(" + cookies + ");");
    }

    @Override
    public Object getCookies() throws ExecutionException {
        return evaluateJSON("JSON.stringify(getCookies());");
    }

    protected void rethrow(Exception e) throws ExecutionException, RuntimeException {
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else if (e instanceof ExecutionException) {
            throw (ExecutionException) e;
        } else {
            throw new ExecutionException(e);
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
