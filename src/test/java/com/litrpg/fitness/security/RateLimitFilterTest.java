package com.litrpg.fitness.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final String LIMITED_PATH = "/api/auth/login";
    private static final String OPEN_PATH = "/api/character/some-id";
    private static final int MAX_REQUESTS_PER_WINDOW = 10;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    private static MockHttpServletRequest request(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static final class CountingChain implements FilterChain {
        final AtomicInteger calls = new AtomicInteger(0);

        @Override
        public void doFilter(ServletRequest req, ServletResponse resp) throws IOException, ServletException {
            calls.incrementAndGet();
        }
    }

    @Test
    void allowsRequestsUpToTheLimit() throws Exception {
        CountingChain chain = new CountingChain();

        for (int i = 0; i < MAX_REQUESTS_PER_WINDOW; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request(LIMITED_PATH, "1.2.3.4"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        assertThat(chain.calls.get()).isEqualTo(MAX_REQUESTS_PER_WINDOW);
    }

    @Test
    void blocksRequestsOnceLimitIsExceeded() throws Exception {
        CountingChain chain = new CountingChain();
        for (int i = 0; i < MAX_REQUESTS_PER_WINDOW; i++) {
            filter.doFilterInternal(request(LIMITED_PATH, "1.2.3.4"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(request(LIMITED_PATH, "1.2.3.4"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too many attempts");
        assertThat(chain.calls.get()).isEqualTo(MAX_REQUESTS_PER_WINDOW);
    }

    @Test
    void doesNotLimitPathsOutsideTheAuthEndpoints() throws Exception {
        CountingChain chain = new CountingChain();

        for (int i = 0; i < MAX_REQUESTS_PER_WINDOW + 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request(OPEN_PATH, "1.2.3.4"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        assertThat(chain.calls.get()).isEqualTo(MAX_REQUESTS_PER_WINDOW + 5);
    }

    @Test
    void tracksQuotaPerClientIndependently() throws Exception {
        CountingChain chain = new CountingChain();
        for (int i = 0; i < MAX_REQUESTS_PER_WINDOW; i++) {
            filter.doFilterInternal(request(LIMITED_PATH, "1.1.1.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse exhausted = new MockHttpServletResponse();
        filter.doFilterInternal(request(LIMITED_PATH, "1.1.1.1"), exhausted, chain);
        assertThat(exhausted.getStatus()).isEqualTo(429);

        // A different client IP has its own, untouched quota.
        MockHttpServletResponse fresh = new MockHttpServletResponse();
        filter.doFilterInternal(request(LIMITED_PATH, "2.2.2.2"), fresh, chain);
        assertThat(fresh.getStatus()).isEqualTo(200);
    }

    @Test
    void tracksQuotaPerPathIndependently() throws Exception {
        CountingChain chain = new CountingChain();
        for (int i = 0; i < MAX_REQUESTS_PER_WINDOW; i++) {
            filter.doFilterInternal(request(LIMITED_PATH, "3.3.3.3"), new MockHttpServletResponse(), chain);
        }

        // Same IP, different limited endpoint — its own quota, not shared with /login.
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request("/api/auth/register", "3.3.3.3"), response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void prefersCfConnectingIpOverRemoteAddr() throws Exception {
        CountingChain chain = new CountingChain();
        for (int i = 0; i < MAX_REQUESTS_PER_WINDOW; i++) {
            MockHttpServletRequest request = request(LIMITED_PATH, "10.0.0.1");
            request.addHeader("CF-Connecting-IP", "9.9.9.9");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        // Different underlying remote addr, but same Cloudflare-supplied client IP —
        // must share the exhausted quota rather than getting a fresh one.
        MockHttpServletRequest blockedRequest = request(LIMITED_PATH, "10.0.0.2");
        blockedRequest.addHeader("CF-Connecting-IP", "9.9.9.9");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blockedRequest, blockedResponse, chain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
    }
}
