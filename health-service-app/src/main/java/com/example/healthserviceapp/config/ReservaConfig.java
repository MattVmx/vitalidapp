package com.example.healthserviceapp.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservaConfig {
    @Bean
    public Clock reservaClock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
