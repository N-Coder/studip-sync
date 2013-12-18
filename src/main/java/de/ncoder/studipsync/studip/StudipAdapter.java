package de.ncoder.studipsync.studip;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface StudipAdapter {
    void init() throws ExecutionException;

    void close() throws IOException;

    boolean doLogin() throws CancellationException, ExecutionException;

    boolean isLoggedIn() throws ExecutionException;

    void selectSeminar(Seminar seminar) throws ExecutionException;

    boolean isSeminarSelected(Seminar seminar) throws ExecutionException;

    Seminar getSelectedSeminar();

    List<Seminar> parseSeminars() throws ExecutionException;

    List<Download> parseDownloads(String downloadsUrl, boolean structured) throws ExecutionException;

    InputStream startDownload(Download download, boolean diffOnly) throws ExecutionException, IOException;
}
