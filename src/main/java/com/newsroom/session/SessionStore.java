package com.newsroom.session;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks where each user is in the conversation, keyed by phon
 e number.
 * Sessions expire after a period of inactivity, so a user who
 * stops halfway
 * starts fresh next time instead of being stuck mid-flow.
 */
public class SessionStore {
    private final ConcurrentHashMap<String, Session> sessions =
            new ConcurrentHashMap<>();
    private final Duration timeout;

    private record Session(ConversationState state, Instant lastSeen) {}

    public SessionStore(int timeout) {
        this.timeout = Duration.ofMinutes(timeout);
    }

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

    public void dropSession(String phoneNumber) {
        Instant now = Instant.now();
        if (isExpired(sessions.get(phoneNumber), now)){
            sessions.remove(phoneNumber);
        }
    }

    private boolean isExpired(Session session, Instant now) {
        return session.lastSeen().plus(timeout).isBefore(now);
    }
}
