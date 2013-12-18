package de.ncoder.studipsync.studip;

import de.ncoder.studipsync.data.LoginData;

public interface UIAdapter {
	public LoginData requestLoginData();

	public void close();
}
