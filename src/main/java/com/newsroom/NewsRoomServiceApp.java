package com.newsroom;

import com.newsroom.config.Config;
import com.newsroom.webhook.WebhookController;
import io.javalin.Javalin;

public class NewsRoomServiceApp {
    private final Config config;
    private final Javalin app;
    private final WebhookController controller;


    public NewsRoomServiceApp() {
        this.config = Config.fromEnv();
        this.app = Javalin.create();
        this.controller = new WebhookController(config);
    }

    private void register(){
        controller.register(app);
    }

    private void registerMessageReceiver(){
        controller.registerMessageReceiver(app);
    }

    private void health(){
        app.get("/health", ctx ->
                ctx.status(200).result("OK"));

    }

    private void start(){
        app.start(config.port());
    }

    public static void main(String[] args) {
        NewsRoomServiceApp service = new NewsRoomServiceApp();
        service.health();
        service.register();
        service.registerMessageReceiver();
        service.start();
    }
}
