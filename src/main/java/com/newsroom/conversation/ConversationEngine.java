package com.newsroom.conversation;

import com.newsroom.session.ConversationState;
import com.newsroom.webhook.MessageType;
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
            case AWAITING_OPTION -> awaitingOptionState(message);
            case AWAITING_CITY, AWAITING_TOPIC, AWAITING_LEAGUE -> Decision.noReply(current);
        };
    }

    private static Decision noneState() {
        Reply reply = new Reply.Buttons("Welcome to NewsRoom bot, how can I help you today?",
                List.of(new Button("menu_weather", "Weather"),
                        new Button("menu_sports", "Sports"),
                        new Button("menu_news", "News")));

        return Decision.replyWith(ConversationState.AWAITING_OPTION, reply);
    }

    private static Decision awaitingOptionState(WebhookMessage message){
        MessageType type = message.type();

        if (type == MessageType.TEXT || type == MessageType.UNSUPPORTED){
            return noneState();
        }

        String button = message.buttonId();
        if (button.equals("menu_weather")){
            return Decision.replyWith(ConversationState.AWAITING_CITY,
                    new Reply.Text("Which city?"));
        }
        else if (button.equals("menu_news")) {
            return Decision.replyWith(ConversationState.AWAITING_TOPIC,
                    new Reply.Text("What topic?"));
        } else if (button.equals("menu_sports")) {
            List<Button> leagueButtons = List.of(new Button("league_pl", "Premier League"),
                    new Button("league_cl", "Champions League"),
                    new Button("league_ll", "La Liga"));

            return Decision.replyWith(ConversationState.AWAITING_LEAGUE,
                    new Reply.Buttons("Which league?", leagueButtons));
        }

        return noneState();
    }
}
