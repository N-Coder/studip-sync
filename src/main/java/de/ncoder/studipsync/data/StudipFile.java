package de.ncoder.studipsync.data;

import de.ncoder.studipsync.studip.StudipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public abstract class StudipFile implements Serializable {
	private static final Logger log = LoggerFactory.getLogger(StudipFile.class);

	private final String hash;
	private final Seminar seminar;
	private final boolean isFolder;

	private boolean infoLoaded = false;

	protected String name;
	protected String fileName;
	protected String description;
	protected String author;

	protected long size;
	protected Date lastModified;
	protected boolean isChanged;

	protected StudipFile parent;

	public StudipFile(String hash, Seminar seminar, boolean isFolder) {
		this.hash = hash;
		this.seminar = seminar;
		this.isFolder = isFolder;
	}

	// ------------------------------------------------------------------------

	protected abstract void loadInfo() throws StudipException;

	public abstract String getDownloadURL();

	public abstract String getDownloadURL(long changesAfterTimestamp);

	protected void provideInfo() throws StudipException {
		if (!infoLoaded) {
			loadInfo();
			infoLoaded = true;
		}
	}

	public String getPath() {
		try {
			if (getParent() == null) {
				return getFileName();
			} else {
				return getParent().getPath() + "/" + getFileName();
			}
		} catch (StudipException e) {
			log.error("Can't load path", e);
			return getName();
		}
	}

	public String getFileExtension() {
		if (isFolder()) {
			return "zip";
		} else {
			String name = null;
			try {
				name = getFileName();
			} catch (StudipException e) {
				name = getName();
			}
			int i = name.lastIndexOf('.') + 1;
			if (i >= 0 && i <= name.length()) {
				return name.substring(i);
			}
		}
		return "";
	}

	public List<StudipFile> getChildren() throws StudipException {
		List<StudipFile> children = new LinkedList<>();
		for (StudipFile child : getSeminar().getFiles()) {
			if (child.isChildOf(this)) {
				children.add(child);
			}
		}
		return children;
	}

	private boolean isChildOf(StudipFile parent) throws StudipException {
		return getParent() != null && (getParent() == parent || getParent().isChildOf(parent));
	}

	// ------------------------------------------------------------------------

	public String getHash() {
		return hash;
	}

	public Seminar getSeminar() {
		return seminar;
	}

	public boolean isFolder() {
		return isFolder;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

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

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public boolean isChanged() {
		return isChanged;
	}

	public void setChanged(boolean isChanged) {
		this.isChanged = isChanged;
	}

	public StudipFile getParent() throws StudipException {
		provideInfo();
		return parent;
	}

	public String getFileName() throws StudipException {
		provideInfo();
		return fileName;
	}

	public String getDescription() throws StudipException {
		provideInfo();
		return description;
	}

	// ------------------------------------------------------------------------

	@Override
	public String toString() {
		return (getSeminar() != null ? getSeminar().getID() : "") + "/" + getPath();
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
