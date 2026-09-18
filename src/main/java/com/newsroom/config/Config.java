package com.newsroom.config;

/**
 * All server config comes from env vars, never hardcoded, so the app never has
 * to change to move between local dev, ngrok, and an eventual real deployment.
 */
public record Config(
        int port,
        String metaAccessToken,
        String metaPhoneNumberId,
        String activeMqBrokerUrl,
        String webhookVerifyToken,
        int sessionTimeoutMinutes
) {

    public static Config fromEnv() {
        return new Config(
                Integer.parseInt(env("PORT", "7000")),
                env("META_ACCESS_TOKEN", null),
                env("META_PHONE_NUMBER_ID", null),
                env("ACTIVEMQ_BROKER_URL", "tcp://localhost:61616"),
                env("WEBHOOK_VERIFY_TOKEN", null),
                Integer.parseInt(env("SESSION_TIMEOUT_MINUTES", "30"))
        );
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value != null ? value : fallback;
    }
}
