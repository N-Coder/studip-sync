/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Niko Fink
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;
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
            return resolve(root, download.getSeminar()).resolve(trim(download.getPath()));
        }

        @Override
        public Path resolve(Path root, Download download, Path srcFile) {
            if (srcFile.isAbsolute()) {
                srcFile = srcFile.getRoot().relativize(srcFile);
            }
            String srcPath = trim(srcFile.toString());
            Path dstRoot = resolve(root, download);
            Path dstPath = dstRoot.resolve(srcPath);
            log.debug(dstPath + " = " + dstRoot + " <~ " + srcPath);
            return dstPath;
        }

        private String trim(String path) {
            int slash = nextSlash(path);
            while (IGNORED_FOLDERS.contains(path.substring(0, slash))) {
                path = path.substring(Math.min(slash + 1, path.length()));
                slash = nextSlash(path);
            }
            return path;
        }

        private int nextSlash(String path) {
            int slash = path.indexOf('/');
            if (slash < 0) {
                return path.length();
            } else {
                return slash;
            }
        }
    };

    private static final Logger log = LoggerFactory.getLogger(StandardPathResolver.class);

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

    @Override
    public Path resolve(Path root, Download download, Path srcFile) {
        if (srcFile.isAbsolute()) {
            srcFile = srcFile.getRoot().relativize(srcFile);
        }
        Path dstRoot = resolve(root, download).getParent(); //only dir in root of src == dir of download ("Hauptordner")
        Path dstPath = dstRoot.resolve(srcFile.toString());
        log.debug(dstPath + " = " + dstRoot + " <~ " + srcFile);
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
