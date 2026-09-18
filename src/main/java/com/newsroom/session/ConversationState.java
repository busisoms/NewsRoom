package com.newsroom.session;

/**
 * A caller's position in the bot's conversation flow:
 * {@code NONE} (no active conversation) &rarr;
 * {@code AWAITING_OPTION} (menu sent, waiting on Weather/Sports/News) &rarr;
 * {@code AWAITING_DETAILS} (option chosen, waiting on further detail, e.g. a city name).
 */
public enum ConversationState {
    NONE,
    AWAITING_OPTION,
    AWAITING_DETAILS
}
