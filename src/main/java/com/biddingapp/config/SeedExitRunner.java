package com.biddingapp.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Used by {@code scripts/seed-demo.sh}: after CommandLineRunner seeders finish, exit so
 * Aiven PostgreSQL is left populated without keeping the API process running.
 */
@Component
@Profile("seed")
@Order(Ordered.LOWEST_PRECEDENCE)
public class SeedExitRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext context;

    public SeedExitRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
