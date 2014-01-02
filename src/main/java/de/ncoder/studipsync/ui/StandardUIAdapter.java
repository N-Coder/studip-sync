package de.ncoder.studipsync.ui;

import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.ui.swing.LoginDialog;

import java.awt.*;
import java.io.Console;
import java.net.URI;

import static de.ncoder.studipsync.Values.LOG_MAIN;

public enum StandardUIAdapter implements UIAdapter {
    SWING() {
        private int loginTries = -1;

        @Override
        public void init() {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                LOG_MAIN.warn("Can't set Look and Feel for Swing UI", e);
            }
        }

        @Override
        public LoginData requestLoginData() {
            loginTries++;
            return new LoginDialog(loginTries > 0).requestLoginData();
        }

        public void displayWebpage(URI uri) {
            Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(uri);
                } catch (Exception e) {
                    LOG_MAIN.warn("Can't open browser", e);
                }
            } else {
                LOG_MAIN.warn("No Browser available");
            }
        }
    },
    CMD() {
        public void init() {
            if (System.console() == null) {
                LOG_MAIN.warn("Won't be able to access console for reading LoginData", new NullPointerException("System.console()==null"));
            }
        }

        @Override
        public LoginData requestLoginData() {
            Console console = System.console();
            console.printf("Username: ");
            String username = console.readLine();
            char password[] = console.readPassword("Password: ");
            return new LoginData(username, password);
        }

        @Override
        public void displayWebpage(URI uri) {
            LOG_MAIN.info("Page dump written to\n" + uri);
        }
    };

    public void init() {
    }

    @Override
    public void close() {
    }
}
