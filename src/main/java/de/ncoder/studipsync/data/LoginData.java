package de.ncoder.studipsync.data;

import java.util.Arrays;

public class LoginData {
    private String username;
    private char[] password;

    public LoginData(String username, char[] password) {
        super();
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public void clean() {
        //FIXME clean not called
        username = null;
        Arrays.fill(password, ' ');
        password = null;
    }
}
