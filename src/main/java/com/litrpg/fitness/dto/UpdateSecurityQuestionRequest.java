package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sets or changes the calling account's security question. Requires the
 * current password so an attacker with a stolen, still-live session token
 * can't silently take over account recovery.
 */
public class UpdateSecurityQuestionRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(max = 255)
    private String securityQuestion;

    @NotBlank
    @Size(max = 255)
    private String securityAnswer;

    public UpdateSecurityQuestionRequest() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getSecurityQuestion() {
        return securityQuestion;
    }

    public void setSecurityQuestion(String securityQuestion) {
        this.securityQuestion = securityQuestion;
    }

    public String getSecurityAnswer() {
        return securityAnswer;
    }

    public void setSecurityAnswer(String securityAnswer) {
        this.securityAnswer = securityAnswer;
    }
}
