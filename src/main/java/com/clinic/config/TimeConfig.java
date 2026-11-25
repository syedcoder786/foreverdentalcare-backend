package com.clinic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Exposes a {@link Clock} in the clinic's local timezone. All "today"/"now" decisions (e.g. rejecting past
 * appointment dates) use this clock so the server's own timezone never matters, and tests can fix the time.
 */
@Configuration
public class TimeConfig {

    @Bean
    public ZoneId clinicZoneId(AppProperties properties) {
        return ZoneId.of(properties.timezone());
    }

    @Bean
    public Clock clinicClock(ZoneId clinicZoneId) {
        return Clock.system(clinicZoneId);
    }
}
