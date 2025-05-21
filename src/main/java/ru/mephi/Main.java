package ru.mephi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        testScenario(1);
        testScenario(2);
        testScenario(3);
        testScenario(4);
        testScenario(5);
    }

    private static void testScenario(int scenario) throws InterruptedException {
        int taskCount = ThreadPoolConstants.TASK_COUNT;
        int taskDurationMs = ThreadPoolConstants.TASK_DURATION_MS;

        System.out.println("=== Scenario " + scenario + " ===");

        runCustomBenchmark(scenario);
        runStandardBenchmark(scenario);

        System.out.println("------------------------------------\n");
    }

    private static void runCustomBenchmark(int scenario) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(ThreadPoolConstants.TASK_COUNT);
        AtomicInteger rejectedCounter = new AtomicInteger(0);

        CustomThreadPool customPool = null;

        switch (scenario) {
            case 1:
                customPool = new CustomThreadPool(2, 4, 5, TimeUnit.SECONDS, 5, 1, new CallerRunsPolicy(rejectedCounter, latch));
                break;
            case 2:
                customPool = new CustomThreadPool(2, 2, 5, TimeUnit.SECONDS, 5, 1, new CallerRunsPolicy(rejectedCounter, latch));
                break;
            case 3:
                customPool = new CustomThreadPool(2, 8, 5, TimeUnit.SECONDS, 10, 1, new CallerRunsPolicy(rejectedCounter, latch));
                break;
            case 4:
                customPool = new CustomThreadPool(2, 4, 5, TimeUnit.SECONDS, 2, 1, new CallerRunsPolicy(rejectedCounter, latch));
                break;
            case 5:
                customPool = new CustomThreadPool(4, 6, 5, TimeUnit.SECONDS, 10, 1, new CallerRunsPolicy(rejectedCounter, latch));
                break;
        }

        if (customPool == null) return;

        customPool.setEnableLogging(false); // Логи отключены по умолчанию
        customPool.setLatch(latch);

        long start = System.currentTimeMillis();

        for (int i = 0; i < ThreadPoolConstants.TASK_COUNT; i++) {
            final int taskId = i + 1;
            customPool.execute(() -> {
                try {
                    Thread.sleep(ThreadPoolConstants.TASK_DURATION_MS); // имитация работы
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean done = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) {
            System.out.println("[Benchmark] Timeout waiting for tasks.");
        }

        long end = System.currentTimeMillis();
        int completed = ThreadPoolConstants.TASK_COUNT - rejectedCounter.get();

        switch (scenario) {
            case 1:
                printBenchmarkResults("CustomThreadPool", 2, 4, 5, 1, start, end, completed, rejectedCounter.get());
                break;
            case 2:
                printBenchmarkResults("CustomThreadPool", 2, 2, 5, 1, start, end, completed, rejectedCounter.get());
                break;
            case 3:
                printBenchmarkResults("CustomThreadPool", 2, 8, 10, 1, start, end, completed, rejectedCounter.get());
                break;
            case 4:
                printBenchmarkResults("CustomThreadPool", 2, 4, 2, 1, start, end, completed, rejectedCounter.get());
                break;
            case 5:
                printBenchmarkResults("CustomThreadPool", 4, 6, 10, 1, start, end, completed, rejectedCounter.get());
                break;
        }

        customPool.close(); // корректное завершение пула
    }

    private static void runStandardBenchmark(int scenario) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(ThreadPoolConstants.TASK_COUNT);
        AtomicInteger rejectedCounter = new AtomicInteger(0);

        ThreadPoolExecutor standardPool = null;

        switch (scenario) {
            case 1:
                standardPool = new ThreadPoolExecutor(2, 4, 5, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(5), new MyThreadFactory("StandardPool"),
                        new ThreadPoolExecutor.CallerRunsPolicy());
                break;
            case 2:
                standardPool = new ThreadPoolExecutor(2, 2, 5, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(5), new MyThreadFactory("StandardPool"),
                        new ThreadPoolExecutor.CallerRunsPolicy());
                break;
            case 3:
                standardPool = new ThreadPoolExecutor(2, 8, 5, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(10), new MyThreadFactory("StandardPool"),
                        new ThreadPoolExecutor.CallerRunsPolicy());
                break;
            case 4:
                standardPool = new ThreadPoolExecutor(2, 4, 5, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(2), new MyThreadFactory("StandardPool"),
                        new ThreadPoolExecutor.CallerRunsPolicy());
                break;
            case 5:
                standardPool = new ThreadPoolExecutor(4, 6, 5, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(10), new MyThreadFactory("StandardPool"),
                        new ThreadPoolExecutor.CallerRunsPolicy());
                break;
        }

        if (standardPool == null) return;

        long start = System.currentTimeMillis();

        for (int i = 0; i < ThreadPoolConstants.TASK_COUNT; i++) {
            final int taskId = i + 1;
            standardPool.execute(() -> {
                try {
                    Thread.sleep(ThreadPoolConstants.TASK_DURATION_MS); // имитация работы
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean done = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) {
            System.out.println("[Benchmark] Timeout waiting for tasks.");
        }

        long end = System.currentTimeMillis();
        int completed = ThreadPoolConstants.TASK_COUNT - rejectedCounter.get();

        switch (scenario) {
            case 1:
                printBenchmarkResults("Standard Pool", 2, 4, 5, 0, start, end, completed, rejectedCounter.get());
                break;
            case 2:
                printBenchmarkResults("Standard Pool", 2, 2, 5, 0, start, end, completed, rejectedCounter.get());
                break;
            case 3:
                printBenchmarkResults("Standard Pool", 2, 8, 10, 0, start, end, completed, rejectedCounter.get());
                break;
            case 4:
                printBenchmarkResults("Standard Pool", 2, 4, 2, 0, start, end, completed, rejectedCounter.get());
                break;
            case 5:
                printBenchmarkResults("Standard Pool", 4, 6, 10, 0, start, end, completed, rejectedCounter.get());
                break;
        }

        standardPool.shutdownNow();
    }

    private static void printBenchmarkResults(
            String label,
            int corePoolSize,
            int maxPoolSize,
            int queueSize,
            int minSpareThreads,
            long start,
            long end,
            int completed,
            int rejected) {
        System.out.println("=== Benchmark: " + label + " ===");
        System.out.println("- Parameters:");
        System.out.println("   - corePoolSize: " + corePoolSize);
        System.out.println("   - maxPoolSize: " + maxPoolSize);
        System.out.println("   - queueSize: " + queueSize);
        System.out.println("   - minSpareThreads: " + minSpareThreads);
        System.out.println("- Tasks submitted: " + ThreadPoolConstants.TASK_COUNT);
        System.out.println("- Tasks completed: " + completed);
        System.out.println("- Tasks rejected: " + rejected);
        System.out.println("- Execution time (ms): " + (end - start));
        System.out.println("- Throughput (tasks/sec): " + (double) completed / ((end - start) / 1000.0));
        System.out.println("------------------------------------\n");
    }
}