package com.litrpg.fitness.exception;

/**
 * Thrown when a requested entity (e.g. a character or stat) does not exist.
 * Mapped to HTTP 404 by {@code GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
