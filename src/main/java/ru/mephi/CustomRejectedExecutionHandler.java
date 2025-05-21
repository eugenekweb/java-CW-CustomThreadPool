package ru.mephi;

public interface CustomRejectedExecutionHandler {
    void rejectedExecution(Runnable r, CustomThreadPool executor);
}