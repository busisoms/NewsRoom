package com.newsroom.webhook;

/**
 * A single inbound WhatsApp text message parsed from Meta's webhook payload.
 *
 * @param from sender's phone number
 * @param text message body
 */
public record WebhookMessage(
        String from,
        String text) {
}
