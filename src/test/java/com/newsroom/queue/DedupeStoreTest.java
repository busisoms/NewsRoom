package com.newsroom.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedupeStoreTest {
    private static final int WINDOW_MINUTES = 5;
    private static final String WAMID = "wamid.HBgL1234567890";

    private MutableClock clock;
    private DedupeStore store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        store = new DedupeStore(WINDOW_MINUTES, clock);
    }

    @Test
    void aWamidThatWasNeverMarkedIsNotADuplicate() {
        assertFalse(store.isDuplicate(WAMID));
    }

    @Test
    void aMarkedWamidIsADuplicateWithinTheWindow() {
        store.markProcessed(WAMID);

        assertTrue(store.isDuplicate(WAMID));
    }

    @Test
    void aMarkedWamidIsStillADuplicateExactlyAtTheWindow() {
        store.markProcessed(WAMID);

        clock.advance(Duration.ofMinutes(WINDOW_MINUTES));

        assertTrue(store.isDuplicate(WAMID));
    }

    @Test
    void aMarkedWamidIsForgottenJustPastTheWindow() {
        store.markProcessed(WAMID);

        clock.advance(Duration.ofMinutes(WINDOW_MINUTES).plusSeconds(1));

        assertFalse(store.isDuplicate(WAMID));
    }

    @Test
    void aForgottenWamidCanBeMarkedAndDedupedAgain() {
        store.markProcessed(WAMID);
        clock.advance(Duration.ofMinutes(WINDOW_MINUTES).plusSeconds(1));
        assertFalse(store.isDuplicate(WAMID));

        store.markProcessed(WAMID);

        assertTrue(store.isDuplicate(WAMID));
    }

    @Test
    void wamidsAreCheckedIndependently() {
        store.markProcessed(WAMID);

        assertFalse(store.isDuplicate("wamid.someOtherMessage"));
    }
}
