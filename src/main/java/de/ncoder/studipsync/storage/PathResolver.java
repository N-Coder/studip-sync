package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.data.StudipFile;

import java.nio.file.Path;

public interface PathResolver {
	public Path resolve(Path root, Seminar seminar);

	public Path resolve(Path root, StudipFile studipFile);
}
