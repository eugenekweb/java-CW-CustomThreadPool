package ru.mephi;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class MyThreadFactory implements ThreadFactory {
    private final String poolName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public MyThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, poolName + "-worker-" + threadNumber.getAndIncrement());
        t.setDaemon(false);
        t.setUncaughtExceptionHandler((thread, ex) -> {
            System.err.println("[Error] Unhandled exception in thread " + thread.getName());
            ex.printStackTrace();
        });
        return t;
    }
}