package com.eczam.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for RateLimitFilter — no Spring context needed.
 * Directly instantiates the filter with a small limit (3/min) and fires
 * mock requests, verifying the 4th is rejected with 429.
 */
class RateLimitFilterTest {

    private static final String AUTH_PATH = "/auth/register";

    private MockHttpServletResponse fire(RateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setServletPath(path);
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test
    void first_N_requests_pass_then_429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 100); // authPerMinute=3

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = fire(filter, AUTH_PATH);
            assertThat(res.getStatus()).as("request %d should pass", i + 1)
                    .isNotEqualTo(429);
        }

        // 4th request should be rate-limited
        MockHttpServletResponse res = fire(filter, AUTH_PATH);
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void different_ips_have_independent_buckets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 100); // 1 per minute per IP

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", AUTH_PATH);
        req1.setServletPath(AUTH_PATH);
        req1.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, mock(FilterChain.class));
        assertThat(res1.getStatus()).isNotEqualTo(429);

        // Different IP should still get through
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", AUTH_PATH);
        req2.setServletPath(AUTH_PATH);
        req2.setRemoteAddr("5.6.7.8");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, mock(FilterChain.class));
        assertThat(res2.getStatus()).isNotEqualTo(429);

        // Same first IP should now be rate-limited
        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", AUTH_PATH);
        req3.setServletPath(AUTH_PATH);
        req3.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, mock(FilterChain.class));
        assertThat(res3.getStatus()).isEqualTo(429);
    }

    @Test
    void non_auth_paths_are_not_rate_limited_by_auth_bucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 100); // very tight: 1 per minute for auth

        // Exhaust the auth bucket
        fire(filter, AUTH_PATH);

        // A non-auth path (e.g. /users/me) should pass regardless
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/users/me");
        req.setServletPath("/users/me");
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        // GET is not classified (only POST/DELETE) → passes through, chain is called
        verify(chain).doFilter(any(), any());
    }
}
