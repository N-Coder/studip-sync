package de.ncoder.studipsync.swt;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.ExecutionException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.google.common.util.concurrent.SettableFuture;

import de.ncoder.studipsync.studip.UIAdapter;
import de.ncoder.studipsync.data.LoginData;

public class BrowserShell extends Shell implements UIAdapter {
    private static final NumberFormat DECIMAL_FORMAT = DecimalFormat.getPercentInstance();

    private Browser browser;
    private Label lblType;
    private Label lblUrl;
    private Label lblProgress;

    public BrowserShell(Display display) {
        super(display, SWT.SHELL_TRIM);
        createContents();
    }

    protected void createContents() {
        setText("StudIP Sync DebugBrowser");
        setSize(450, 300);

        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        setLayout(gridLayout);

        browser = new Browser(this, SWT.WEBKIT);
        browser.addProgressListener(new ProgressAdapter() {
            @Override
            public void changed(ProgressEvent event) {
                lblProgress.setForeground(null);
                lblProgress.setText(DECIMAL_FORMAT.format(event.current / (double) event.total));
            }

            @Override
            public void completed(ProgressEvent event) {
                lblProgress.setForeground(getDisplay().getSystemColor(SWT.COLOR_GREEN));
                lblProgress.setText(DECIMAL_FORMAT.format(event.current / (double) event.total));
            }
        });
        browser.addLocationListener(new LocationAdapter() {
            @Override
            public void changed(LocationEvent event) {
                lblUrl.setText(browser.getUrl());
            }
        });
        browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        lblType = new Label(this, SWT.NONE);
        GridData gd_lblType = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_lblType.widthHint = 50;
        lblType.setLayoutData(gd_lblType);
        lblType.setText(browser.getBrowserType());

        lblUrl = new Label(this, SWT.NONE);
        lblUrl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        lblUrl.setText("about:blank");

        lblProgress = new Label(this, SWT.NONE);
        GridData gd_lblProgress = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_lblProgress.widthHint = 50;
        lblProgress.setLayoutData(gd_lblProgress);
        lblProgress.setText("Progress");
    }

    public Browser getBrowser() {
        return browser;
    }

    @Override
    public void close() {
        getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                BrowserShell.super.close();
            }
        });
    }

    /**
     * Disable the check that prevents subclassing of SWT components
     *
     * @see org.eclipse.swt.widgets.Decorations#checkSubclass()
     */
    @Override
    protected void checkSubclass() {
    }

    @Override
    public LoginData requestLoginData() {
        final SettableFuture<LoginData> value = SettableFuture.create();
        getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                LoginDialog login = new LoginDialog(BrowserShell.this, SWT.NONE);
                if (login.open()) {
                    value.set(new LoginData(login.getUsername(), login.getPassword()));
                } else {
                    value.set(null);
                }
            }
        });
        try {
            return value.get();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Could not get value from UI Thread");
            e.printStackTrace();
            return null;
        }
    }
}
