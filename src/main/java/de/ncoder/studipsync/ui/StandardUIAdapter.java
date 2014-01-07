package de.ncoder.studipsync.ui;

import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.ui.swing.LoginDialog;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Console;
import java.net.URI;

public enum StandardUIAdapter implements UIAdapter {
    SWING() {
        private final Logger log = LoggerFactory.getLogger(this.toString());

        private int loginTries = -1;
        private LoginDialog dialog;

        @Override
        public void init() {
            if (dialog == null) {
                try {
                    javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    log.warn("Can't set Look and Feel for Swing UI", e);
                }
                dialog = new LoginDialog();
            }
        }

        @Override
        public LoginData requestLoginData() {
            dialog.setLoginFailed(loginTries++ > 0);
            return dialog.requestLoginData();
        }

        public void displayWebpage(URI uri) {
            Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(uri);
                } catch (Exception e) {
                    log.warn("Can't open browser", e);
                }
            } else {
                log.warn("No Browser available");
            }
        }

        @Override
        public void close() {
            if (dialog != null) {
                dialog.dispose();
            }
            super.close();
        }
    },
    CMD() {
        private final Logger log = LoggerFactory.getLogger(this.toString());

        public void init() {
            if (System.console() == null) {
                log.warn("Won't be able to access console for reading LoginData", new NullPointerException("System.console()==null"));
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
            log.info("Page dump written to\n" + uri);
        }
    };

    // --------------------------------

    public static UIAdapter getDefaultUIAdapter() {
        if (System.console() != null) {
            CMD.init();
            return CMD;
        } else {
            SWING.init();
            return SWING;
        }
    }

    public static UIAdapter getUIAdapter(String type) throws ParseException {
        if (type != null) {
            try {
                StandardUIAdapter ui = valueOf(type);
                ui.init();
                return ui;
            } catch (IllegalArgumentException earg) {
                try {
                    return loadUIAdapter(type);
                } catch (ClassNotFoundException eclass) {
                    ParseException pe = new ParseException(type + " is neither an UIType nor can it be resolved to a Java class.");
                    pe.initCause(eclass);
                    pe.addSuppressed(earg);
                    throw pe;
                }
            }
        } else {
            return getDefaultUIAdapter();
        }
    }

    public static UIAdapter loadUIAdapter(String classname) throws ParseException, ClassNotFoundException {
        try {
            Class<?> clazz = Class.forName(classname);
            Object instance = clazz.newInstance();
            if (!(instance instanceof UIAdapter)) {
                throw new ParseException(instance + " is not an UI adapter");
            }
            return (UIAdapter) instance;
        } catch (InstantiationException | IllegalAccessException e) {
            ParseException pe = new ParseException("Could not instantiate class " + classname + ". " + e.getMessage());
            pe.initCause(e);
            throw pe;
        }
    }

    public void init() {
    }

    @Override
    public void close() {
    }
}
