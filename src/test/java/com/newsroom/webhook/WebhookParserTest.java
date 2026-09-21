package com.newsroom.webhook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookParserTest {

    @Test
    void textMessageCarriesWamidSenderAndBody() {
        WebhookMessage message = WebhookParser.parse(fixture("text_message.json")).orElseThrow();

        assertEquals(new WebhookMessage(
                "wamid.TEXT0001", "27820000001", MessageType.TEXT, "hello", null), message);
    }

    @Test
    void buttonReplyCarriesButtonIdAndNoText() {
        WebhookMessage message = WebhookParser.parse(fixture("button_reply.json")).orElseThrow();

        assertEquals(new WebhookMessage(
                "wamid.BUTTON0001", "27820000001", MessageType.BUTTON, null, "menu_weather"), message);
    }

    @Test
    void buttonReplyWamidIsTheMessageIdNotTheButtonId() {
        WebhookMessage message = WebhookParser.parse(fixture("button_reply.json")).orElseThrow();

        assertNotEquals(message.buttonId(), message.wamId());
    }

    @Test
    void statusOnlyPayloadHasNoMessage() {
        Optional<WebhookMessage> message = WebhookParser.parse(fixture("status_only.json"));

        assertTrue(message.isEmpty());
    }

    @Test
    void imageMessageIsUnsupportedButKeepsWamidAndSender() {
        WebhookMessage message = WebhookParser.parse(fixture("image_message.json")).orElseThrow();

        assertEquals(new WebhookMessage(
                "wamid.IMAGE0001", "27820000001", MessageType.UNSUPPORTED, null, null), message);
    }

    @Test
    void emptyObjectHasNoMessage() {
        assertTrue(WebhookParser.parse("{}").isEmpty());
    }

    @Test
    void malformedJsonThrows() {
        String body = fixture("malformed.json");

        assertThrows(RuntimeException.class, () -> WebhookParser.parse(body));
    }

    private static String fixture(String name) {
        try (InputStream in = WebhookParserTest.class.getResourceAsStream("/webhook/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read test fixture: " + name, e);
        }
    }
}
