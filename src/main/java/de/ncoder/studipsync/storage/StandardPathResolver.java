package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.data.StudipFile;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public enum StandardPathResolver implements PathResolver {
	ByID() {
		@Override
		public Path resolve(Path root, Seminar seminar) {
			return root.resolve(seminar.getID());
		}
	},
	ByHash() {
		@Override
		public Path resolve(Path root, Seminar seminar) {
			return root.resolve(seminar.getHash());
		}
	},
	ByName() {
		@Override
		public Path resolve(Path root, Seminar seminar) {
			return root.resolve(fixName(seminar.getFullName()));
		}
	},
	Flat() {
		@Override
		public Path resolve(Path root, Seminar seminar) {
			return root.resolve(fixName(seminar.getFullName()));
		}

		@Override
		public Path resolve(Path root, StudipFile studipFile) {
			return resolveFlat(root, studipFile);
		}

		@Override
		public Path resolve(Path root, StudipFile studipFile, Path srcFile) {
			return resolveFlat(root, studipFile, srcFile);
		}
	},
	Natural() {
		@Override
		public Path resolve(Path root, Seminar seminar) {
			return root.resolve(fixName(seminar.getName()));
		}

		@Override
		public Path resolve(Path root, StudipFile studipFile) {
			return resolveFlat(root, studipFile);
		}

		@Override
		public Path resolve(Path root, StudipFile studipFile, Path srcFile) {
			return resolveFlat(root, studipFile, srcFile);
		}
	};

	private static final Logger log = LoggerFactory.getLogger(StandardPathResolver.class);

	public static final List<String> IGNORED_FOLDERS = Arrays.asList(
			"Allgemeiner_Dateiordner", "Hauptordner"
	);

	public static final char[] FILE_ILLEGAL_CHARS = new char[]{
			'%', '*', ',', ':', '?', '"', '|', '\\', '/', '<', '>', '[', ']'
	};

	protected String fixName(String name) {
		//Remove illegal chars
		for (int i = 0; i < FILE_ILLEGAL_CHARS.length; i++) {
			name = name.replace(FILE_ILLEGAL_CHARS[i], '_');
		}
		//Fix spaces
		return name.replaceAll("[_ ]+", " ");
	}

	protected String trimPath(String path) {
		int slash = nextSlash(path);
		while (IGNORED_FOLDERS.contains(path.substring(0, slash))) {
			path = path.substring(Math.min(slash + 1, path.length()));
			slash = nextSlash(path);
		}
		return path;
	}

	protected int nextSlash(String path) {
		int slash = path.indexOf('/');
		if (slash < 0) {
			return path.length();
		} else {
			return slash;
		}
	}

	@Override
	public Path resolve(Path root, StudipFile studipFile) {
		return resolve(root, studipFile.getSeminar()).resolve(studipFile.getPath());
	}

	protected Path resolveFlat(Path root, StudipFile studipFile) {
		return resolve(root, studipFile.getSeminar()).resolve(trimPath(studipFile.getPath()));
	}

	public Path resolve(Path root, StudipFile studipFile, Path srcFile) {
		if (srcFile.isAbsolute()) {
			srcFile = srcFile.getRoot().relativize(srcFile);
		}
		Path dstRoot = resolve(root, studipFile).getParent(); //only dir in root of src == dir of download ("Hauptordner")
		Path dstPath = dstRoot.resolve(srcFile.toString());
		log.debug(dstPath + " = " + dstRoot + " <~ " + srcFile);
		return dstPath;
	}

	protected Path resolveFlat(Path root, StudipFile studipFile, Path srcFile) {
		if (srcFile.isAbsolute()) {
			srcFile = srcFile.getRoot().relativize(srcFile);
		}
		String srcPath = trimPath(srcFile.toString());
		Path dstRoot = resolve(root, studipFile);
		Path dstPath = dstRoot.resolve(srcPath);
		log.debug(dstPath + " = " + dstRoot + " <~ " + srcPath);
		return dstPath;
	}

	// --------------------------------

	public static PathResolver getDefaultPathResolver() {
		return ByHash;
	}

	public static PathResolver getPathResolver(String type) throws ParseException {
		if (type != null) {
			try {
				return valueOf(type);
			} catch (IllegalArgumentException earg) {
				try {
					return loadPathResolver(type);
				} catch (ClassNotFoundException eclass) {
					ParseException pe = new ParseException(type + " is neither an StandardPathResolver nor can it be resolved to a Java class.");
					pe.initCause(eclass);
					pe.addSuppressed(earg);
					throw pe;
				}
			}
		} else {
			return getDefaultPathResolver();
		}
	}

	public static PathResolver loadPathResolver(String classname) throws ParseException, ClassNotFoundException {
		try {
			Class<?> clazz = Class.forName(classname);
			Object instance = clazz.newInstance();
			if (!(instance instanceof PathResolver)) {
				throw new ParseException(instance + " is not a PathResolver");
			}
			return (PathResolver) instance;
		} catch (InstantiationException | IllegalAccessException e) {
			ParseException pe = new ParseException("Could not instantiate class " + classname + ". " + e.getMessage());
			pe.initCause(e);
			throw pe;
		}
	}
}
