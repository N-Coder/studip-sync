package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;
import org.apache.commons.cli.ParseException;

import java.nio.file.Path;

import static de.ncoder.studipsync.studip.StudipAdapter.URI_ILLEGAL_CHARS;
import static de.ncoder.studipsync.studip.StudipAdapter.URI_REPLACE_CHARS;

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
            for (int i = 0; i < URI_ILLEGAL_CHARS.length; i++) {
                name = name.replace(URI_ILLEGAL_CHARS[i], URI_REPLACE_CHARS[i]);
            }
            return root.resolve(name);
        }
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
