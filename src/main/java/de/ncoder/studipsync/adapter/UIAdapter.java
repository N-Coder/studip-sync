package de.ncoder.studipsync.adapter;

import de.ncoder.studipsync.data.LoginData;

public interface UIAdapter {
	public LoginData requestLoginData();

	public void close();
}
