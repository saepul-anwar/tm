package com.studenttask.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Single time source; code must inject this instead of calling Instant.now(). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
