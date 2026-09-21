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
    @EnumSource(value = ConversationState.class, names = {"AWAITING_OPTION", "AWAITING_DETAILS"})
    void waitingStatesStayPutAndSendNothingForAnyMessageType(ConversationState state) {
        for (MessageType type : MessageType.values()) {
            Decision decision = ConversationEngine.decide(state, messageOf(type));

            assertEquals(state, decision.nextState(), "state changed for " + type);
            assertNull(decision.reply(), "reply sent for " + type);
        }
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

    private static WebhookMessage messageOf(MessageType type) {
        return switch (type) {
            case TEXT -> new WebhookMessage("wamid.T1", PHONE, MessageType.TEXT, "hello", null);
            case BUTTON -> new WebhookMessage("wamid.B1", PHONE, MessageType.BUTTON, null, "menu_weather");
            case UNSUPPORTED -> new WebhookMessage("wamid.U1", PHONE, MessageType.UNSUPPORTED, null, null);
        };
    }
}
