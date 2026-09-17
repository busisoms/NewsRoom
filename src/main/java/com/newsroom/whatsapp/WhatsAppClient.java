package com.newsroom.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsroom.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;


/**
 * Sends outbound messages to a user via Meta's WhatsApp Cloud API.
 */
public class WhatsAppClient {
    private final Config config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final Logger log = LoggerFactory
            .getLogger(WhatsAppClient.class);

    /**
     * @param config supplies the Meta access token and phone number ID to send from
     */
    public WhatsAppClient(Config config) {
        this.config = config;
    }

    private void post(String payload){
        String url = "https://graph.facebook.com/v25.0/" +
                config.metaPhoneNumberId() + "/messages";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.metaAccessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("WhatsApp send failed: {} {}", status, response.body());
                return;
            }

            log.info("Message sent with status code: {}", status);
            log.info("Response Body: {}", response.body());

        } catch (IOException | InterruptedException e) {
            log.warn("Failed to send request to WhatsApp", e);
        }
    }

    /**
     * Sends a plain-text message.
     *
     * @param to recipient's phone number
     * @param text message body
     */
    public void sendText(String to, String text){
        ObjectNode payloadNode = mapper.createObjectNode();
        payloadNode.put("messaging_product", "whatsapp");
        payloadNode.put("to", to);
        payloadNode.put("type", "text");

        ObjectNode bodyNode = mapper.createObjectNode();
        bodyNode.put("body", text);

        payloadNode.set("text", bodyNode);

        try {
            String payload = mapper.writeValueAsString(payloadNode);

            post(payload);

        } catch (JsonProcessingException e) {
            log.warn("Failed to process payload body", e);
        }
    }

    /**
     * Sends an interactive message with up to 3 reply buttons.
     *
     * @param to recipient's phone number
     * @param text message body shown above the buttons
     * @param buttonList 1-3 reply buttons; anything outside that range is rejected without sending
     */
    public void sendButtons(String to, String text, List<Button> buttonList){
        if (buttonList.isEmpty() || buttonList.size() > 3){
            log.warn("expected 3 buttons but got {}", buttonList.size());
            return;
        }

        ObjectNode payloadNode = mapper.createObjectNode();
        payloadNode.put("messaging_product", "whatsapp");
        payloadNode.put("to", to);
        payloadNode.put("type", "interactive");

        ObjectNode interactiveNode = mapper.createObjectNode();
        interactiveNode.put("type", "button");

        ObjectNode bodyNode = mapper.createObjectNode();
        bodyNode.put("text", text);
        interactiveNode.set("body", bodyNode);

        ArrayNode buttonNodeList = mapper.createArrayNode();
        for (Button button : buttonList) {
            ObjectNode replyNode = mapper.createObjectNode();
            replyNode.put("id", button.id());
            replyNode.put("title", button.title());

            ObjectNode buttonNode = mapper.createObjectNode();
            buttonNode.put("type", "reply");
            buttonNode.set("reply", replyNode);

            buttonNodeList.add(buttonNode);
        }

        ObjectNode actionNode = mapper.createObjectNode();
        actionNode.set("buttons", buttonNodeList);
        interactiveNode.set("action", actionNode);
        payloadNode.set("interactive", interactiveNode);

        try {
            String payload = mapper.writeValueAsString(payloadNode);

            post(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to process interactive buttons payload body", e);
        }

    }
}
