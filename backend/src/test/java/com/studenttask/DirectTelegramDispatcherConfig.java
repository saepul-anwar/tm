package com.studenttask;

import com.studenttask.telegram.service.TelegramUpdateDispatcher;
import com.studenttask.telegram.service.TelegramUpdateHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Integration tests run webhook updates on the calling thread so assertions stay deterministic. The production
 * bounded pool is covered by TelegramUpdateDispatcherTest.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DirectTelegramDispatcherConfig {

    @Bean
    @Primary
    TelegramUpdateDispatcher directTelegramUpdateDispatcher(TelegramUpdateHandler handler) {
        return new TelegramUpdateDispatcher(handler, Runnable::run);
    }
}
