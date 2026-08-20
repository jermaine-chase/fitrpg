package com.litrpg.fitness.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for the unauthenticated account endpoints
 * (register, login, forgot/reset-password). These take a username/password
 * or security-answer guess and would otherwise be open to unlimited
 * brute-force/credential-stuffing attempts once the app is reachable from
 * the internet (see the Cloudflare Tunnel exposure this guards against).
 *
 * <p>Keyed by client IP + path, in-memory only — sufficient for a
 * single-instance deployment; would need a shared store (e.g. Redis) behind
 * a load balancer.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password");

    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = 5 * 60 * 1000L;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        maybeEvictStaleEntries();

        String key = clientIp(request) + ':' + request.getRequestURI();
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || now - existing.windowStart > WINDOW_MILLIS) ? new Window(now) : existing);
        int count = window.count.incrementAndGet();

        if (count > MAX_REQUESTS_PER_WINDOW) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many attempts. Please wait a few minutes and try again.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Cloudflare sets this to the real client IP; the tunnel makes it trustworthy here since the app isn't otherwise reachable directly. */
    private String clientIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Opportunistic cleanup so long-running processes don't accumulate unbounded stale entries. */
    private void maybeEvictStaleEntries() {
        if (ThreadLocalRandom.current().nextInt(200) != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MILLIS);
    }

    private static final class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
