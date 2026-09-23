package com.newsroom.conversation;

import com.newsroom.session.ConversationState;
import com.newsroom.webhook.MessageType;
import com.newsroom.webhook.WebhookMessage;
import com.newsroom.whatsapp.Button;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Decides how the bot responds to an inbound message,
 * given where the caller is in the conversation.
 */
public class ConversationEngine {

    private static final List<Button> LEAGUE_BUTTONS = List.of(
            new Button("league_pl", "Premier League"),
            new Button("league_cl", "Champions League"),
            new Button("league_pd", "La Liga"));

    private static final Map<String, String> LEAGUE_CODES = Map.of(
            "league_pl", "PL",
            "league_cl", "CL",
            "league_pd", "PD"
    );

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

        return switch (message.buttonId()) {
            case "menu_weather" -> cityPromptState();
            case "menu_news" -> topicPromptState();
            case "menu_sports" -> leaguePromptState();
            default -> noneState();
        };
    }

    private static Decision cityPromptState() {
        return Decision.replyWith(ConversationState.AWAITING_CITY, new Reply.Text("Which city?"));
    }

    private static Decision topicPromptState() {
        return Decision.replyWith(ConversationState.AWAITING_TOPIC, new Reply.Text("What topic?"));
    }

    private static Decision leaguePromptState() {
        return Decision.replyWith(ConversationState.AWAITING_LEAGUE,
                new Reply.Buttons("Which league?", LEAGUE_BUTTONS));
    }

    private static Decision lookupCity(WebhookMessage message){
        return lookupFreeText(message, Lookup.Weather::new, ConversationEngine::cityPromptState);
    }

    private static Decision lookupTopic(WebhookMessage message){
        return lookupFreeText(message, Lookup.News::new, ConversationEngine::topicPromptState);
    }

    private static Decision lookupFreeText(WebhookMessage message, Function<String, Lookup> toLookup,
                                            Supplier<Decision> rePrompt){
        if (message.type() != MessageType.TEXT){
            return rePrompt.get();
        }

        String text = (message.text() == null) ? "" : message.text().trim();

        if (text.isBlank()){
            return rePrompt.get();
        }

        return Decision.lookUpFor(ConversationState.NONE, toLookup.apply(text));
    }

    private static Decision lookupLeague(WebhookMessage message){
        if (message.type() != MessageType.BUTTON){
            return leaguePromptState();
        }

        String competitionCode = LEAGUE_CODES.get(message.buttonId());

        if (competitionCode == null){
            return leaguePromptState();
        }

        return Decision.lookUpFor(ConversationState.NONE, new Lookup.Sports(competitionCode));
    }
}
