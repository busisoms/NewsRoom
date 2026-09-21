package com.newsroom.webhook;

/**
 * The kind of inbound WhatsApp message, as far as the bot can act on it.
 */
public enum MessageType {
    TEXT,
    BUTTON,
    UNSUPPORTED
}
