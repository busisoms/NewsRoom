package com.newsroom.webhook;

import com.newsroom.config.Config;
import com.newsroom.session.ConversationState;
import com.newsroom.whatsapp.Button;
import com.newsroom.whatsapp.WhatsAppClient;
import com.newsroom.session.SessionStore;
import io.javalin.Javalin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;


/**
 * Handles Meta's WhatsApp webhook: the GET verification handshake, and the POST
 * receiver that parses inbound messages, walks the caller through the conversation
 * state machine, and replies via {@link WhatsAppClient}.
 */
public class WebhookController {

    private final Config config;
    private final WhatsAppClient client;
    private final SessionStore store;
    private final Logger log = LoggerFactory
            .getLogger(WebhookController.class);

    public WebhookController(Config config, SessionStore store, WhatsAppClient client) {
        this.config = config;
        this.store = store;
        this.client = client;
    }

    /**
     * Registers the GET /webhook verification handshake required by Meta: echoes back
     * {@code hub.challenge} when {@code hub.verify_token} matches
     * {@link Config#webhookVerifyToken()}, otherwise responds 403.
     */
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

    /**
     * Registers the POST /webhook receiver.
     * Always acks with 200 even for events without
     * a message (status updates, template updates)
     * since a non-200 makes Meta retry and can get the webhook disabled.
     */
    public void registerMessageReceiver(Javalin app){
        app.post("/webhook", ctx -> {
            try {
                // Meta also posts status updates and other fields here, which carry no message.
                // Always ack with 200, otherwise Meta retries and may disable the webhook.
                getPayload(ctx.body()).ifPresentOrElse(
                        payload -> {
                            log.info("Received message: {}", payload);
                            handleMessages(payload);
                        },
                        () -> log.debug("Ignoring webhook event without a message"));
                ctx.status(200);
            } catch(Exception e){
                ctx.status(400).result("Invalid request body format");
                log.warn("Failed to process payload body", e);
            }
        });
    }

    /**
     * Advances a caller who is at the start of the conversation ({@code NONE}) into the
     * menu: sends the Weather/Sports/News options and moves them to {@code AWAITING_OPTION}.
     *
     * @param message the caller's inbound message
     */
    public void handleMessages(WebhookMessage message){
        String user = message.from();

        ConversationState currentState = store.onMessage(user);
        if (currentState == ConversationState.NONE) {
            store.updateState(user, ConversationState.AWAITING_OPTION);
            client.sendButtons(user, "Welcome to NewsRoom bot, how can I help you today?",
                    List.of(new Button("menu_weather", "Weather"),
                            new Button("menu_sports", "Sports"),
                            new Button("menu_news", "News")));
        }
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
