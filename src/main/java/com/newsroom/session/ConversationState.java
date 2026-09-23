package com.newsroom.session;

/**
 * A caller's position in the bot's conversation flow:
 * {@code NONE} (no active conversation) &rarr;
 * {@code AWAITING_OPTION} (menu sent, waiting on Weather/Sports/News) &rarr;
 * {@code AWAITING_CITY} (Waiting for the user to enter a city if weather was picked)
 * {@code AWAITING_LEAGUE} (Waiting for user to pick a league if Sports was picked)
 * {@code AWAITING_TOPIC} (Waiting for user to enter a topic if news was picked)
 * {@code AWAITING_DETAILS} (option chosen, waiting on further detail, e.g. a city name).
 */
public enum ConversationState {
    NONE,
    AWAITING_OPTION,
    AWAITING_CITY,
    AWAITING_LEAGUE,
    AWAITING_TOPIC
}
