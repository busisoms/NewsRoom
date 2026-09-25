package com.newsroom.conversation;

import com.newsroom.session.ConversationState;

/**
 * The engine's output: the next conversation state, an optional reply to send
 * now, and an optional lookup whose result the consumer sends afterwards.
 *
 * @param nextState the state to store after this decision
 * @param reply what to send, or {@code null} when nothing should be sent
 * @param lookup data to fetch and reply with, or {@code null} when there's nothing to fetch
 */
public record Decision(ConversationState nextState, Reply reply, Lookup lookup) {

    /**
     * Creates a decision that sends a reply.
     */
    public static Decision replyWith(ConversationState state, Reply reply) {
        return new Decision(state, reply, null);
    }

    /**
     * Creates a decision that sends nothing.
     */
    public static Decision noReply(ConversationState state) {
        return new Decision(state, null, null);
    }

    /**
     * Creates a decision that sends a lookup
     */
    public static Decision lookUpFor(ConversationState state, Lookup lookup){
        return new Decision(state, null, lookup);
    }
}