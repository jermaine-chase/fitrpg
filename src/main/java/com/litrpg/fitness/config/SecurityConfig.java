package com.litrpg.fitness.config;

import com.litrpg.fitness.security.JwtAuthenticationFilter;
import com.litrpg.fitness.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * A single auth mechanism — JWT bearer tokens for player accounts (see
 * {@code /api/auth/**} and {@link JwtAuthenticationFilter}) — guards both
 * {@code /api/character/**}/{@code /api/friends/**} (any authenticated
 * player) and {@code /api/admin/**} (only accounts with the admin role,
 * granted via the token's {@code admin} claim). Everything else remains open.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RateLimitFilter rateLimitFilter) throws Exception {
        return http
                // Delegate CORS to the WebMvcConfigurer in WebConfig.
                .cors(Customizer.withDefaults())
                // REST API — CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/security-question").authenticated()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/character/**").authenticated()
                        .requestMatchers("/api/friends/**").authenticated()
                        .requestMatchers("/api/leaderboard/**").authenticated()
                        .requestMatchers("/api/quests/submit").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
