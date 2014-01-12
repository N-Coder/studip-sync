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

package de.ncoder.studipsync.studip;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class StudipException extends ExecutionException {
    private final Map<String, Object> additionals = new HashMap<>();

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
        String message = super.getLocalizedMessage();
        StringBuilder bob = message != null ? new StringBuilder(message) : new StringBuilder();
        for (Map.Entry additional : additionals.entrySet()) {
            bob.append("\n\t");
            bob.append(String.valueOf(additional.getKey()));
            bob.append(": ");
            bob.append(String.valueOf(additional.getValue()));
        }
        return bob.toString();
    }
}
