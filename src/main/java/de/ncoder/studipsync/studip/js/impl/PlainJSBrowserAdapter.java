package de.ncoder.studipsync.studip.js.impl;

import de.ncoder.studipsync.studip.js.AbstractBrowserAdapter;
import de.ncoder.studipsync.studip.js.BrowserAdapter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;
import org.w3c.dom.Document;

import java.util.concurrent.ExecutionException;

public class PlainJSBrowserAdapter extends AbstractBrowserAdapter {
    private Scriptable globalScope;
    private Context context;
    private WrapFactory savedFactory;

    static {
//        try {
//            Context context = closer.register(new ContextCloseable()).getContext();
//            URL sizzlejs = Frizzle.class.getResource("sizzle.js");
//            Reader in = closer.register(new InputStreamReader(
//                    sizzlejs.openStream(), Charsets.UTF_8));
//            return context.compileReader(in, sizzlejs.toString(), 1, null);
//        } catch (IOException e) {
//            throw new AssertionError(e);
//        }
    }

    public PlainJSBrowserAdapter(Document doc) {
        enterContext();
        try {
            this.globalScope = context.initStandardObjects();
            Scriptable window = context.newObject(globalScope);
            window.put("document", window, toJS(doc));
            globalScope.put("window", globalScope, window);
            //SIZZLE_SCRIPT.exec(context, globalScope);
        } finally {
            exitContext();
        }
    }

    private void enterContext() {
        context = Context.enter();
        savedFactory = context.getWrapFactory();
        //context.setWrapFactory(new DOMWrapFactory());
    }

    private void exitContext() {
        context.setWrapFactory(savedFactory);
        Context.exit();
    }

    private Object toJS(Object javaObject) {
        return Context.javaToJS(javaObject, globalScope);
    }

    @Override
    public Object evaluate(String js) throws ExecutionException {
        return null;
    }
}
