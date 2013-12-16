package de.ncoder.studipsync.data;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.json.simple.JSONObject;

import de.ncoder.studipsync.Loggers;
import de.ncoder.studipsync.Values;

// TODO externalize parsing relevant Strings
public class Download implements Serializable {
	private static final Map<URL, Download> instances = new HashMap<URL, Download>();

	private final URL url;
	private final Map<String, String> urlParams;
	private final URL urlNewest;

	private long size = -1;
	private Date lastModified;
	private boolean hasNewest;

	private int level = -1;
	private Download parent;
	// private boolean parentKnown = true;
	private Seminar seminar;

	private String displayName = "";
	private String displayDescription = "";

	public Download(URL url) {
		try {
			Map<String, String> urlParams = URLUtils.extractUrlParameters(url);
			hasNewest = "true".equals(urlParams.get("newestOnly"));

			// Newest
			urlParams.put("newestOnly", "true");
			urlNewest = URLUtils.setUrlParameters(url, urlParams);

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

	public static Download getDownload(JSONObject json) {
		try {
			return getDownload(new URL((String) json.get("url")));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	// ------------------------------------------------------------------------

	public void update(JSONObject json) {
		long size = parseSize((String) json.get("size"));
		if (size >= 0) {
			setSize(size);
		}
		Date lastModified = parseDate((String) json.get("lastModified"));
		if (lastModified != null) {
			setLastModified(lastModified);
		}
		String displayName = (String) json.get("displayName");
		if (displayName != null) {
			setDisplayName(displayName);
		}
		String displayDescription = (String) json.get("displayDescription");
		if (displayDescription != null) {
			setDisplayDescription(displayDescription);
		}
		int level = ((Number) json.get("level")).intValue();
		if (level >= 0) {
			setLevel(level);
		}
	}

	private static long parseSize(String string) {
		if (string == null || string.isEmpty()) {
			return -1;
		}
		return 0; // TODO parse size (dependency "read size" in parseDownloads.js)
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

	private static final String[] urlOldChars = new String[] { " ", ":", "ä", "ö", "ü", "Ä", "Ö", "Ü", "ß" };
	private static final String[] urlNewChars = new String[] { "_", "", "ae", "oe", "ue", "Ae", "Oe", "Ue", "ss" };

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

	// public List<Download> getChildren() {
	// if (getLevel() < 0) {
	// return Collections.emptyList();
	// }
	// List<Download> children = new LinkedList<Download>();
	// for (Download child : seminar.getDownloads()) {
	// if (equals(child.getParent())) {
	// children.add(child);
	// }
	// }
	// return children;
	// }

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

	public boolean hasNewest() {
		return hasNewest;
	}

	public void setHasNewest(boolean hasNewest) {
		this.hasNewest = hasNewest;
	}

	public Download getParent() {
		assert (level <= 0) == (parent == null);
		return parent;
	}

	public void setParent(Download parent) {
		if (parent == null) {
			setLevel(0);
		} else {
			this.parent = parent;
			level = parent.level + 1;
		}
		// parentKnown = true;
	}

	public void unsetParent() {
		setLevel(-1);
	}

	public void setLevel(int level) {
		parent = null;
		this.level = level;
	}

	// public boolean isParentUnknown() {
	// return !parentKnown;
	// }

	public int getLevel() {
		assert (level <= 0) == (parent == null);
		return level;
		// if (isParentUnknown()) {
		// return -1;
		// } else if (getParent() == null) {
		// return 0;
		// } else if (getParent().getLevel() < 0) {
		// return getParent().getLevel() - 1;
		// } else {
		// return getParent().getLevel() + 1;
		// }
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

	public URL getUrl() {
		return url;
	}

	public URL getUrlNewest() {
		return urlNewest;
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
		return String.format("'%s'%s (%s, #%s)", getDisplayName(), hasNewest() ? "*" : "", getFileName(), getHash().substring(0, 5));
	}

	// public void print(StringBuilder bob, String nl) {
	// List<Download> children = getChildren();
	// if (getLevel() < 0) {
	// bob.append(nl + "├╶");
	// } else if (children.isEmpty()) {
	// bob.append(nl + "├");
	// } else {
	// bob.append(nl + "├┐");
	// }
	// bob.append(toString());
	// String cnl = nl + "│";
	// for (Download child : children) {
	// child.print(bob, cnl);
	// }
	// }

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
