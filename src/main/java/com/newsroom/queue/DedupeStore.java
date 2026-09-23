package com.newsroom.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which wamids {@link InboundMessageConsumer} has already processed, so a
 * Meta retry or an ActiveMQ redelivery of the same message doesn't send a second reply.
 *
 * <p>A wamid is only remembered once its message has been fully handled (see
 * {@link #markProcessed}), never just because it was seen; a redelivery that
 * follows a real processing failure must still go through, not be skipped as a
 * false duplicate.
 */
public class DedupeStore {
    private final ConcurrentHashMap<String, Instant> processed = new ConcurrentHashMap<>();
    private final Duration window;
    private final Clock clock;

    /**
     * Creates a store that forgets a wamid after {@code windowMinutes} minutes,
     * measured against the system clock.
     *
     * @param windowMinutes minutes a wamid is remembered as processed
     */
    public DedupeStore(int windowMinutes) {
        this(windowMinutes, Clock.systemUTC());
    }

    /**
     * Creates a store that forgets a wamid after {@code windowMinutes} minutes,
     * measured against the given clock. Lets tests advance time without sleeping.
     *
     * @param windowMinutes minutes a wamid is remembered as processed
     * @param clock the source of the current time
     */
    public DedupeStore(int windowMinutes, Clock clock) {
        this.window = Duration.ofMinutes(windowMinutes);
        this.clock = clock;
    }

    /**
     * Whether {@code wamid} was marked processed within the window and hasn't expired yet.
     *
     * @param wamid the message id to check
     */
    public boolean isDuplicate(String wamid) {
        Instant seenAt = processed.get(wamid);
        return seenAt != null && !isExpired(seenAt, clock.instant());
    }

    /**
     * Marks {@code wamid} as processed now, starting its window.
     *
     * @param wamid the message id to remember
     */
    public void markProcessed(String wamid) {
        processed.put(wamid, clock.instant());
    }

    private boolean isExpired(Instant seenAt, Instant now) {
        return seenAt.plus(window).isBefore(now);
    }
}
