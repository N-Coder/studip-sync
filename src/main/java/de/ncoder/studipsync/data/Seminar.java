package de.ncoder.studipsync.data;

import de.ncoder.studipsync.studip.StudipException;

import java.io.Serializable;
import java.util.*;

public abstract class Seminar implements Serializable {
	private final String hash;
	private final String id;
	private final String name;
	private final String period;

	private boolean filesLoaded = false;
	protected Date lastSync = null;
	private final Map<String, StudipFile> files = new HashMap<>();
	private final Collection<StudipFile> filesExt = Collections.unmodifiableCollection(files.values());

	public Seminar(String hash, String id, String name, String period) {
		this.hash = hash;
		this.id = id;
		this.name = name;
		this.period = period;
	}

	public abstract String getDownloadURL();

	public abstract String getDownloadURL(long changesAfterTimestamp);

	protected abstract Iterable<StudipFile> loadFiles() throws StudipException;

	public Collection<StudipFile> getFiles() throws StudipException {
		if (!filesLoaded) {
			for (StudipFile studipFile : loadFiles()) {
				files.put(studipFile.getHash(), studipFile);
			}
			filesLoaded = true;
		}
		return filesExt;
	}

	public boolean hasChangedFiles() throws StudipException {
		for (StudipFile studipFile : getFiles()) {
			if (studipFile.isChanged()) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------

	public String getHash() {
		return hash;
	}

	public String getID() {
		return (id == null || id.isEmpty()) ? "#" + getHash().substring(0, 5) : id;
	}

	public String getFullName() {
		return name;
	}

	public String getNr() {
		return getID().replaceAll("[A-Za-z]*", "");
	}

	public String getType() {
		return getID().replaceAll("[0-9]*", "");
	}

	public String getName() {
		return getFullName().replaceAll("\\(.*\\)", "");
	}

	public String getPeriod() {
		return period;
	}

	public Date getLastSyncTime() {
		return lastSync;
	}

	// ------------------------------------------------------------------------

	@Override
	public String toString() {
		return getID() + " " + getName() + " #" + getHash().substring(0, 5);
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
