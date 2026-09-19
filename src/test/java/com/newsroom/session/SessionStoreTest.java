package com.newsroom.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionStoreTest {
    private static final int TIMEOUT_MINUTES = 30;
    private static final String PHONE = "27821234567";

    private MutableClock clock;
    private SessionStore store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        store = new SessionStore(TIMEOUT_MINUTES, clock);
    }

    @Test
    void newUserStartsAtNone() {
        assertEquals(ConversationState.NONE, store.onMessage(PHONE));
    }

    @Test
    void updateStateIsVisibleOnNextMessage() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);

        assertEquals(ConversationState.AWAITING_OPTION, store.onMessage(PHONE));
    }

    @Test
    void stateIsKeptJustInsideTheTimeout() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);

        clock.advance(Duration.ofMinutes(TIMEOUT_MINUTES).minusSeconds(1));

        assertEquals(ConversationState.AWAITING_OPTION, store.onMessage(PHONE));
    }

    @Test
    void stateIsKeptExactlyAtTheTimeout() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);

        clock.advance(Duration.ofMinutes(TIMEOUT_MINUTES));

        assertEquals(ConversationState.AWAITING_OPTION, store.onMessage(PHONE));
    }

    @Test
    void stateResetsToNoneJustPastTheTimeout() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);

        clock.advance(Duration.ofMinutes(TIMEOUT_MINUTES).plusSeconds(1));

        assertEquals(ConversationState.NONE, store.onMessage(PHONE));
    }

    @Test
    void eachMessageRefreshesTheTimeout() {
        store.updateState(PHONE, ConversationState.AWAITING_DETAILS);

        // Three gaps of 20 minutes: 60 minutes total, but never idle for more than 30.
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofMinutes(20));
            assertEquals(ConversationState.AWAITING_DETAILS, store.onMessage(PHONE));
        }
    }

    @Test
    void expiredSessionStartsFreshAndIsUsableAgain() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);
        clock.advance(Duration.ofMinutes(TIMEOUT_MINUTES + 1));
        assertEquals(ConversationState.NONE, store.onMessage(PHONE));

        store.updateState(PHONE, ConversationState.AWAITING_DETAILS);

        assertEquals(ConversationState.AWAITING_DETAILS, store.onMessage(PHONE));
    }

    @Test
    void sessionsAreIndependentPerPhoneNumber() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);

        assertEquals(ConversationState.NONE, store.onMessage("27829999999"));
    }

    @Test
    void dropSessionResetsTheUserToNone() {
        store.updateState(PHONE, ConversationState.AWAITING_OPTION);

        store.dropSession(PHONE);

        assertEquals(ConversationState.NONE, store.onMessage(PHONE));
    }

    @Test
    void dropSessionOnUnknownNumberDoesNotThrow() {
        assertDoesNotThrow(() -> store.dropSession("never-messaged"));
    }
}
