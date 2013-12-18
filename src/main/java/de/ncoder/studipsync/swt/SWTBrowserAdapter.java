package de.ncoder.studipsync.swt;

import com.google.common.util.concurrent.SettableFuture;
import de.ncoder.studipsync.studip.browser.AbstractBrowserAdapter;
import de.ncoder.studipsync.studip.browser.BrowserAdapter;
import de.ncoder.studipsync.studip.browser.BrowserListener;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutionException;

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
            }

            @Override
            public void completed(ProgressEvent event) {
                url = browser.getUrl();
                for (BrowserListener l : listeners) {
                    l.pageLoaded(url);
                }
            }
        });
    }

    @Override
    public String getUrl() {
        return url;
        // final SettableFuture<String> value = SettableFuture.create();
        // browser.getDisplay().syncExec(new Runnable() {
        // @Override
        // public void run() {
        // try {
        // value.set(browser.getFullUrl());
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

//    @Override
//    public void execute(final String js) throws ExecutionException {
//        final SettableFuture<Boolean> value = SettableFuture.create();
//        browser.getDisplay().syncExec(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    value.set(browser.execute(js));
//                } catch (Exception e) {
//                    value.setException(e);
//                }
//            }
//        });
//        try {
//            if (!value.get()) {
//                // FIXME execute returns false
//                // throw new ExecutionException(new IllegalStateException("Script has not been executed"));
//            }
//        } catch (ExecutionException | InterruptedException e) {
//            rethrow(e);
//            throw new IllegalStateException("Couldn't rethrow " + e);
//        }
//    }

    @Override
    public Object evaluate(final String js) throws ExecutionException {
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
    public void navigate(final String url) throws ExecutionException {
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
                throw new ExecutionException(new IllegalStateException("Navigating to " + url + " has failed"));
            }
        } catch (ExecutionException | InterruptedException e) {
            rethrow(e);
            throw new IllegalStateException("Couldn't rethrow " + e);
        }
    }
}
