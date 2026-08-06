package com.litrpg.fitness.dto;

/**
 * The security question text for a username (forgot-password flow) or for
 * the calling account (authenticated settings view). {@code question} is
 * {@code null} when the account hasn't configured one yet.
 */
public class SecurityQuestionResponse {

    private String question;

    public SecurityQuestionResponse() {
    }

    public SecurityQuestionResponse(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
