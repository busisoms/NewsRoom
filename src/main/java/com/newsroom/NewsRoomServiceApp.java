package com.newsroom;

import com.newsroom.config.Config;
import com.newsroom.queue.DedupeStore;
import com.newsroom.queue.InboundMessageConsumer;
import com.newsroom.queue.InboundMessagePublisher;
import com.newsroom.sports.SportsClient;
import com.newsroom.weather.WeatherClient;
import com.newsroom.webhook.WebhookController;
import com.newsroom.whatsapp.WhatsAppClient;
import com.newsroom.session.SessionStore;
import io.javalin.Javalin;

import java.time.Clock;
import java.time.Duration;

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

        WeatherClient weatherClient = new WeatherClient(
                config.weatherGeocodingUrl(),
                config.weatherForecastUrl(),
                Duration.ofSeconds(config.externalTimeoutSeconds()));

        SportsClient sportsClient = new SportsClient(
                config.footballDataBaseUrl(),
                config.footballDataApiKey(),
                Duration.ofSeconds(config.externalTimeoutSeconds()),
                Clock.systemUTC());

        SessionStore store = new SessionStore(config.sessionTimeoutMinutes());
        DedupeStore dedupe = new DedupeStore(config.dedupeWindowMinutes());
        this.consumer = new InboundMessageConsumer(config, outboundClient,
                weatherClient, sportsClient, store, dedupe);
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
