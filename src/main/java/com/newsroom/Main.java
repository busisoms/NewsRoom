package com.newsroom;

import com.newsroom.config.Config;
import io.javalin.Javalin;

public class Main {

    public static void main(String[] args) {
        Config config = Config.fromEnv();

        Javalin app = Javalin.create();
        app.get("/health", ctx -> ctx.status(200).result("OK"));

        app.start(config.port());
    }
}
