package de.ncoder.studipsync.storage;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

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
}
