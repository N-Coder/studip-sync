package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.data.StudipFile;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.StudipException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public interface Storage extends Closeable {
	public java.nio.file.Path getRoot();

	public java.nio.file.Path resolve(Seminar seminar);

	public java.nio.file.Path resolve(StudipFile studipFile);

	public void store(StudipAdapter adapter, Seminar seminar) throws IOException, StudipException;

	public void store(StudipAdapter adapter, Seminar seminar, long changesAfter) throws IOException, StudipException;

	public void store(StudipAdapter adapter, StudipFile file) throws IOException, StudipException;

	public void store(StudipAdapter adapter, StudipFile file, long changesAfter) throws IOException, StudipException;

	public boolean registerListener(StorageListener e);

	public boolean unregisterListener(StorageListener o);

	public static interface StorageListener {
		public void onDelete(StudipFile studipFile, Path localFile);

		public void onUpdate(StudipFile studipFile, Path localFile, Path replacement);
	}
}
