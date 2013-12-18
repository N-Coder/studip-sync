package de.ncoder.studipsync.studip.js.impl;


import de.ncoder.studipsync.studip.js.AbstractBrowserAdapter;
import de.ncoder.studipsync.studip.js.BrowserListener;
import sun.org.mozilla.javascript.Context;
import sun.org.mozilla.javascript.ContextFactory;
import sun.org.mozilla.javascript.ScriptableObject;
import sun.org.mozilla.javascript.tools.shell.Global;
import sun.org.mozilla.javascript.tools.shell.Main;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static de.ncoder.studipsync.Values.JS_ENV;
import static de.ncoder.studipsync.Values.JS_LIB;

public class EnvJSBrowserAdapter extends AbstractBrowserAdapter {
    private final Context context;
    private final Global scope;

    public EnvJSBrowserAdapter() throws IOException {
        context = ContextFactory.getGlobal().enterContext();
        context.setOptimizationLevel(-1);
        context.setLanguageVersion(Context.VERSION_1_8);
        scope = Main.getGlobal();
        scope.init(context);

        context.evaluateString(scope, JS_ENV, "env.rhino.js", 0, null);
        ScriptableObject.putProperty(scope, "adapter", Context.javaToJS(this, scope));
        context.evaluateString(scope, JS_LIB, "lib", 0, null);
    }

    private int injectedCount = 0;

    @Override
    public Object evaluate(String js) throws ExecutionException {
        try {
            System.out.println("INJ " + js);
            Object ret = context.evaluateString(scope, js, "injected" + (injectedCount++), 0, null);
            System.out.println("RET " + ret);
            return ret;
        } catch (Exception e) {
            System.err.println(js);
            throw e;
        }
    }

    private int loadCount = 0;

    public void onload() {
        String url = getUrl();
        for (BrowserListener l : listeners) {
            l.pageLoaded(url);
        }
    }
}
