package com.newsroom.session;

/**
 * A caller's position in the bot's conversation flow:
 * {@code NONE} (no active conversation) &rarr;
 * {@code AWAITING_OPTION} (menu sent, waiting on Weather/Sports/News) &rarr;
 * one of:
 * {@code AWAITING_CITY} (waiting for the user to enter a city, if Weather was picked),
 * {@code AWAITING_LEAGUE} (waiting for the user to pick a league, if Sports was picked),
 * {@code AWAITING_TOPIC} (waiting for the user to enter a topic, if News was picked).
 */
public enum ConversationState {
    NONE,
    AWAITING_OPTION,
    AWAITING_CITY,
    AWAITING_LEAGUE,
    AWAITING_TOPIC
}
