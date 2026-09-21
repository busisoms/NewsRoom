package com.newsroom.conversation;

import com.newsroom.whatsapp.Button;
import java.util.List;

/**
 * What the conversation engine wants to send back to the user.
 *
 * <p>Maps 1:1 onto {@code WhatsAppClient.sendText} and
 * {@code WhatsAppClient.sendButtons}.
 */
public sealed interface Reply permits Reply.Text, Reply.Buttons {

    /**
     * A plain text reply.
     *
     * @param body the message body
     */
    record Text(String body) implements Reply {}

    /**
     * A reply with a body and a list of buttons.
     *
     * @param body the message body
     * @param buttons the buttons to show
     */
    record Buttons(String body, List<Button> buttons) implements Reply {}
}