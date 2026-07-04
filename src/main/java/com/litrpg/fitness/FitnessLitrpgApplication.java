package com.litrpg.fitness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Fitness LitRPG backend.
 * <p>
 * {@link EnableScheduling} activates the Midnight Decay Engine
 * ({@code MidnightDecayService}).
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class FitnessLitrpgApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitnessLitrpgApplication.class, args);
    }
}
