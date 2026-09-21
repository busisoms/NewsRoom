package com.newsroom.conversation;

import com.newsroom.session.ConversationState;
import com.newsroom.webhook.WebhookMessage;
import com.newsroom.whatsapp.Button;

import java.util.List;

/**
 * Decides how the bot responds to an inbound message,
 * given where the caller is in the conversation.
 */
public class ConversationEngine {

    /**
     * Works out the caller's next state and what to send back.
     *
     * @param current the caller's state before this message
     * @param message the caller's inbound message
     * @return the state to store next, and the reply to send (if any)
     */
    public static Decision decide(ConversationState current, WebhookMessage message){
        return switch (current){
            case NONE -> noneState();
            case AWAITING_OPTION, AWAITING_DETAILS -> Decision.noReply(current);
        };
    }

    private static Decision noneState() {
        Reply reply = new Reply.Buttons("Welcome to NewsRoom bot, how can I help you today?",
                List.of(new Button("menu_weather", "Weather"),
                        new Button("menu_sports", "Sports"),
                        new Button("menu_news", "News")));

        return Decision.replyWith(ConversationState.AWAITING_OPTION, reply);
    }
}
