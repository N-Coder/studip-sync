package de.ncoder.studipsync.studip;

import de.ncoder.studipsync.data.Download;
import de.ncoder.studipsync.data.Seminar;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CancellationException;

public interface StudipAdapter {
    void init() throws StudipException;

    void close() throws IOException;

    void displayWebsite();

    boolean doLogin() throws CancellationException, StudipException;

    boolean isLoggedIn();

    void selectSeminar(Seminar seminar) throws StudipException;

    boolean isSeminarSelected(Seminar seminar);

    Seminar getSelectedSeminar();

    List<Seminar> parseSeminars() throws StudipException;

    List<Download> parseDownloads(String downloadsUrl, boolean structured) throws StudipException;

    InputStream startDownload(Download download, boolean diffOnly) throws StudipException, IOException;
}
