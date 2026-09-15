package com.newsroom.webhook;

import com.newsroom.config.Config;
import io.javalin.Javalin;

import java.util.Objects;

public class WebhookController {

    private final Config config;

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
}
