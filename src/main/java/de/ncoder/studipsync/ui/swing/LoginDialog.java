/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Niko Fink
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
