package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.StudipFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StorageLog implements Storage.StorageListener {
	private Map<StoredFile, StoreAction> actions = new HashMap<>();

	public void clear() {
		actions.clear();
	}

	@Override
	public void onDelete(StudipFile studipFile, Path localFile) {
		actions.put(new StoredFile(studipFile, localFile), StoreAction.DELETE);
	}

	@Override
	public void onUpdate(StudipFile studipFile, Path localFile, Path replacement) {
		if (Files.exists(localFile)) {
			actions.put(new StoredFile(studipFile, localFile), StoreAction.UPDATE);
		} else {
			actions.put(new StoredFile(studipFile, localFile), StoreAction.CREATE);
		}
	}

	public Map<StoredFile, StoreAction> getActions() {
		return actions;
	}

	public String getStatusMessage() {
		List<Map.Entry<StoredFile, StoreAction>> entries = new ArrayList<>(actions.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<StoredFile, StoreAction>>() {
			@Override
			public int compare(Map.Entry<StoredFile, StoreAction> o1, Map.Entry<StoredFile, StoreAction> o2) {
				return o1.getKey().compareTo(o2.getKey());
			}
		});
		StringBuilder bob = new StringBuilder(entries.size() + " file" + (entries.size() != 1 ? "s" : "") + " modified" + (entries.size() > 0 ? ":" : ""));
		for (Map.Entry<StoredFile, StoreAction> entry : entries) {
			bob.append("\n\t");
			switch (entry.getValue()) {
				case CREATE:
					bob.append("NEW");
					break;
				case UPDATE:
					bob.append("MOD");
					break;
				case DELETE:
					bob.append("DEL");
					break;
			}
			bob.append(": ");
			bob.append(entry.getKey().getFile());
		}
		return bob.toString();
	}

	public static enum StoreAction {
		DELETE, UPDATE, CREATE
	}

	public static class StoredFile implements Comparable<StoredFile> {
		public final StudipFile studipFile;
		public final Path file;

		public StoredFile(StudipFile studipFile, Path file) {
			this.studipFile = studipFile;
			this.file = file;
		}

		public StudipFile getStudipFile() {
			return studipFile;
		}

		public Path getFile() {
			return file;
		}

		@Override
		public int compareTo(StoredFile o) {
			return getFile().compareTo(o.getFile());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			StoredFile that = (StoredFile) o;
			if (studipFile != null ? !studipFile.equals(that.studipFile) : that.studipFile != null) return false;
			if (file != null ? !file.equals(that.file) : that.file != null) return false;
			return true;
		}

		@Override
		public int hashCode() {
			int result = studipFile != null ? studipFile.hashCode() : 0;
			result = 31 * result + (file != null ? file.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return String.valueOf(file);
		}
	}
}
