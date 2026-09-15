package com.newsroom.webhook;

public record WebhookMessage(
        String from,
        String text) {
}
