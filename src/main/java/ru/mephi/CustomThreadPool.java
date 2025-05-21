package ru.mephi;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class CustomThreadPool implements CustomExecutor {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final ThreadFactory threadFactory;
    private final AtomicReferenceArray<Worker> workers;
    private final AtomicInteger activeWorkerCount = new AtomicInteger(0);
    private volatile boolean isShutdown = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private boolean enableLogging = true;
    private final CustomRejectedExecutionHandler rejectedExecutionHandler;
    private CountDownLatch latch;

    public CustomThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int queueSize,
            int minSpareThreads,
            CustomRejectedExecutionHandler handler) {

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.threadFactory = new MyThreadFactory("MyPool");
        this.workers = new AtomicReferenceArray<>(maxPoolSize);
        this.rejectedExecutionHandler = handler;

        for (int i = 0; i < corePoolSize; i++) {
            createAndStartWorker();
        }

        scheduleEnsureMinSpareThreads();
    }

    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) return;

        Runnable wrappedCommand = () -> {
            try {
                command.run();
            } finally {
                activeTaskCount.decrementAndGet();
                if (latch != null) {
                    latch.countDown();
                }
            }
        };

        activeTaskCount.incrementAndGet();

        Worker targetWorker = selectWorker();
        if (targetWorker != null) {
            try {
                boolean added = targetWorker.getQueue().offer(wrappedCommand, keepAliveTime, timeUnit);
                if (added) {
                    log("[selectWorker] Task accepted into worker " + targetWorker.getName());
                } else {
                    reject(wrappedCommand);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reject(wrappedCommand);
            }
        } else {
            reject(wrappedCommand);
        }
    }

    private void reject(Runnable command) {
        if (rejectedExecutionHandler != null) {
            rejectedExecutionHandler.rejectedExecution(command, this);
        } else {
            log("[Rejected] Task was rejected due to overload!");
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> future = new FutureTask<>(callable);
        execute(future);
        return future;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        for (int i = 0; i < maxPoolSize; i++) {
            Worker worker = workers.get(i);
            if (worker != null) {
                worker.interrupt();
            }
        }
        log("[Pool] Shutdown initiated.");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        for (int i = 0; i < maxPoolSize; i++) {
            Worker worker = workers.get(i);
            if (worker != null) {
                worker.interrupt();
            }
        }
        log("[Pool] Immediate shutdown initiated.");
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanosTimeout = unit.toNanos(timeout);
        final long deadline = System.nanoTime() + nanosTimeout;

        while (!isAllTasksDone()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                return false;
            }

            Thread.sleep(Math.min(TimeUnit.MILLISECONDS.convert(remaining, TimeUnit.NANOSECONDS), 500));
        }

        return true;
    }

    public void close() {
        shutdown();
        try {
            awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private boolean isAllTasksDone() {
        return activeTaskCount.get() == 0;
    }

    private void log(String message) {
        if (enableLogging) {
            System.out.println("[" + getName() + "] " + message);
        }
    }

    private String getName() {
        return "CustomThreadPool";
    }

    private Worker selectWorker() {
        if (isShutdown) return null;

        Worker leastLoaded = null;
        int minSize = Integer.MAX_VALUE;

        for (int i = 0; i < maxPoolSize; i++) {
            Worker worker = workers.get(i);
            if (worker != null && worker.getQueue().remainingCapacity() > 0) {
                int size = worker.getQueue().size();
                if (size < minSize) {
                    minSize = size;
                    leastLoaded = worker;
                }
            }
        }

        if (leastLoaded != null) {
            log("[selectWorker] Selected worker with queue size: " + leastLoaded.getQueue().size());
            return leastLoaded;
        }

        if (activeWorkerCount.get() < maxPoolSize) {
            log("[selectWorker] No suitable worker found. Trying to create a new one.");
            return createAndStartWorker();
        }

        return null;
    }

    private Worker createAndStartWorker() {
        if (isShutdown) return null;

        int index = -1;
        for (int i = 0; i < maxPoolSize; i++) {
            if (workers.get(i) == null) {
                index = i;
                break;
            }
        }

        if (index == -1) return null;

        Worker worker = new Worker(index);
        Thread t = threadFactory.newThread(worker);
        t.start();
        workers.set(index, worker);
        activeWorkerCount.incrementAndGet();
        log("[ThreadFactory] Creating new thread: " + t.getName());
        return worker;
    }

    private void scheduleEnsureMinSpareThreads() {
        scheduler.scheduleAtFixedRate(this::ensureMinSpareThreads, 0, 500, TimeUnit.MILLISECONDS); // каждые 500 мс
    }

    private static final double QUEUE_USAGE_THRESHOLD = 0.7;

    private void ensureMinSpareThreads() {
        if (isShutdown) return;

        int totalTasks = activeTaskCount.get();
        int totalQueuesCapacity = maxPoolSize * queueSize;
        boolean isOverloaded = totalTasks > (int) (totalQueuesCapacity * QUEUE_USAGE_THRESHOLD);

        if (isOverloaded || activeWorkerCount.get() <= corePoolSize) {
            log("[Pool] Increasing threads due to high load or low pool size.");
            createAndStartWorker();
        } else {
            int currentAvailable = 0;
            for (int i = 0; i < maxPoolSize; i++) {
                Worker worker = workers.get(i);
                if (worker != null && worker.getQueue().isEmpty()) {
                    currentAvailable++;
                }
            }

            int spareSlots = maxPoolSize - corePoolSize;
            if (currentAvailable < minSpareThreads && activeWorkerCount.get() > corePoolSize) {
                log("[Pool] Creating new thread to maintain spare capacity.");
                createAndStartWorker();
            }
        }
    }

    private class Worker implements Runnable {
        private final int id;
        private final BlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(queueSize);
        private Thread thread;

        public Worker(int id) {
            this.id = id;
        }

        public String getName() {
            return "MyPool-worker-" + id;
        }

        public BlockingQueue<Runnable> getQueue() {
            return taskQueue;
        }

        public void interrupt() {
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void run() {
            thread = Thread.currentThread();

            try {
                while (!Thread.interrupted() && !isShutdown) {
                    Runnable task = taskQueue.poll(keepAliveTime, timeUnit);

                    if (task == null) {
                        // Не завершаем поток, если он относится к corePoolSize
                        if (activeWorkerCount.get() > corePoolSize) {
                            log("[Worker] " + getName() + " idle timeout, stopping.");
                            break;
                        }
                        continue;
                    }

                    log("[Worker] " + getName() + " executes task.");
                    task.run();
                }
            } catch (InterruptedException e) {
                log("[Worker] " + getName() + " interrupted.");
                Thread.currentThread().interrupt();
            } finally {
                log("[Worker] " + getName() + " terminated.");
                workers.set(id, null);
                activeWorkerCount.decrementAndGet();
            }
        }
    }
}