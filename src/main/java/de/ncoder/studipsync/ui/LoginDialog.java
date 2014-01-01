package de.ncoder.studipsync.ui;

import de.ncoder.studipsync.data.LoginData;

import javax.swing.*;
import java.awt.event.*;

public class LoginDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField username;
    private JPasswordField password;
    private JLabel labelError;
    private boolean accepted = false;

    public LoginDialog() {
        this(false);
    }

    public LoginDialog(boolean loginFailed) {
        setContentPane(contentPane);
        setModal(true);
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
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
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

        if (loginFailed) {
            labelError.setText("Invalid username or password");
            labelError.setVisible(true);
        }
    }

    public LoginData requestLoginData() {
        try {
            pack();
            setVisible(true);
            if (accepted) {
                return new LoginData(username.getText(), password.getPassword());
            } else {
                return null;
            }
        } finally {
            dispose();
        }
    }

    private void onOK() {
        accepted = true;
        setVisible(false);
    }

    private void onCancel() {
        accepted = false;
        setVisible(false);
    }
}
