package de.ncoder.studipsync.data;

import de.ncoder.studipsync.Loggers;
import de.ncoder.studipsync.Values;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.*;

// TODO externalize parsing relevant Strings
// TODO make URL non final, but immodifyable
// TODO improve parent hierachy
public class Download implements Serializable {
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
        try {
            Map<String, String> urlParams = URLUtils.extractUrlParameters(url);
            isChanged = "true".equals(urlParams.get("newestOnly"));

            // Newest
            urlParams.put("newestOnly", "true");
            diffUrl = URLUtils.setUrlParameters(url, urlParams);

            // General
            urlParams.put("newestOnly", "false");
            this.url = URLUtils.setUrlParameters(url, urlParams);

            this.urlParams = Collections.unmodifiableMap(urlParams);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Download getDownload(URL url) {
        if (!instances.containsKey(url)) {
            instances.put(url, new Download(url));
        }
        return instances.get(url);
    }

    public static Download getDownload(String url, String name, String lastModified, String size) throws MalformedURLException {
        Download download = getDownload(new URL(url));
        download.setDisplayName(name);
        download.setLastModified(parseDate(lastModified));
        download.setSize(parseSize(size));
        return download;
    }

    // ------------------------------------------------------------------------

    private static long parseSize(String string) {
        if (string == null || string.isEmpty()) {
            return -1;
        }
        return 0; // TODO parse size (dependency "read size" in parseDownloads.browser)
    }

    private static Date parseDate(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        try {
            return Values.DATE_FORMAT.parse(string);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ------------------------------------------------------------------------

    public String getHash() {
        String hash = urlParams.get("file_id");
        if (hash == null) {
            hash = urlParams.get("folder_id");
        }
        if (hash == null) {
            hash = "?" + Objects.hashCode(url);
        }
        return hash;
    }

    public boolean isFolder() {
        return urlParams.containsKey("folder_id");
    }

    private static final String[] urlOldChars = new String[]{" ", ":", "ä", "ö", "ü", "Ä", "Ö", "Ü", "ß"};
    private static final String[] urlNewChars = new String[]{"_", "", "ae", "oe", "ue", "Ae", "Oe", "Ue", "ss"};

    public String getFileName() {
        String name = urlParams.get("file_name");
        if (name == null) {
            name = getDisplayName();
            // if (isFolder()) {
            // name += ".zip";
            // }
        }
        try {
            name = URLDecoder.decode(name, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            Loggers.LOG_DOWNLOAD.warn("Could't decode URL", e);
        }
        for (int i = 0; i < urlOldChars.length; i++) {
            name = name.replace(urlOldChars[i], urlNewChars[i]);
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
