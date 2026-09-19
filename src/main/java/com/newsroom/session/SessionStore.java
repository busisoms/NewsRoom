package com.newsroom.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks where each user is in the conversation, keyed by phone number.
 * Sessions expire after a period of inactivity, so a user who
 * stops halfway
 * starts fresh next time instead of being stuck mid-flow.
 */
public class SessionStore {
    private final ConcurrentHashMap<String, Session> sessions =
            new ConcurrentHashMap<>();
    private final Duration timeout;
    private final Clock clock;

    private record Session(ConversationState state, Instant lastSeen) {}

    /**
     * Creates a store that expires sessions after {@code timeout} minutes of inactivity,
     * measured against the system clock.
     *
     * @param timeout minutes of inactivity before a session expires
     */
    public SessionStore(int timeout) {
        this(timeout, Clock.systemUTC());
    }

    /**
     * Creates a store that expires sessions after {@code timeout} minutes of inactivity,
     * measured against the given clock. Lets tests advance time without sleeping.
     *
     * @param timeout minutes of inactivity before a session expires
     * @param clock the source of the current time
     */
    public SessionStore(int timeout, Clock clock) {
        this.timeout = Duration.ofMinutes(timeout);
        this.clock = clock;
    }

    /**
     * Returns the caller's current state, resetting to {@code NONE} if their previous
     * session expired. Refreshes their last-seen time either way.
     *
     * @param phoneNumber the caller's phone number
     */
    public ConversationState onMessage(String phoneNumber) {
        Instant now = clock.instant();

        Session session = sessions.compute(phoneNumber, (phone, existing) -> {
            if (existing == null || isExpired(existing, now)) {
                return new Session(ConversationState.NONE, now);
            }
            return new Session(existing.state(), now);
        });

        return session.state();
    }

    /**
     * Sets the caller's state, creating a session if one doesn't exist yet.
     *
     * @param phoneNumber the caller's phone number
     * @param state the state to set
     */
    public void updateState(String phoneNumber, ConversationState state){
        Instant now = clock.instant();
        sessions.compute(phoneNumber, (phone, existing) ->
                new Session(state, now));
    }

    /**
     * Removes the caller's session, if any.
     *
     * @param phoneNumber the caller's phone number
     */
    public void dropSession(String phoneNumber) {
        sessions.remove(phoneNumber);
    }

    private boolean isExpired(Session session, Instant now) {
        return session.lastSeen().plus(timeout).isBefore(now);
    }
}
