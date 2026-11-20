package com.studenttask.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.config.AppProperties;
import com.studenttask.telegram.client.HttpTelegramClient;
import com.studenttask.telegram.client.TelegramClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TelegramConfig {

    @Bean
    TelegramClient telegramClient(AppProperties props, ObjectMapper mapper) {
        AppProperties.Telegram t = props.telegram();
        return new HttpTelegramClient(t.apiBase(), t.botToken(), mapper, Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}
