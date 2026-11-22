package com.studenttask.notification;

import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.dto.SendResult;
import com.studenttask.telegram.dto.TelegramUpdate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Scripted fake: each sendMessage consumes the next scripted failure (null = success); empty script = success. */
final class FakeTelegramClient implements TelegramClient {

    record Sent(long chatId, String text) {
    }

    final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Deque<RuntimeException> script = new ArrayDeque<>();
    volatile Runnable duringSend = () -> { };

    synchronized void failNext(RuntimeException... failures) {
        for (RuntimeException f : failures) {
            script.add(f);
        }
    }

    @Override
    public SendResult sendMessage(long chatId, String text) {
        sent.add(new Sent(chatId, text));
        duringSend.run();
        RuntimeException failure;
        synchronized (this) {
            failure = script.poll();
        }
        if (failure != null) {
            throw failure;
        }
        return new SendResult(1L);
    }

    @Override
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setWebhook(String url, String secret) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteWebhook() {
        throw new UnsupportedOperationException();
    }
}
