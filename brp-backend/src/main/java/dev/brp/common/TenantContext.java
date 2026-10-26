package dev.brp.common;

import org.slf4j.MDC;

/** Request-scoped tenant holder; also stamps the logging MDC. */
public final class TenantContext {

    public static final String HEADER = "X-BRP-Tenant";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
        MDC.put("tenant", tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove("tenant");
    }
}
