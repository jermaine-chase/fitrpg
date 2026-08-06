package com.litrpg.fitness.security;

import com.litrpg.fitness.repository.RevokedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-that-is-long-enough-for-hs256-signing", 60_000L);

    @Mock
    private RevokedTokenRepository revokedTokenRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, revokedTokenRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_authenticatesValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "hero", true);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(revokedTokenRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getUsername()).isEqualTo("hero");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains("ROLE_USER", "ROLE_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsRevokedToken() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "hero", false);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(revokedTokenRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ignoresMissingHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ignoresMalformedToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-real-token");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_nonAdminGetsOnlyUserRole() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID(), "sidekick", false);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(revokedTokenRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }
}
