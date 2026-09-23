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
        assertNull(decision.lookup());
    }

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {"TEXT", "UNSUPPORTED"})
    void awaitingOptionResendsTheMenuForNonButtonMessages(MessageType type) {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, messageOf(type));

        assertEquals(ConversationState.AWAITING_OPTION, decision.nextState());
        assertEquals(MENU, decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void awaitingOptionResendsTheMenuForAnUnrecognisedButtonId() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_garbage"));

        assertEquals(ConversationState.AWAITING_OPTION, decision.nextState());
        assertEquals(MENU, decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void weatherButtonMovesToAwaitingCityAndAsksWhichCity() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_weather"));

        assertEquals(ConversationState.AWAITING_CITY, decision.nextState());
        assertEquals(new Reply.Text("Which city?"), decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void newsButtonMovesToAwaitingTopicAndAsksWhatTopic() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_news"));

        assertEquals(ConversationState.AWAITING_TOPIC, decision.nextState());
        assertEquals(new Reply.Text("What topic?"), decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void sportsButtonMovesToAwaitingLeagueAndAsksWhichLeague() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_OPTION, buttonMessage("menu_sports"));

        assertEquals(ConversationState.AWAITING_LEAGUE, decision.nextState());
        Reply.Buttons league = assertInstanceOf(Reply.Buttons.class, decision.reply());
        assertEquals("Which league?", league.body());
        assertNull(decision.lookup());
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

    @Test
    void awaitingCityWithTextProducesAWeatherLookupAndReturnsToNone() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_CITY, textMessage("Cape Town"));

        assertEquals(ConversationState.NONE, decision.nextState());
        assertNull(decision.reply());
        Lookup.Weather weather = assertInstanceOf(Lookup.Weather.class, decision.lookup());
        assertEquals("Cape Town", weather.city());
    }

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {"BUTTON", "UNSUPPORTED"})
    void awaitingCityRePromptsForNonTextMessages(MessageType type) {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_CITY, messageOf(type));

        assertEquals(ConversationState.AWAITING_CITY, decision.nextState());
        assertEquals(new Reply.Text("Which city?"), decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void awaitingCityWithBlankTextRePromptsInsteadOfALookup() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_CITY, textMessage("   "));

        assertEquals(ConversationState.AWAITING_CITY, decision.nextState());
        assertEquals(new Reply.Text("Which city?"), decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void awaitingTopicWithTextProducesANewsLookupAndReturnsToNone() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_TOPIC, textMessage("elections"));

        assertEquals(ConversationState.NONE, decision.nextState());
        assertNull(decision.reply());
        Lookup.News news = assertInstanceOf(Lookup.News.class, decision.lookup());
        assertEquals("elections", news.topic());
    }

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {"BUTTON", "UNSUPPORTED"})
    void awaitingTopicRePromptsForNonTextMessages(MessageType type) {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_TOPIC, messageOf(type));

        assertEquals(ConversationState.AWAITING_TOPIC, decision.nextState());
        assertEquals(new Reply.Text("What topic?"), decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void awaitingTopicWithBlankTextRePromptsInsteadOfALookup() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_TOPIC, textMessage("   "));

        assertEquals(ConversationState.AWAITING_TOPIC, decision.nextState());
        assertEquals(new Reply.Text("What topic?"), decision.reply());
        assertNull(decision.lookup());
    }

    @Test
    void awaitingLeagueWithARecognisedButtonProducesASportsLookupAndReturnsToNone() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_LEAGUE, buttonMessage("league_pl"));

        assertEquals(ConversationState.NONE, decision.nextState());
        assertNull(decision.reply());
        Lookup.Sports sports = assertInstanceOf(Lookup.Sports.class, decision.lookup());
        assertEquals("PL", sports.competitionCode());
    }

    @Test
    void laLigaButtonMapsToTheFootballDataPdCode() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_LEAGUE, buttonMessage("league_pd"));

        Lookup.Sports sports = assertInstanceOf(Lookup.Sports.class, decision.lookup());
        assertEquals("PD", sports.competitionCode());
    }

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {"TEXT", "UNSUPPORTED"})
    void awaitingLeagueRePromptsForNonButtonMessages(MessageType type) {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_LEAGUE, messageOf(type));

        assertEquals(ConversationState.AWAITING_LEAGUE, decision.nextState());
        Reply.Buttons league = assertInstanceOf(Reply.Buttons.class, decision.reply());
        assertEquals("Which league?", league.body());
        assertNull(decision.lookup());
    }

    @Test
    void awaitingLeagueRePromptsForAnUnrecognisedButtonId() {
        Decision decision = ConversationEngine.decide(ConversationState.AWAITING_LEAGUE, buttonMessage("league_garbage"));

        assertEquals(ConversationState.AWAITING_LEAGUE, decision.nextState());
        Reply.Buttons league = assertInstanceOf(Reply.Buttons.class, decision.reply());
        assertEquals("Which league?", league.body());
        assertNull(decision.lookup());
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
