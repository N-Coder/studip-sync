package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface Storage {
    public Path getRoot();

    public Path resolve(Download download);

    public Path resolve(Seminar seminar);

    public void close() throws IOException;

    public void store(Download download, InputStream dataSrc, boolean isDiff) throws IOException;

    public void store(Download download, Path dataSrc, boolean isDiff) throws IOException;

    public boolean hasListener(StorageListener o);

    public boolean registerListener(StorageListener e);

    public boolean unregisterListener(StorageListener o);

    public static interface StorageListener {
        public void onDelete(Download download, Path child);

        public void onUpdate(Download download, Path child, Path replacement);
    }
}
