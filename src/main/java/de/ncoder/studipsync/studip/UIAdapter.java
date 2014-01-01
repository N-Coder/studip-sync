package de.ncoder.studipsync.studip;

import de.ncoder.studipsync.data.LoginData;

import java.net.URI;

public interface UIAdapter {
    public LoginData requestLoginData();

    public void displayWebpage(URI uri);

    public void close();
}
