package de.ncoder.studipsync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("static-access")
public class Values {
    public static final Logger LOG_MAIN = LoggerFactory.getLogger("MAIN");
    public static final Logger LOG_SYNCER = LoggerFactory.getLogger("Sync");
    public static final Logger LOG_NAVIGATE = LoggerFactory.getLogger("Navigate");
    public static final Logger LOG_SEMINARS = LoggerFactory.getLogger("Seminar");
    public static final Logger LOG_DOWNLOAD = LoggerFactory.getLogger("Download");

    private Values() {
    }
}
