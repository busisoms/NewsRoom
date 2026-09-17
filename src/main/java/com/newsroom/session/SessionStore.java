package com.newsroom.session;

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

    private record Session(ConversationState state, Instant lastSeen) {}

    /**
     * Creates a store that expires sessions after {@code timeout} minutes of inactivity.
     *
     * @param timeout minutes of inactivity before a session expires
     */
    public SessionStore(int timeout) {
        this.timeout = Duration.ofMinutes(timeout);
    }

    /**
     * Returns the caller's current state, resetting to {@code NONE} if their previous
     * session expired. Refreshes their last-seen time either way.
     *
     * @param phoneNumber the caller's phone number
     */
    public ConversationState onMessage(String phoneNumber) {
        Instant now = Instant.now();

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
        Instant now = Instant.now();
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
