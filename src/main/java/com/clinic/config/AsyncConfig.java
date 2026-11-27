package com.clinic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** Background work: WhatsApp messages are sent off the request thread so a slow or failing API never blocks users. */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String WHATSAPP_EXECUTOR = "whatsappExecutor";

    @Bean(name = WHATSAPP_EXECUTOR)
    public Executor whatsappExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("whatsapp-");
        executor.initialize();
        return executor;
    }
}
