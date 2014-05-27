package de.ncoder.studipsync.data;

import java.util.Arrays;

public class LoginData {
	private String username;
	private char[] password;

	public LoginData(String username, char[] password) {
		this.username = username;
		this.password = password;
	}

	public String getUsername() {
		return username;
	}

	public char[] getPassword() {
		return password;
	}

	public void clean() {
		username = null;
		if (password != null) {
			Arrays.fill(password, ' ');
			password = null;
		}
	}
}
