package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.AuthResponse;
import com.litrpg.fitness.dto.ForgotPasswordRequest;
import com.litrpg.fitness.dto.LoginRequest;
import com.litrpg.fitness.dto.MeResponse;
import com.litrpg.fitness.dto.RegisterRequest;
import com.litrpg.fitness.dto.ResetPasswordRequest;
import com.litrpg.fitness.dto.SecurityQuestionResponse;
import com.litrpg.fitness.dto.UpdateSecurityQuestionRequest;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Player account registration, login, and logout. Register/login are open
 * endpoints; logout requires the token being logged out — see
 * {@link com.litrpg.fitness.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** {@code POST /api/auth/register} */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request.getUsername(), request.getPassword(),
                request.getSecurityQuestion(), request.getSecurityAnswer());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** {@code POST /api/auth/login} */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    /**
     * {@code GET /api/auth/me} — the caller's own account info, read fresh
     * from the database. Used on page load to decide whether to show
     * admin-only UI without needing a fresh login.
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getMe(principal.getId()));
    }

    /**
     * {@code POST /api/auth/forgot-password} — step 1 of account recovery:
     * looks up the security question for a username. 404s if the account
     * doesn't exist or never configured one; either way there's no next step.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<SecurityQuestionResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String question = authService.getSecurityQuestion(request.getUsername());
        return ResponseEntity.ok(new SecurityQuestionResponse(question));
    }

    /**
     * {@code POST /api/auth/reset-password} — step 2: verifies the security
     * answer and sets a new password, signing the player in immediately.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(
                request.getUsername(), request.getSecurityAnswer(), request.getNewPassword()));
    }

    /**
     * {@code GET /api/auth/security-question} — the caller's own recovery
     * question, or an absent one if they haven't set it yet.
     */
    @GetMapping("/security-question")
    public ResponseEntity<SecurityQuestionResponse> getMySecurityQuestion(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new SecurityQuestionResponse(authService.getMySecurityQuestion(principal.getId())));
    }

    /**
     * {@code PUT /api/auth/security-question} — sets or replaces the
     * caller's recovery question; requires the current password.
     */
    @PutMapping("/security-question")
    public ResponseEntity<Void> updateSecurityQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateSecurityQuestionRequest request) {
        authService.updateSecurityQuestion(principal.getId(), request.getCurrentPassword(),
                request.getSecurityQuestion(), request.getSecurityAnswer());
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code POST /api/auth/logout} — revokes the bearer token used to call
     * it, so it can no longer authenticate future requests.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing bearer token");
        }
        authService.logout(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }
}
