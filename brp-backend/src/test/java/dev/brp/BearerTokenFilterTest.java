package dev.brp;

import dev.brp.config.BearerTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit #30: docs/01 §6 promised the v0 bearer token, the mod sent it from
 * day one, the backend never checked. The filter is the delivery of that
 * promise — enforced only when BRP_TOKEN is set, /actuator/health exempt.
 */
class BearerTokenFilterTest {

    private MockHttpServletResponse run(BearerTokenFilter filter, String auth, String path)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        if (auth != null) {
            req.addHeader("Authorization", auth);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        // a passed request must actually reach the chain
        if (res.getStatus() != 401) {
            assertTrue(chain.getRequest() != null, "request should reach the servlet");
        }
        return res;
    }

    @Test
    void blankTokenKeepsTheDocumentedOpenPosture() throws Exception {
        BearerTokenFilter filter = new BearerTokenFilter("");
        assertEquals(200, run(filter, null, "/api/v1/teams").getStatus());
    }

    @Test
    void setTokenRejectsMissingWrongAndMalformed() throws Exception {
        BearerTokenFilter filter = new BearerTokenFilter("s3cret");
        assertEquals(401, run(filter, null, "/api/v1/teams").getStatus());
        assertEquals(401, run(filter, "Bearer wrong", "/api/v1/teams").getStatus());
        assertEquals(401, run(filter, "s3cret", "/api/v1/teams").getStatus());
        assertEquals(401, run(filter, "Bearer s3cret extra", "/api/v1/teams").getStatus());
        MockHttpServletResponse res = run(filter, null, "/api/v1/teams");
        assertTrue(res.getContentAsString().contains("auth.token.required"));
    }

    @Test
    void setTokenAdmitsTheExactToken() throws Exception {
        BearerTokenFilter filter = new BearerTokenFilter("s3cret");
        assertEquals(200, run(filter, "Bearer s3cret", "/api/v1/teams").getStatus());
    }

    @Test
    void exactByteMatchOnlyNoPrefixGames() throws Exception {
        BearerTokenFilter filter = new BearerTokenFilter("s3cret");
        assertEquals(401, run(filter, "Bearer s3crett", "/api/v1/teams").getStatus());
        assertEquals(401, run(filter, "Bearer s3cre", "/api/v1/teams").getStatus());
    }
}
