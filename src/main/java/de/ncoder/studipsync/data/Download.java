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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.*;

import static de.ncoder.studipsync.studip.StudipAdapter.*;

public class Download implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(Download.class);
    private static final Map<URL, Download> instances = new HashMap<>();

    private final URL url;
    private final Map<String, String> urlParams;
    private final URL diffUrl;

    private long size = -1;
    private Date lastModified;
    private boolean isChanged;

    private int level = -1;
    private Download parent;
    private Seminar seminar;

    private String displayName = "";
    private String displayDescription = "";

    private Download(URL url) {
        Map<String, String> urlParams = URLUtils.extractUrlParameters(url);
        isChanged = Boolean.parseBoolean(urlParams.get(PARAM_NEWEST_ONLY));

        // Newest
        urlParams.put(PARAM_NEWEST_ONLY, Boolean.toString(true));
        diffUrl = URLUtils.setUrlParameters(url, urlParams);

        // General
        urlParams.put(PARAM_NEWEST_ONLY, Boolean.toString(false));
        this.url = URLUtils.setUrlParameters(url, urlParams);

        this.urlParams = Collections.unmodifiableMap(urlParams);
    }

    public static Download getDownload(URL url) {
        if (!instances.containsKey(url)) {
            instances.put(url, new Download(url));
        }
        return instances.get(url);
    }

    public static Download getDownload(String url, String name, String lastModified, String size) throws StudipException {
        try {
            Download download = getDownload(new URL(url));
            download.setDisplayName(name);
            download.setLastModified(parseDate(lastModified));
            download.setSize(parseSize(size));
            return download;
        } catch (MalformedURLException e) {
            StudipException ex = new StudipException("Illegal URL " + url, e);
            ex.put("download.url", url);
            ex.put("download.name", name);
            throw ex;
        }
    }

    // ------------------------------------------------------------------------

    private static long parseSize(String string) {
        if (string == null || string.isEmpty()) {
            return -1;
        }
        return 0; // TODO parse size (dependency "read size" in JsoupStudipAdapter.java)
    }

    private static Date parseDate(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        try {
            return DATE_FORMAT.parse(string);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ------------------------------------------------------------------------

    public String getHash() {
        String hash = urlParams.get(PARAM_FILE_ID);
        if (hash == null) {
            hash = urlParams.get(PARAM_FOLDER_ID);
        }
        if (hash == null) {
            hash = "?" + Objects.hashCode(url);
        }
        return hash;
    }

    public boolean isFolder() {
        return urlParams.containsKey(PARAM_FOLDER_ID);
    }

    public String getFileName() {
        String name = urlParams.get(PARAM_FILE_NAME);
        if (name == null) {
            name = getDisplayName();
        }
        try {
            name = URLDecoder.decode(name, URI_ENCODING);
        } catch (UnsupportedEncodingException e) {
            log.warn("Couldn't decode URL", e);
        }
        for (int i = 0; i < URI_ILLEGAL_CHARS.length; i++) {
            name = name.replace(URI_ILLEGAL_CHARS[i], URI_REPLACE_CHARS[i]);
        }
        return name;
    }

    // --------------------------------

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isChanged() {
        return isChanged;
    }

    public void setIsChanged(boolean isChanged) {
        this.isChanged = isChanged;
    }

    public Download getParent() {
        assert (level <= 0) == (parent == null);
        return parent;
    }

    public void setParent(Download parent) {
        this.parent = parent;
        if (parent == null) {
            level = 0;
        } else {
            level = parent.level + 1;
        }
    }

    public void unsetParent() {
        setLevel(-1);
    }

    public void setLevel(int level) {
        parent = null;
        this.level = level;
    }

    public int getLevel() {
        assert (level <= 0) == (parent == null);
        return level;
    }

    public Seminar getSeminar() {
        return seminar;
    }

    public void setSeminar(Seminar seminar) {
        this.seminar = seminar;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayDescription() {
        return displayDescription;
    }

    public void setDisplayDescription(String displayDescription) {
        this.displayDescription = displayDescription;
    }

    public URL getFullUrl() {
        return url;
    }

    public URL getDiffUrl() {
        return diffUrl;
    }

    public String getPath() {
        if (getLevel() <= 0) {
            return getFileName();
        } else {
            return getParent().getPath() + "/" + getFileName();
        }
    }

    // ------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("'%s'%s (%s, #%s)", getDisplayName(), isChanged() ? "*" : "", getFileName(), getHash().substring(0, 5));
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
        if (!(obj instanceof Download))
            return false;
        Download other = (Download) obj;
        if (getHash() == null) {
            if (other.getHash() != null)
                return false;
        } else if (!getHash().equals(other.getHash()))
            return false;
        return true;
    }
}
