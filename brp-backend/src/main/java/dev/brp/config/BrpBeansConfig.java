package dev.brp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Injectable clock: accrual jobs (credit interest, bond coupons) and the load
 * plane take time through this seam, so tests can hold the clock still instead
 * of racing midnight.
 */
@Configuration
public class BrpBeansConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
