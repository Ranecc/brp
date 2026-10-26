package dev.brp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fail fast on toy credentials against a real database (red line 10):
 * a blank BRP_DB_PASS on a PostgreSQL URL aborts startup instead of
 * silently running as brp/brp. H2 (tests, local) is exempt.
 */
@Component
public class ProdReadinessCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProdReadinessCheck.class);

    private final String dbUrl;
    private final String dbPass;

    public ProdReadinessCheck(@Value("${spring.datasource.url:}") String dbUrl,
                              @Value("${spring.datasource.password:}") String dbPass) {
        this.dbUrl = dbUrl;
        this.dbPass = dbPass;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (dbUrl.contains("postgresql") && (dbPass == null || dbPass.isBlank())) {
            throw new IllegalStateException(
                    "BRP_DB_PASS is empty against PostgreSQL — refusing to start. "
                            + "This is a feature, not an accident (AGENTS.md red line 10).");
        }
        log.info("prod readiness: datasource ok ({}backed)",
                dbUrl.contains("h2") ? "h2" : "postgres");
    }
}
