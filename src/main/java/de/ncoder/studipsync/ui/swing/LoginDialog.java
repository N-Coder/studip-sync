package de.ncoder.studipsync.ui.swing;

import de.ncoder.studipsync.data.LoginData;

import javax.swing.*;
import java.awt.event.*;

public class LoginDialog extends JFrame {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField username;
    private JPasswordField password;
    private JLabel labelError;
    private boolean accepted = false;

    public LoginDialog() {
        setContentPane(contentPane);
        //setModal(false);
        getRootPane().setDefaultButton(buttonOK);
        setTitle("StudIP Login");

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    public LoginData requestLoginData() {
        try {
            pack();
            setVisible(true);
            awaitClose();
            if (accepted) {
                return new LoginData(username.getText(), password.getPassword());
            } else {
                return null;
            }
        } finally {
            setVisible(false);
            dispose();
        }
    }

    public void setLoginFailed(boolean failed) {
        if (failed) {
            labelError.setText("Invalid username or password");
        }
        labelError.setVisible(failed);
    }

    private void awaitClose() {
        synchronized (this) {
            while (isVisible()) {
                try {
                    this.wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void onOK() {
        accepted = true;
        setVisible(false);
        synchronized (this) {
            this.notifyAll();
        }
    }

    private void onCancel() {
        accepted = false;
        setVisible(false);
        synchronized (this) {
            this.notifyAll();
        }
    }
}
