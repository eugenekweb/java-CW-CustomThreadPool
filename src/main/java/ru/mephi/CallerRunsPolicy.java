package ru.mephi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CallerRunsPolicy implements CustomRejectedExecutionHandler {
    private final AtomicInteger rejectedCounter;
    private final CountDownLatch latch;

    public CallerRunsPolicy(AtomicInteger rejectedCounter, CountDownLatch latch) {
        this.rejectedCounter = rejectedCounter;
        this.latch = latch;
    }

    @Override
    public void rejectedExecution(Runnable r, CustomThreadPool executor) {
        if (executor.isEnableLogging()) {
            System.out.println("[Rejected] Task was rejected due to overload!");
        }
        if (!executor.isShutdown()) {
            r.run(); // выполняется в текущем потоке
        }
        if (rejectedCounter != null) {
            rejectedCounter.incrementAndGet();
        }
        if (latch != null) {
            latch.countDown();
        }
    }
}