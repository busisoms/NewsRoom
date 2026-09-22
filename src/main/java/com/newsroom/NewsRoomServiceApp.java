package com.newsroom;

import com.newsroom.config.Config;
import com.newsroom.queue.InboundMessageConsumer;
import com.newsroom.queue.InboundMessagePublisher;
import com.newsroom.webhook.WebhookController;
import com.newsroom.whatsapp.WhatsAppClient;
import com.newsroom.session.SessionStore;
import io.javalin.Javalin;

public class NewsRoomServiceApp {
    private final Config config;
    private final Javalin app;
    private final WebhookController controller;
    private final InboundMessageConsumer consumer;
    private final InboundMessagePublisher producer;


    public NewsRoomServiceApp() {
        this.config = Config.fromEnv();
        this.app = Javalin.create();
        this.producer = new InboundMessagePublisher(config);
        this.controller = new WebhookController(config, producer);
        WhatsAppClient outboundClient = new WhatsAppClient(config);
        SessionStore store = new SessionStore(config.sessionTimeoutMinutes());
        this.consumer = new InboundMessageConsumer(config, outboundClient, store);
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
        consumer.start();
        app.start(config.port());
    }

    private void stop(){
        app.stop();
        consumer.close();
        producer.close();
    }

    public static void main(String[] args) {
        NewsRoomServiceApp service = new NewsRoomServiceApp();
        service.health();
        service.register();
        service.registerMessageReceiver();
        service.start();
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
    }
}
