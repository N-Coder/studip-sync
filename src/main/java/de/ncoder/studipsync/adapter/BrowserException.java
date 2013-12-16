package de.ncoder.studipsync.adapter;

import java.util.concurrent.ExecutionException;

public class BrowserException extends ExecutionException {
	public BrowserException() {
		super();
	}

	public BrowserException(String message) {
		super(message);
	}

	public BrowserException(Throwable cause) {
		super(cause);
	}

	public BrowserException(String message, Throwable cause) {
		super(message, cause);
	}

}
