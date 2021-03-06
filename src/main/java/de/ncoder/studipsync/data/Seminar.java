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

package de.ncoder.studipsync.data;

import de.ncoder.studipsync.studip.StudipException;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static de.ncoder.studipsync.studip.StudipAdapter.PARAM_SEMINAR_SELECTION;

public class Seminar implements Serializable {
    private static final Map<URL, Seminar> instances = new HashMap<>();

    private final URL url;
    private final Map<String, String> urlParams;

    private String name;
    private String description;

    private Seminar(URL url) {
        urlParams = URLUtils.extractUrlParameters(url);
        this.url = url;
    }

    public static Seminar getSeminar(URL url) {
        if (!instances.containsKey(url)) {
            instances.put(url, new Seminar(url));
        }
        return instances.get(url);
    }

    public static Seminar getSeminar(String url, String name, String description) throws StudipException {
        try {
            Seminar seminar = getSeminar(new URL(url));
            seminar.setFullName(name);
            seminar.setDescription(description);
            return seminar;
        } catch (MalformedURLException e) {
            StudipException ex = new StudipException("Illegal URL " + url, e);
            ex.put("seminar.url", url);
            ex.put("seminar.name", name);
            throw ex;
        }
    }

    // ------------------------------------------------------------------------

    public String getHash() {
        String hash = urlParams.get(PARAM_SEMINAR_SELECTION);
        return hash == null ? "?" + Objects.hashCode(name) + "?" : hash;
    }

    public String getID() {
        return name.substring(0, name.indexOf(" "));
    }

    public String getType() {
        int start = name.indexOf(" ") + 1;
        int end = name.indexOf(": ", start);
        if (start < 0 || end < 0) {
            return getID();
        }
        return name.substring(start, end);
    }

    public String getName() {
        return name.substring(name.indexOf(": ") + 2);
    }

    public String getPeriod() {
        return description.substring(0, description.indexOf(","));
    }

    // --------------------------------

    public URL getUrl() {
        return url;
    }

    public String getFullName() {
        return name;
    }

    public void setFullName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // ------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("'%s' (%s, #%s)", getFullName(), getDescription(), getHash().substring(0, 5));
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Seminar))
            return false;
        Seminar other = (Seminar) obj;
        if (getHash() == null) {
            if (other.getHash() != null)
                return false;
        } else if (!getHash().equals(other.getHash()))
            return false;
        return true;
    }
}
