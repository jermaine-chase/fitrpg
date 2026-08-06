package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.AuthResponse;
import com.litrpg.fitness.dto.ForgotPasswordRequest;
import com.litrpg.fitness.dto.LoginRequest;
import com.litrpg.fitness.dto.MeResponse;
import com.litrpg.fitness.dto.RegisterRequest;
import com.litrpg.fitness.dto.ResetPasswordRequest;
import com.litrpg.fitness.dto.UpdateSecurityQuestionRequest;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void register_returns201WithBody() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("hero");
        request.setPassword("password");
        request.setSecurityQuestion("Q?");
        request.setSecurityAnswer("A");
        AuthResponse expected = new AuthResponse("token", "hero", true);
        when(authService.register("hero", "password", "Q?", "A")).thenReturn(expected);

        var response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void login_returns200WithBody() {
        LoginRequest request = new LoginRequest();
        request.setUsername("hero");
        request.setPassword("password");
        AuthResponse expected = new AuthResponse("token", "hero", false);
        when(authService.login("hero", "password")).thenReturn(expected);

        var response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void me_delegatesToPrincipalId() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "hero");
        MeResponse expected = new MeResponse("hero", false);
        when(authService.getMe(userId)).thenReturn(expected);

        var response = controller.me(principal);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void forgotPassword_wrapsQuestionInResponse() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setUsername("hero");
        when(authService.getSecurityQuestion("hero")).thenReturn("Favorite color?");

        var response = controller.forgotPassword(request);

        assertThat(response.getBody().getQuestion()).isEqualTo("Favorite color?");
    }

    @Test
    void resetPassword_delegatesToService() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setUsername("hero");
        request.setSecurityAnswer("blue");
        request.setNewPassword("newpassword");
        AuthResponse expected = new AuthResponse("token", "hero", false);
        when(authService.resetPassword("hero", "blue", "newpassword")).thenReturn(expected);

        var response = controller.resetPassword(request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void getMySecurityQuestion_wrapsResult() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "hero");
        when(authService.getMySecurityQuestion(userId)).thenReturn("Favorite color?");

        var response = controller.getMySecurityQuestion(principal);

        assertThat(response.getBody().getQuestion()).isEqualTo("Favorite color?");
    }

    @Test
    void updateSecurityQuestion_returnsNoContent() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "hero");
        UpdateSecurityQuestionRequest request = new UpdateSecurityQuestionRequest();
        request.setCurrentPassword("current");
        request.setSecurityQuestion("Q?");
        request.setSecurityAnswer("A");

        var response = controller.updateSecurityQuestion(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).updateSecurityQuestion(userId, "current", "Q?", "A");
    }

    @Test
    void logout_revokesTokenFromBearerHeader() {
        var response = controller.logout("Bearer abc.def.ghi");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout("abc.def.ghi");
    }

    @Test
    void logout_rejectsMissingBearerPrefix() {
        assertThatThrownBy(() -> controller.logout("abc.def.ghi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing bearer token");
    }

    @Test
    void logout_rejectsNullHeader() {
        assertThatThrownBy(() -> controller.logout(null))
                .isInstanceOf(ResponseStatusException.class);
    }
}
