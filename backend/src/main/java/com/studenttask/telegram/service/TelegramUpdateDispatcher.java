package com.studenttask.telegram.service;

import com.studenttask.telegram.dto.TelegramUpdate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Hands webhook updates to a small bounded worker pool so the public endpoint answers immediately and a flood of
 * updates can neither tie up request threads nor grow memory: {@link #WORKER_THREADS} workers, a queue of
 * {@link #QUEUE_CAPACITY}; when both are full the update is dropped (Telegram still gets 200, so it does not
 * retry-storm). Nothing is ever thrown to the caller and only exception class names are logged.
 *
 * <p>A Spring bean: shut down with the context, letting in-flight work finish for a few seconds. Tests may supply
 * their own {@link Executor} (for example a direct one) through the second constructor.
 */
@Component
public class TelegramUpdateDispatcher implements DisposableBean {

    public static final int WORKER_THREADS = 4;
    public static final int QUEUE_CAPACITY = 100;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateDispatcher.class);

    private final TelegramUpdateHandler handler;
    private final Executor executor;

    @Autowired
    public TelegramUpdateDispatcher(TelegramUpdateHandler handler) {
        this(handler, boundedPool());
    }

    public TelegramUpdateDispatcher(TelegramUpdateHandler handler, Executor executor) {
        this.handler = handler;
        this.executor = executor;
    }

    /** Never blocks and never throws. */
    public void submit(TelegramUpdate update) {
        try {
            executor.execute(() -> process(update));
        } catch (RejectedExecutionException e) {
            log.debug("Telegram update dropped (worker queue full or shutting down)");
        }
    }

    private void process(TelegramUpdate update) {
        try {
            handler.handle(update);
        } catch (RuntimeException e) {
            log.error("Telegram update handling failed ({})", e.getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        if (executor instanceof ExecutorService pool) {
            pool.shutdown();
            if (!pool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    private static ExecutorService boundedPool() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "telegram-update-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), threads, new ThreadPoolExecutor.AbortPolicy());
    }
}
