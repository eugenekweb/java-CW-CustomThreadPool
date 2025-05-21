package ru.mephi;

import java.util.concurrent.TimeUnit;

public class ThreadPoolConstants {
    // Параметры пула
    public static final int CORE_POOL_SIZE = 2;
    public static final int MAX_POOL_SIZE = 4;
    public static final long KEEP_ALIVE_TIME = 5;
    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    public static final int QUEUE_SIZE = 5;
    public static final int MIN_SPARE_THREADS = 1;

    // Тестовые параметры
    public static final int TASK_COUNT = 100;        // Увеличено до 100
    public static final int TASK_DURATION_MS = 100;  // Уменьшено до 100 мс (более высокая нагрузка)
}