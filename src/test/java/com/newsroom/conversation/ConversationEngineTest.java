package com.newsroom.conversation;

import com.newsroom.session.ConversationState;
import com.newsroom.webhook.MessageType;
import com.newsroom.webhook.WebhookMessage;
import com.newsroom.whatsapp.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationEngineTest {
    private static final String PHONE = "27820000001";

    private static final Reply.Buttons MENU = new Reply.Buttons(
            "Welcome to NewsRoom bot, how can I help you today?",
            List.of(new Button("menu_weather", "Weather"),
                    new Button("menu_sports", "Sports"),
                    new Button("menu_news", "News")));

    @ParameterizedTest
    @EnumSource(MessageType.class)
    void noneSendsTheMenuAndMovesToAwaitingOptionForAnyMessageType(MessageType type) {
        Decision decision = ConversationEngine.decide(ConversationState.NONE, messageOf(type));

        assertEquals(ConversationState.AWAITING_OPTION, decision.nextState());
        assertEquals(MENU, decision.reply());
    }

    @ParameterizedTest
    @EnumSource(value = ConversationState.class, names = {"AWAITING_CITY", "AWAITING_TOPIC", "AWAITING_LEAGUE"})
    void waitingStatesStayPutAndSendNothingForAnyMessageType(ConversationState state) {
        for (MessageType type : MessageType.values()) {
            Decision decision = ConversationEngine.decide(state, messageOf(type));

            assertEquals(state, decision.nextState(), "state changed for " + type);
            assertNull(decision.reply(), "reply sent for " + type);
        }
    }

    @Test
    void awaitingOptionResendsTheMenuAndStaysPutForUnsupportedMessages() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, messageOf(MessageType.UNSUPPORTED));

        assertEquals(ConversationState.AWAITING_OPTION, decision.nextState());
        assertEquals(MENU, decision.reply());
    }

    @Test
    void awaitingOptionResendsTheMenuForAnUnrecognisedButtonId() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_garbage"));

        assertEquals(ConversationState.AWAITING_OPTION, decision.nextState());
        assertEquals(MENU, decision.reply());
    }

    @Test
    void weatherButtonMovesToAwaitingCityAndAsksWhichCity() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_weather"));

        assertEquals(ConversationState.AWAITING_CITY, decision.nextState());
        assertEquals(new Reply.Text("Which city?"), decision.reply());
    }

    @Test
    void newsButtonMovesToAwaitingTopicAndAsksWhatTopic() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_news"));

        assertEquals(ConversationState.AWAITING_TOPIC, decision.nextState());
        assertEquals(new Reply.Text("What topic?"), decision.reply());
    }

    @Test
    void sportsButtonMovesToAwaitingLeagueAndAsksWhichLeague() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_sports"));

        assertEquals(ConversationState.AWAITING_LEAGUE, decision.nextState());
        Reply.Buttons league = assertInstanceOf(Reply.Buttons.class, decision.reply());
        assertEquals("Which league?", league.body());
    }

    @Test
    void menuRespectsWhatsAppButtonLimits() {
        Decision decision = ConversationEngine.decide(ConversationState.NONE, messageOf(MessageType.TEXT));
        Reply.Buttons menu = assertInstanceOf(Reply.Buttons.class, decision.reply());

        assertTrue(menu.buttons().size() <= 3, "WhatsApp allows at most 3 reply buttons");
        for (Button button : menu.buttons()) {
            assertTrue(button.title().length() <= 20, "Title too long: " + button.title());
        }
    }

    @Test
    void menuButtonIdsAreUnique() {
        Decision decision = ConversationEngine.decide(ConversationState.NONE, messageOf(MessageType.TEXT));
        Reply.Buttons menu = assertInstanceOf(Reply.Buttons.class, decision.reply());

        long distinct = menu.buttons().stream().map(Button::id).distinct().count();

        assertEquals(menu.buttons().size(), distinct);
    }

    @Test
    void leagueMenuRespectsWhatsAppButtonLimits() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_sports"));
        Reply.Buttons league = assertInstanceOf(Reply.Buttons.class, decision.reply());

        assertTrue(league.buttons().size() <= 3, "WhatsApp allows at most 3 reply buttons");
        for (Button button : league.buttons()) {
            assertTrue(button.title().length() <= 20, "Title too long: " + button.title());
        }
    }

    @Test
    void leagueMenuButtonIdsAreUnique() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_sports"));
        Reply.Buttons league = assertInstanceOf(Reply.Buttons.class, decision.reply());

        long distinct = league.buttons().stream().map(Button::id).distinct().count();

        assertEquals(league.buttons().size(), distinct);
    }

    private static WebhookMessage messageOf(MessageType type) {
        return switch (type) {
            case TEXT -> textMessage("hello");
            case BUTTON -> buttonMessage("menu_weather");
            case UNSUPPORTED -> new WebhookMessage("wamid.U1", PHONE, MessageType.UNSUPPORTED, null, null);
        };
    }

    private static WebhookMessage textMessage(String body) {
        return new WebhookMessage("wamid.T1", PHONE, MessageType.TEXT, body, null);
    }

    private static WebhookMessage buttonMessage(String id) {
        return new WebhookMessage("wamid.B1", PHONE, MessageType.BUTTON, null, id);
    }
}
