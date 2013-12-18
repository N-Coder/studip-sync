package de.ncoder.studipsync.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class LoginDialog extends Dialog {
    private Shell shlStudipLogin;
    private Text txtUsername;
    private Text txtPassword;

    private boolean result;
    private String username;
    private char[] password;

    public LoginDialog(Shell parent, int style) {
        super(parent, style);
        setText("StudIP Login");
    }

    public boolean open() {
        createContents();
        shlStudipLogin.open();
        shlStudipLogin.layout();
        Display display = getParent().getDisplay();
        while (!shlStudipLogin.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return result;
    }

    private void createContents() {
        shlStudipLogin = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shlStudipLogin.setSize(200, 140);
        shlStudipLogin.setText(getText());
        shlStudipLogin.setLayout(new GridLayout(2, false));

        Label lblUsername = new Label(shlStudipLogin, SWT.NONE);
        lblUsername.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblUsername.setText("Username");

        txtUsername = new Text(shlStudipLogin, SWT.BORDER);
        txtUsername.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label lblPassword = new Label(shlStudipLogin, SWT.NONE);
        lblPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblPassword.setText("Password");

        txtPassword = new Text(shlStudipLogin, SWT.BORDER | SWT.PASSWORD);
        txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label divider1 = new Label(shlStudipLogin, SWT.SEPARATOR | SWT.HORIZONTAL);
        divider1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

        Composite boxButtons = new Composite(shlStudipLogin, SWT.NONE);
        boxButtons.setLayout(new FillLayout(SWT.HORIZONTAL));
        boxButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));

        Button btnCancel = new Button(boxButtons, SWT.NONE);
        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                result = false;
                username = null;
                password = null;
                shlStudipLogin.dispose();
            }
        });
        btnCancel.setText("Cancel");

        Button btnLogin = new Button(boxButtons, SWT.NONE);
        btnLogin.setText("Login");
        btnLogin.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                result = true;
                username = txtUsername.getText();
                password = txtPassword.getTextChars();
                shlStudipLogin.dispose();
            }
        });
        shlStudipLogin.setDefaultButton(btnLogin);
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }
}
