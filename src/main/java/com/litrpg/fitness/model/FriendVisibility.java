package com.litrpg.fitness.model;

/**
 * How much of a character sheet a user allows a friend to see.
 */
public enum FriendVisibility {
    /** Friend cannot see any character details, only that you're connected. */
    NONE,
    /** Friend can see character name and level only. */
    BASIC,
    /** Friend can see the full character sheet: XP, streak, and stats. */
    FULL
}
