package dev.brp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Injectable clock: accrual jobs (credit interest, bond coupons) and the load
 * plane take time through this seam, so tests can hold the clock still instead
 * of racing midnight.
 *
 * Also registers the v0 bearer token (audit #30): set BRP_TOKEN and the
 * machine API demands it; leave it blank for the documented open posture.
 */
@Configuration
public class BrpBeansConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<BearerTokenFilter> bearerTokenFilter(
            @Value("${brp.auth.token:}") String token) {
        FilterRegistrationBean<BearerTokenFilter> reg = new FilterRegistrationBean<>(
                new BearerTokenFilter(token));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(0);
        return reg;
    }
}
