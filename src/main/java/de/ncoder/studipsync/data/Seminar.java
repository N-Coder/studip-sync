package de.ncoder.studipsync.data;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.json.simple.JSONObject;

public class Seminar implements Serializable {
	private static final Map<URL, Seminar> instances = new HashMap<URL, Seminar>();

	private final URL url;
	private final Map<String, String> urlParams;

	// private final List<Download> downloads = new ArrayList<>();

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

	public static Seminar getSeminar(JSONObject json) {
		try {
			return getSeminar(new URL((String) json.get("url")));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	// ------------------------------------------------------------------------

	// public void addDownloads(List<Download> newDownloads) {
	// for (Download newDownload : newDownloads) {
	// addDownload(newDownload);
	// }
	// }

	// public void addDownload(Download download) {
	// assert download.getSeminar() == null || download.getSeminar() == this;
	// download.setSeminar(this);
	// if (!downloads.contains(download)) {
	// downloads.add(download);
	// }
	// }

	public void update(JSONObject json) throws MalformedURLException {
		String name = (String) json.get("name");
		if (name != null) {
			setFullName(name);
		}
		String description = (String) json.get("description");
		if (description != null) {
			setDescription(description);
		}
	}

	public String getHash() {
		String hash = urlParams.get("auswahl");
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

	// public List<Download> getDownloads() {
	// return downloads;
	// }

	// ------------------------------------------------------------------------

	@Override
	public String toString() {
		return String.format("'%s' (%s, #%s)", getFullName(), getDescription(), getHash().substring(0, 5));
	}

	// public void print(StringBuilder bob, String nl) {
	// if (getID().equals("5172V")) {
	// System.out.println("x");
	// }
	//
	// if (downloads.isEmpty()) {
	// bob.append(nl + "╠═");
	// } else {
	// bob.append(nl + "╠╤");
	// }
	// bob.append(getFullName());
	// bob.append(": ");
	// bob.append(getDescription());
	// bob.append(" (");
	// bob.append(getHash().substring(0, 5));
	// bob.append(")");
	//
	// String dnl = nl + "║";
	// StringBuilder bobBuffer = new StringBuilder();
	// for (Download download : downloads) {
	// if (download.getLevel() == 0) {
	// download.print(bob, dnl);
	// } else if (download.getLevel() == -1) {
	// download.print(bobBuffer, dnl);
	// }
	// }
	// bob.append(bobBuffer);
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
