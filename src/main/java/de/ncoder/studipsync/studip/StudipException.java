package de.ncoder.studipsync.studip;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class StudipException extends ExecutionException {
    private Map<String, Object> additionals = new HashMap<>();

    public StudipException() {
    }

    public StudipException(String message) {
        super(message);
    }

    public StudipException(String message, Throwable cause) {
        super(message, cause);
    }

    public StudipException(Throwable cause) {
        super(cause);
    }

    public Object get(String key) {
        return additionals.get(key);
    }

    public Object put(String key, Object value) {
        return additionals.put(key, value);
    }

    public Object remove(String key) {
        return additionals.remove(key);
    }

    public void clear() {
        additionals.clear();
    }

    public Map<String, Object> getAdditionals() {
        return additionals;
    }

    @Override
    public String getLocalizedMessage() {
        StringBuilder bob = new StringBuilder(super.getLocalizedMessage());
        for (Map.Entry additional : additionals.entrySet()) {
            bob.append("\n");
            bob.append(additional.getKey().toString());
            bob.append(": ");
            bob.append(additional.getValue().toString());
        }
        return bob.toString();
    }
}
