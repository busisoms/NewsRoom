package com.newsroom.webhook;

import com.newsroom.config.Config;
import io.javalin.Javalin;

import java.util.Objects;

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
                WebhookMessage payload = getPayload(ctx.body());
                log.info("Received message: {}", payload);
            } catch(Exception e){
                ctx.status(400).result("Invalid request body format");
                log.warn("Failed to process payload body: {}", e);
            }
        });
    }


    private WebhookMessage getPayload(String body){
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);

            JsonNode messageNode = root.path("entry").get(0)
                    .path("changes").get(0)
                    .path("value")
                    .path("messages").get(0);

            String fromNumber = messageNode.path("from").asText();
            String textBody = messageNode.path("text").path("body").asText();

            return new WebhookMessage(fromNumber, textBody);

        } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
    }
}
