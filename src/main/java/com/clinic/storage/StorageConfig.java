package com.clinic.storage;

import com.clinic.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean
    StorageSettingsStore storageSettingsStore(AppProperties properties) {
        return new StorageSettingsStore(properties.storage().configFile());
    }
}
