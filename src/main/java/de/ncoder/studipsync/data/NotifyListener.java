package de.ncoder.studipsync.data;

import java.util.concurrent.TimeUnit;

public interface NotifyListener {
    public void release();

    public void awaitNotify() throws InterruptedException;

    public void awaitNotify(long timeout, TimeUnit unit) throws InterruptedException;
}