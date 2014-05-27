package de.ncoder.studipsync.studip;

import de.ncoder.studipsync.data.Seminar;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CancellationException;

public interface StudipAdapter {
	public void init() throws StudipException;

	public void close() throws IOException;

	public boolean doLogin() throws CancellationException, StudipException;

	public boolean isLoggedIn();

	public List<Seminar> getSeminars() throws StudipException;

	public Seminar getSeminar(String hash) throws StudipException;

	public InputStream startDownload(String urlString) throws IOException, StudipException;
}
