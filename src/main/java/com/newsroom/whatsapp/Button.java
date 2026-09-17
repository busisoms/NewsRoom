package com.newsroom.whatsapp;

/**
 * A single reply button in an interactive WhatsApp message.
 *
 * @param id stable identifier returned when the user taps this button
 * @param title button label shown to the user (20 chars max per WhatsApp)
 */
public record Button(
        String id,
        String title) {
}
