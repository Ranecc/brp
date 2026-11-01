package dev.brp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The v0 machine-API bearer token docs/01 section 6 promised and the code
 * never delivered (audit #30): the mod has sent Authorization: Bearer from
 * day one; the backend shrugged. When BRP_TOKEN (brp.auth.token) is set,
 * every /api request must carry exactly that token; otherwise the filter is
 * a no-op, which keeps the documented zero-arg singleplayer boot and the
 * test suite working unchanged.
 *
 * The portal stays out of scope on purpose — docs/01 section 2 declares it
 * the deliberate no-auth, internal-only exception, gated by loopback binding.
 * /actuator/health stays open for the supervisor's liveness probe.
 *
 * Comparison is constant-time; a token check that leaks its own length and
 * prefix match byte-by-byte would be a shame to add while fixing auth.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();

    public BearerTokenFilter(String token) {
        this.token = token == null ? "" : token.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (token.isEmpty() || authorized(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(),
                new ErrorResponse("auth.token.required",
                        "missing or invalid bearer token (BRP_TOKEN)"));
    }

    private boolean authorized(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        byte[] given = header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8);
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, expected);
    }

    private record ErrorResponse(String code, String message) {}
}
