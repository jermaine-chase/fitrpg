package com.litrpg.fitness.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.stream.Stream;

/**
 * Cross-Origin Resource Sharing for the API.
 *
 * <p>The app serves its own frontend, so ordinary usage is same-origin and
 * never triggers CORS at all. This only matters if a frontend hosted on a
 * <em>different</em> origin needs to call this API — in that case, set the
 * {@code APP_CORS_ALLOWED_ORIGINS} environment variable (comma-separated).
 * With nothing configured, no cross-origin mapping is registered and
 * cross-origin requests to {@code /api/**} are simply not permitted.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Stream.of(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (origins.length == 0) {
            return;
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
