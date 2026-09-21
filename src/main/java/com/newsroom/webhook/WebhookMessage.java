package com.newsroom.webhook;

/**
 * A single inbound WhatsApp message parsed from Meta's webhook payload.
 * Which of {@code text} and {@code buttonId} is set depends on {@code type};
 * the other is {@code null}.
 *
 * @param wamId Meta's unique id (the {@code wamid}) for this inbound message; a Meta retry
 *              carries the same value, so it identifies duplicates. Not the same as {@code buttonId}
 * @param from sender's phone number
 * @param type what kind of message this is
 * @param text message body; only set for {@link MessageType#TEXT}
 * @param buttonId id of the tapped button, as given to {@code Button}; only set for {@link MessageType#BUTTON}
 */
public record WebhookMessage(
        String wamId,
        String from,
        MessageType type,
        String text,
        String buttonId) {
}
