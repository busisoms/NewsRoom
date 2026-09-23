package com.newsroom.conversation;

import com.newsroom.session.ConversationState;
import com.newsroom.webhook.MessageType;
import com.newsroom.webhook.WebhookMessage;
import com.newsroom.whatsapp.Button;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
            case AWAITING_CITY -> lookupCity(message);
            case AWAITING_TOPIC -> lookupTopic(message);
            case AWAITING_LEAGUE -> lookupLeague(message);
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

        if (type != MessageType.BUTTON){
            return noneState();
        }

        String button = message.buttonId();
        switch (button) {
            case "menu_weather" -> {
                return Decision.replyWith(ConversationState.AWAITING_CITY,
                        new Reply.Text("Which city?"));
            }
            case "menu_news" -> {
                return Decision.replyWith(ConversationState.AWAITING_TOPIC,
                        new Reply.Text("What topic?"));
            }
            case "menu_sports" -> {
                List<Button> leagueButtons = List.of(new Button("league_pl", "Premier League"),
                        new Button("league_cl", "Champions League"),
                        new Button("league_ll", "La Liga"));

                return Decision.replyWith(ConversationState.AWAITING_LEAGUE,
                        new Reply.Buttons("Which league?", leagueButtons));
            }
        }

        return noneState();
    }

    private static Decision lookupCity(WebhookMessage message){
        return lookupFreeText(message, Lookup.Weather::new);
    }

    private static Decision lookupTopic(WebhookMessage message){
        return lookupFreeText(message, Lookup.News::new);
    }

    private static Decision lookupFreeText(WebhookMessage message, Function<String, Lookup> toLookup){
        if (message.type() == MessageType.UNSUPPORTED){
            return noneState();
        }

        String text = (message.text() == null) ? "" : message.text().trim();

        if (text.isBlank()){
            return noneState();
        }

        return Decision.lookUpFor(ConversationState.NONE, toLookup.apply(text));
    }

    private static final Map<String, String> LEAGUE_CODES = Map.of(
            "league_pl", "PL",
            "league_cl", "CL",
            "league_ll", "LL"
    );

    private static Decision lookupLeague(WebhookMessage message){
        if (message.type() != MessageType.BUTTON){
            return noneState();
        }

        String competitionCode = LEAGUE_CODES.get(message.buttonId());

        if (competitionCode == null){
            return noneState();
        }

        return Decision.lookUpFor(ConversationState.NONE,
                new Lookup.Sports(competitionCode));
    }
}
