package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AuthResponse;
import com.litrpg.fitness.dto.MeResponse;
import com.litrpg.fitness.model.User;
import com.litrpg.fitness.repository.RevokedTokenRepository;
import com.litrpg.fitness.repository.UserRepository;
import com.litrpg.fitness.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, revokedTokenRepository);
    }

    @Test
    void register_firstAccountBecomesAdmin() {
        when(userRepository.existsByUsername("hero")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("password")).thenReturn("hashed-password");
        when(passwordEncoder.encode("blue")).thenReturn("hashed-answer");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(), eq("hero"), eq(true))).thenReturn("token-123");

        AuthResponse response = authService.register("hero", "password", "Favorite color?", "blue");

        assertThat(response.getToken()).isEqualTo("token-123");
        assertThat(response.getUsername()).isEqualTo("hero");
        assertThat(response.isAdmin()).isTrue();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().isAdmin()).isTrue();
        assertThat(savedUser.getValue().getSecurityAnswerHash()).isEqualTo("hashed-answer");
    }

    @Test
    void register_secondAccountIsNotAdmin() {
        when(userRepository.existsByUsername("sidekick")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(), anyString(), eq(false))).thenReturn("token-456");

        AuthResponse response = authService.register("sidekick", "password", "Favorite color?", "red");

        assertThat(response.isAdmin()).isFalse();
    }

    @Test
    void register_rejectsDuplicateUsername() {
        when(userRepository.existsByUsername("hero")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("hero", "password", "q", "a"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_succeedsWithCorrectPassword() {
        User user = new User("hero", "hashed-password");
        user.setId(UUID.randomUUID());
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), "hero", false)).thenReturn("token-123");

        AuthResponse response = authService.login("hero", "password");

        assertThat(response.getToken()).isEqualTo("token-123");
        assertThat(response.getUsername()).isEqualTo("hero");
    }

    @Test
    void login_rejectsUnknownUsername() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "password"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_rejectsWrongPassword() {
        User user = new User("hero", "hashed-password");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("hero", "wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid username or password");

        verify(jwtService, never()).generateToken(any(), anyString(), anyBoolean());
    }

    @Test
    void getMe_returnsAccountInfo() {
        User user = new User("hero", "hashed-password");
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setAdmin(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        MeResponse response = authService.getMe(id);

        assertThat(response.getUsername()).isEqualTo("hero");
        assertThat(response.isAdmin()).isTrue();
    }

    @Test
    void getMe_throwsWhenUserMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(id))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getSecurityQuestion_returnsQuestionWhenConfigured() {
        User user = new User("hero", "hashed-password");
        user.setSecurityQuestion("Favorite color?");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));

        assertThat(authService.getSecurityQuestion("hero")).isEqualTo("Favorite color?");
    }

    @Test
    void getSecurityQuestion_throws404WhenNotConfigured() {
        User user = new User("hero", "hashed-password");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.getSecurityQuestion("hero"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No recovery question configured");
    }

    @Test
    void getSecurityQuestion_throws404WhenUserMissing() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getSecurityQuestion("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No recovery question configured");
    }

    @Test
    void resetPassword_succeedsWithCorrectAnswer() {
        User user = new User("hero", "old-hash");
        user.setId(UUID.randomUUID());
        user.setSecurityAnswerHash("answer-hash");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("blue", "answer-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(jwtService.generateToken(user.getId(), "hero", false)).thenReturn("token-789");

        AuthResponse response = authService.resetPassword("hero", "  Blue  ", "newpassword");

        assertThat(response.getToken()).isEqualTo("token-789");
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_rejectsWrongAnswer() {
        User user = new User("hero", "old-hash");
        user.setSecurityAnswerHash("answer-hash");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "answer-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.resetPassword("hero", "wrong", "newpassword"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Incorrect answer");

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_rejectsWhenNoSecurityAnswerConfigured() {
        User user = new User("hero", "old-hash");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword("hero", "blue", "newpassword"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Incorrect answer");
    }

    @Test
    void resetPassword_locksAccountAfterTooManyFailedAttempts() {
        User user = new User("hero", "old-hash");
        user.setSecurityAnswerHash("answer-hash");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "answer-hash")).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.resetPassword("hero", "wrong", "newpassword"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Incorrect answer");
        }

        assertThatThrownBy(() -> authService.resetPassword("hero", "wrong", "newpassword"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many incorrect attempts");

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_lockoutIsScopedPerUsername() {
        User hero = new User("hero", "hash");
        hero.setSecurityAnswerHash("hero-answer-hash");
        User sidekick = new User("sidekick", "hash2");
        sidekick.setId(UUID.randomUUID());
        sidekick.setSecurityAnswerHash("sidekick-answer-hash");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(hero));
        when(userRepository.findByUsernameIgnoreCase("sidekick")).thenReturn(Optional.of(sidekick));
        when(passwordEncoder.matches("wrong", "hero-answer-hash")).thenReturn(false);
        when(passwordEncoder.matches("blue", "sidekick-answer-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(jwtService.generateToken(sidekick.getId(), "sidekick", false)).thenReturn("token-xyz");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.resetPassword("hero", "wrong", "newpassword"))
                    .isInstanceOf(ResponseStatusException.class);
        }

        // "hero" is now locked out, but a different username has its own, untouched attempt counter.
        AuthResponse response = authService.resetPassword("sidekick", "blue", "newpassword");
        assertThat(response.getToken()).isEqualTo("token-xyz");
    }

    @Test
    void resetPassword_successfulResetClearsTheFailureCounter() {
        User user = new User("hero", "old-hash");
        user.setId(UUID.randomUUID());
        user.setSecurityAnswerHash("answer-hash");
        when(userRepository.findByUsernameIgnoreCase("hero")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "answer-hash")).thenReturn(false);
        when(passwordEncoder.matches("blue", "answer-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(jwtService.generateToken(user.getId(), "hero", false)).thenReturn("token-1");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.resetPassword("hero", "wrong", "newpassword"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Incorrect answer");
        }

        // Succeeds before hitting the lockout threshold, which should reset the counter.
        AuthResponse response = authService.resetPassword("hero", "blue", "newpassword");
        assertThat(response.getToken()).isEqualTo("token-1");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.resetPassword("hero", "wrong", "newpassword"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Incorrect answer");
        }
    }

    @Test
    void updateSecurityQuestion_succeedsWithCorrectPassword() {
        User user = new User("hero", "current-hash");
        UUID id = UUID.randomUUID();
        user.setId(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "current-hash")).thenReturn(true);
        when(passwordEncoder.encode("purple")).thenReturn("purple-hash");

        authService.updateSecurityQuestion(id, "current", " Favorite color? ", " Purple ");

        assertThat(user.getSecurityQuestion()).isEqualTo("Favorite color?");
        assertThat(user.getSecurityAnswerHash()).isEqualTo("purple-hash");
        verify(userRepository).save(user);
    }

    @Test
    void updateSecurityQuestion_rejectsWrongPassword() {
        User user = new User("hero", "current-hash");
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "current-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.updateSecurityQuestion(id, "wrong", "Q?", "A"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Incorrect password");

        verify(userRepository, never()).save(any());
    }

    @Test
    void logout_revokesTokenAndPrunesExpiredOnes() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-123");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(jwtService.parseClaims("token-abc")).thenReturn(claims);

        authService.logout("token-abc");

        verify(revokedTokenRepository).save(any());
        verify(revokedTokenRepository).deleteAllByExpiresAtBefore(any());
    }
}
