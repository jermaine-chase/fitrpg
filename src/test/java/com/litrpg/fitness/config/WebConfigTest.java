package com.litrpg.fitness.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    /** Widens {@code getCorsConfigurations()} from protected to accessible-in-test. */
    private static final class TestableCorsRegistry extends CorsRegistry {
        @Override
        public Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }

    private WebConfig configWithOrigins(String allowedOrigins) {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", allowedOrigins);
        return config;
    }

    @Test
    void registersNoMappingWhenNoOriginsConfigured() {
        TestableCorsRegistry registry = new TestableCorsRegistry();

        configWithOrigins("").addCorsMappings(registry);

        assertThat(registry.getCorsConfigurations()).isEmpty();
    }

    @Test
    void registersNoMappingWhenOriginsIsBlank() {
        TestableCorsRegistry registry = new TestableCorsRegistry();

        configWithOrigins("   ").addCorsMappings(registry);

        assertThat(registry.getCorsConfigurations()).isEmpty();
    }

    @Test
    void registersConfiguredOriginsForApiPaths() {
        TestableCorsRegistry registry = new TestableCorsRegistry();

        configWithOrigins(" https://example.com , https://other.example.com ").addCorsMappings(registry);

        Map<String, CorsConfiguration> configs = registry.getCorsConfigurations();
        assertThat(configs).containsKey("/api/**");
        CorsConfiguration corsConfig = configs.get("/api/**");
        assertThat(corsConfig.getAllowedOriginPatterns())
                .containsExactly("https://example.com", "https://other.example.com");
        assertThat(corsConfig.getAllowCredentials()).isFalse();
    }

    @Test
    void ignoresBlankEntriesInOriginsList() {
        TestableCorsRegistry registry = new TestableCorsRegistry();

        configWithOrigins("https://example.com,,  ,https://other.example.com").addCorsMappings(registry);

        CorsConfiguration corsConfig = registry.getCorsConfigurations().get("/api/**");
        assertThat(corsConfig.getAllowedOriginPatterns())
                .containsExactly("https://example.com", "https://other.example.com");
    }
}
