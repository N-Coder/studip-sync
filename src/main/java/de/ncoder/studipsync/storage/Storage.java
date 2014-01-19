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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface Storage {
    public Path getRoot();

    public Path resolve(Seminar seminar);

    public Path resolve(Download download);

    public Path resolve(Download download, Path srcFile);

    public void close() throws IOException;

    public void store(Download download, InputStream dataSrc, boolean isDiff) throws IOException;

    public void store(Download download, Path dataSrc, boolean isDiff) throws IOException;

    public void delete(Download download) throws IOException;

    public boolean hasListener(StorageListener o);

    public boolean registerListener(StorageListener e);

    public boolean unregisterListener(StorageListener o);

    public static interface StorageListener {
        public void onDelete(Download download, Path child) throws OperationVeto;

        public void onUpdate(Download download, Path child, Path replacement) throws OperationVeto;
    }

    public static class OperationVeto extends Exception {
        public OperationVeto() {
        }

        public OperationVeto(String message) {
            super(message);
        }
    }

}
