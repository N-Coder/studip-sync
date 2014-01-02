package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

import java.nio.file.Path;

public interface PathResolver {
    public Path resolve(Path root, Download download);

    public Path resolve(Path root, Seminar seminar);
}
