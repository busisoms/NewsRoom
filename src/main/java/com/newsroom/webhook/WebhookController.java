package com.newsroom.webhook;

import com.newsroom.config.Config;
import io.javalin.Javalin;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;


public class WebhookController {

    private final Config config;
    private final Logger log = LoggerFactory
            .getLogger(WebhookController.class);

    public WebhookController(Config config) {
        this.config = config;
    }

    public void register(Javalin app) {
        app.get("/webhook", ctx -> {
            String mode = ctx.queryParam("hub.mode");
            String token = ctx.queryParam("hub.verify_token");
            String challenge = ctx.queryParam("hub.challenge");

            if ("subscribe".equals(mode) && Objects.equals(config.webhookVerifyToken(), token)) {
                ctx.status(200).result(challenge);
            } else {
                ctx.status(403).result("Forbidden");
            }
        });
    }

    public void registerMessageReceiver(Javalin app){
        app.post("/webhook", ctx -> {
            try {
                // Meta also posts status updates and other fields here, which carry no message.
                // Always ack with 200, otherwise Meta retries and may disable the webhook.
                getPayload(ctx.body()).ifPresentOrElse(
                        payload -> log.info("Received message: {}", payload),
                        () -> log.debug("Ignoring webhook event without a message"));
                ctx.status(200);
            } catch(Exception e){
                ctx.status(400).result("Invalid request body format");
                log.warn("Failed to process payload body: {}", e);
            }
        });
    }


    private Optional<WebhookMessage> getPayload(String body){
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);

            // path() never returns null, so events without entry/changes/messages end up as a missing node
            JsonNode messageNode = root.path("entry").path(0)
                    .path("changes").path(0)
                    .path("value")
                    .path("messages").path(0);

            if (messageNode.isMissingNode()) {
                return Optional.empty();
            }

            String fromNumber = messageNode.path("from").asText();
            String textBody = messageNode.path("text").path("body").asText();

            return Optional.of(new WebhookMessage(fromNumber, textBody));

        } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
    }
}
