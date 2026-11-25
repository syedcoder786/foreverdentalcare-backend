package com.clinic.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    /** Serialise times as "HH:mm" (clinic-local wall clock); input accepts both "HH:mm" and "HH:mm:ss". */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localTimeFormatCustomizer() {
        return builder -> builder.serializerByType(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
    }
}
