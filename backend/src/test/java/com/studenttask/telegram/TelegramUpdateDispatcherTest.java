package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.studenttask.telegram.dto.TelegramUpdate;
import com.studenttask.telegram.service.TelegramUpdateDispatcher;
import com.studenttask.telegram.service.TelegramUpdateHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TelegramUpdateDispatcherTest {

    private final TelegramUpdateHandler handler = mock(TelegramUpdateHandler.class);
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;
    private Level previous;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(TelegramUpdateDispatcher.class);
        previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
        logger.setLevel(previous);
    }

    private static TelegramUpdate update(long id) {
        return new TelegramUpdate(id, new TelegramUpdate.Message(new TelegramUpdate.Chat(9L, "private"), "/stop"));
    }

    @Test
    void submit_returns_before_the_handler_runs_and_the_worker_then_handles_the_update() {
        List<Runnable> queued = new ArrayList<>();
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(handler, queued::add);
        TelegramUpdate update = update(1);

        dispatcher.submit(update);

        verify(handler, never()).handle(any());
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        verify(handler).handle(update);
    }

    @Test
    void a_rejecting_executor_drops_the_update_without_throwing_and_logs_at_debug_only() {
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(handler, task -> {
            throw new RejectedExecutionException("queue full secret-detail-123");
        });

        assertThatCode(() -> dispatcher.submit(update(1))).doesNotThrowAnyException();

        verify(handler, never()).handle(any());
        assertThat(logs.list).isNotEmpty().allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(e.getFormattedMessage()).doesNotContain("secret-detail-123");
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

    @Test
    void worker_exceptions_are_swallowed_and_only_the_class_name_is_logged() {
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(handler, Runnable::run);
        doThrow(new IllegalStateException("leaky chat 777 token abc")).when(handler).handle(any());

        assertThatCode(() -> dispatcher.submit(update(1))).doesNotThrowAnyException();

        assertThat(logs.list).hasSize(1).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("IllegalStateException").doesNotContain("leaky")
                    .doesNotContain("777").doesNotContain("abc");
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

    @Test
    void the_real_bounded_pool_drops_overflow_and_never_throws_to_the_caller() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger handled = new AtomicInteger();
        doBlockUntil(release, handled);
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(handler);
        int capacity = TelegramUpdateDispatcher.WORKER_THREADS + TelegramUpdateDispatcher.QUEUE_CAPACITY;
        try {
            for (int i = 0; i < capacity + 50; i++) {
                int id = i;
                assertThatCode(() -> dispatcher.submit(update(id))).doesNotThrowAnyException();
            }
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (handled.get() < capacity && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            Thread.sleep(100); // any wrongly accepted extra update would show up now
            assertThat(handled.get()).isEqualTo(capacity);
        } finally {
            release.countDown();
            dispatcher.destroy();
        }
    }

    private void doBlockUntil(CountDownLatch release, AtomicInteger handled) {
        org.mockito.Mockito.doAnswer(inv -> {
            release.await(10, TimeUnit.SECONDS);
            handled.incrementAndGet();
            return null;
        }).when(handler).handle(any());
    }

    @Test
    void destroy_stops_the_pool_so_it_accepts_no_more_work() throws Exception {
        TelegramUpdateDispatcher dispatcher = new TelegramUpdateDispatcher(handler);
        dispatcher.destroy();

        dispatcher.submit(update(1));
        Thread.sleep(50);

        verify(handler, never()).handle(any());
    }
}
