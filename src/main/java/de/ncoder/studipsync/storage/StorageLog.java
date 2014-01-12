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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StorageLog implements Storage.StorageListener {
    private Map<StoredFile, StoreAction> actions = new HashMap<>();

    public void clear() {
        actions.clear();
    }

    @Override
    public void onDelete(Download download, Path child) {
        actions.put(new StoredFile(download, child), StoreAction.DELETE);
    }

    @Override
    public void onUpdate(Download download, Path child, Path replacement) {
        if (Files.exists(child)) {
            actions.put(new StoredFile(download, child), StoreAction.UPDATE);
        } else {
            actions.put(new StoredFile(download, child), StoreAction.CREATE);
        }
    }

    public Map<StoredFile, StoreAction> getActions() {
        return actions;
    }

    public String getStatusMessage() {
        return getStatusMessage(null);
    }

    public String getStatusMessage(Path relativeTo) {
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
            Path path = entry.getKey().getFile().toAbsolutePath();
            if (relativeTo != null) {
                path = relativeTo.relativize(path);
            }
            bob.append(path);
        }
        return bob.toString();
    }

    public static enum StoreAction {
        DELETE, UPDATE, CREATE
    }

    public static class StoredFile implements Comparable<StoredFile> {
        public final Download download;
        public final Path file;

        public StoredFile(Download download, Path file) {
            this.download = download;
            this.file = file;
        }

        public Download getDownload() {
            return download;
        }

        public Path getFile() {
            return file;
        }

        public Path getAbsolutePath() {
            return getFile().toAbsolutePath();
        }

        @Override
        public int compareTo(StoredFile o) {
            return getAbsolutePath().compareTo(o.getAbsolutePath());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StoredFile that = (StoredFile) o;
            if (download != null ? !download.equals(that.download) : that.download != null) return false;
            if (file != null ? !file.equals(that.file) : that.file != null) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = download != null ? download.hashCode() : 0;
            result = 31 * result + (file != null ? file.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return String.valueOf(file);
        }
    }
}
