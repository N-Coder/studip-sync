package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;
import org.apache.commons.cli.ParseException;

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
            String name = seminar.getFullName();
            //Remove illegal chars
            for (int i = 0; i < FILE_ILLEGAL_CHARS.length; i++) {
                name = name.replace(FILE_ILLEGAL_CHARS[i], '_');
            }
            //Fix spaces
            name = name.replaceAll("[_ ]+", " ");
            return root.resolve(name);
        }
    },
    Natural() {
        @Override
        public Path resolve(Path root, Seminar seminar) {
            return ByName.resolve(root, seminar);
        }

        @Override
        public Path resolve(Path root, Download download) {
            String path = download.getPath();
            int slash = path.indexOf('/');
            slash = slash >= 0 ? slash : path.length() - 1;
            while (IGNORED_FOLDERS.contains(path.substring(0, slash))) {
                path = path.substring(slash + 1);
            }
            return resolve(root, download.getSeminar()).resolve(path);
        }
    };

    public static final List<String> IGNORED_FOLDERS = Arrays.asList(
            "Allgemeiner_Dateiordner", "Hauptordner"
    );

    public static final char[] FILE_ILLEGAL_CHARS = new char[]{
            '%', '*', ',', ':', '?', '"', '|', '\\', '/', '<', '>', '[', ']'
    };

    @Override
    public Path resolve(Path root, Download download) {
        return resolve(root, download.getSeminar()).resolve(download.getPath());
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
                    ParseException pe = new ParseException(type + " is neither an UIType nor can it be resolved to a Java class.");
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
