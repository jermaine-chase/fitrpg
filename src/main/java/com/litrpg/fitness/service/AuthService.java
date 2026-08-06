package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AuthResponse;
import com.litrpg.fitness.dto.MeResponse;
import com.litrpg.fitness.model.RevokedToken;
import com.litrpg.fitness.model.User;
import com.litrpg.fitness.repository.RevokedTokenRepository;
import com.litrpg.fitness.repository.UserRepository;
import com.litrpg.fitness.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * Player account registration, login, and logout. Issues (and, on logout,
 * revokes) the JWTs consumed by
 * {@link com.litrpg.fitness.security.JwtAuthenticationFilter}.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                        RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    /** The very first account ever registered becomes an admin automatically. */
    @Transactional
    public AuthResponse register(String username, String password, String securityQuestion, String securityAnswer) {
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken: " + username);
        }
        boolean isFirstAccount = userRepository.count() == 0;
        User user = new User(username, passwordEncoder.encode(password));
        user.setSecurityQuestion(securityQuestion.trim());
        user.setSecurityAnswerHash(passwordEncoder.encode(normalizeAnswer(securityAnswer)));
        user.setAdmin(isFirstAccount);
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.isAdmin());
        return new AuthResponse(token, user.getUsername(), user.isAdmin());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.isAdmin());
        return new AuthResponse(token, user.getUsername(), user.isAdmin());
    }

    /** Fresh account info for the calling player — used to restore admin-link visibility etc. on page load. */
    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
        return new MeResponse(user.getUsername(), user.isAdmin());
    }

    /**
     * The security question configured for {@code username}, for the
     * forgot-password flow. Throws 404 for both a missing account and one
     * that hasn't configured a question — either way there's nothing to show.
     */
    @Transactional(readOnly = true)
    public String getSecurityQuestion(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No recovery question configured for this account"));
        if (user.getSecurityQuestion() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No recovery question configured for this account");
        }
        return user.getSecurityQuestion();
    }

    /**
     * Resets a forgotten password after verifying the security answer, then
     * signs the player straight in (there's nothing else the old password
     * could have protected that the answer hasn't already proven).
     */
    @Transactional
    public AuthResponse resetPassword(String username, String securityAnswer, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect answer"));
        if (user.getSecurityAnswerHash() == null
                || !passwordEncoder.matches(normalizeAnswer(securityAnswer), user.getSecurityAnswerHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect answer");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.isAdmin());
        return new AuthResponse(token, user.getUsername(), user.isAdmin());
    }

    /** The calling account's current security question, or {@code null} if none is set. */
    @Transactional(readOnly = true)
    public String getMySecurityQuestion(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId))
                .getSecurityQuestion();
    }

    /** Sets or replaces the calling account's security question, guarded by the current password. */
    @Transactional
    public void updateSecurityQuestion(UUID userId, String currentPassword,
                                        String securityQuestion, String securityAnswer) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
        }
        user.setSecurityQuestion(securityQuestion.trim());
        user.setSecurityAnswerHash(passwordEncoder.encode(normalizeAnswer(securityAnswer)));
        userRepository.save(user);
    }

    /** Case/whitespace-insensitive normalization so answer matching isn't needlessly brittle. */
    private String normalizeAnswer(String answer) {
        return answer.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Revokes the given (already-authenticated) token so it's rejected by
     * {@link com.litrpg.fitness.security.JwtAuthenticationFilter} even though
     * it hasn't naturally expired yet. Opportunistically prunes rows for
     * tokens that have since expired anyway, keeping the table small without
     * needing a scheduled job.
     */
    @Transactional
    public void logout(String token) {
        Claims claims = jwtService.parseClaims(token);
        LocalDateTime expiresAt = claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        revokedTokenRepository.save(new RevokedToken(claims.getId(), expiresAt));
        revokedTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}
