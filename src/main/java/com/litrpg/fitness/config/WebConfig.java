package com.litrpg.fitness.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-Origin Resource Sharing for the API.
 *
 * <p>The standalone HTML/JS client runs on a different origin from the API
 * (e.g. a file:// page, a local static server, or a separate host), so the
 * browser requires CORS headers on {@code /api/**}.
 *
 * <p>Allowed origins are configurable via the {@code APP_CORS_ALLOWED_ORIGINS}
 * environment variable (comma-separated). The default {@code *} is convenient
 * for local development; tighten it to your real frontend origin in production.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
