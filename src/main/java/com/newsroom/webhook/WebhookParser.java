package com.newsroom.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Turns the JSON body of a Meta webhook POST into a {@link WebhookMessage}.
 * Stateless: it only reads the body, and never calls Meta or touches the session.
 */
public class WebhookParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses the first message in a webhook payload. Only the first message is read;
     * batched payloads are not handled.
     *
     * @param body the raw JSON request body
     * @return the parsed message, or empty if the payload has no inbound message
     *         (e.g. a status update for a message we sent)
     * @throws RuntimeException if {@code body} is not valid JSON
     */
    public static Optional<WebhookMessage> parse(String body){
        try {
            JsonNode root = mapper.readTree(body);

            JsonNode messageNode = root.path("entry").path(0)
                    .path("changes").path(0)
                    .path("value")
                    .path("messages").path(0);

            if (messageNode.isMissingNode()) {
                return Optional.empty();
            }

            String fromNumber = messageNode.path("from").textValue();
            String wamId = messageNode.path("id").textValue();

            JsonNode interactiveNode = messageNode.path("interactive");

            if (!interactiveNode.isMissingNode()){
                String buttonId = interactiveNode.path("button_reply")
                        .path("id").textValue();

                return Optional.of(new WebhookMessage(wamId, fromNumber,
                        MessageType.BUTTON, null, buttonId));

            }

            String textBody = messageNode.path("text")
                    .path("body")
                    .textValue();

            if (textBody == null){
                return Optional.of(new WebhookMessage(wamId, fromNumber,
                        MessageType.UNSUPPORTED, null, null));
            }

            return Optional.of(new WebhookMessage(wamId, fromNumber,
                    MessageType.TEXT, textBody, null));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
