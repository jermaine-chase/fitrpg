package com.litrpg.fitness.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds the application {@link DataSource}.
 *
 * <p>Heroku exposes Postgres credentials as a single connection string in the
 * {@code DATABASE_URL} environment variable, in the form:
 * <pre>postgres://user:password@host:port/database</pre>
 * The JDBC driver expects a different shape
 * ({@code jdbc:postgresql://host:port/database}) plus separate username/password,
 * so this class parses the URI and assembles a proper JDBC datasource.
 *
 * <p>When {@code DATABASE_URL} is absent (local development) it transparently
 * falls back to the {@code spring.datasource.*} properties, so the app boots
 * out of the box in both environments.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url:}") String localUrl,
            @Value("${spring.datasource.username:}") String localUsername,
            @Value("${spring.datasource.password:}") String localPassword) {

        String databaseUrl = System.getenv("DATABASE_URL");

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            log.info("DATABASE_URL detected - configuring datasource from Heroku connection string.");
            return buildFromHerokuUrl(databaseUrl);
        }

        log.info("DATABASE_URL not set - falling back to local spring.datasource.* properties.");
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName("org.postgresql.Driver")
                .url(localUrl)
                .username(localUsername)
                .password(localPassword)
                .build();
    }

    private DataSource buildFromHerokuUrl(String databaseUrl) {
        try {
            URI uri = new URI(databaseUrl);

            String userInfo = uri.getUserInfo();
            if (userInfo == null || !userInfo.contains(":")) {
                throw new IllegalStateException("DATABASE_URL is missing user:password credentials.");
            }
            String[] credentials = userInfo.split(":", 2);
            String username = credentials[0];
            String password = credentials[1];

            int port = uri.getPort() == -1 ? 5432 : uri.getPort();

            // Heroku Postgres requires SSL; sslmode=require avoids cert-chain validation issues.
            String jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%d%s?sslmode=require",
                    uri.getHost(), port, uri.getPath());

            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .driverClassName("org.postgresql.Driver")
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .build();

        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + databaseUrl, e);
        }
    }
}
