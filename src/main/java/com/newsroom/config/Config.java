package com.newsroom.config;

/**
 * All server config comes from env vars, never hardcoded, so the app never has
 * to change to move between local dev, ngrok, and an eventual real deployment.
 *
 * @param port HTTP port the Javalin app listens on
 * @param metaAccessToken bearer token used to call the Meta WhatsApp Cloud API
 * @param metaPhoneNumberId the WhatsApp Business phone number ID messages are sent from
 * @param activeMqBrokerUrl connection URL for the ActiveMQ broker
 * @param activeMqBrokerQueue name of the queue the webhook publishes inbound messages to
 * @param webhookVerifyToken token Meta sends back during the GET /webhook verify handshake
 * @param metaAppSecret app secret used to verify the HMAC-SHA256 signature on inbound webhooks
 * @param sessionTimeoutMinutes how long a user's conversation session stays alive without activity
 */
public record Config(
        int port,
        String metaAccessToken,
        String metaPhoneNumberId,
        String activeMqBrokerUrl,
        String activeMqBrokerQueue,
        String webhookVerifyToken,
        String metaAppSecret,
        int sessionTimeoutMinutes
) {

    /**
     * Reads all config from environment variables, applying defaults where given.
     * Throws {@link IllegalArgumentException} naming any required variable that is
     * missing, so the app fails at startup instead of later with a null value.
     */
    public static Config fromEnv() {
        String port = env("PORT", "7000");
        String accessToken = env("META_ACCESS_TOKEN", null);
        String phoneNumberId = env("META_PHONE_NUMBER_ID", null);
        String broker = env("ACTIVEMQ_BROKER_URL", "tcp://localhost:61616");
        String queue = env("ACTIVEMQ_BROKER_QUEUE", "inbound-message-queue");
        String webhookToken = env("WEBHOOK_VERIFY_TOKEN", null);
        String metaAppSecret = env("META_APP_SECRET", null);
        String timeout = env("SESSION_TIMEOUT_MINUTES", "30");

        String[] keys = {
                "PORT", "META_ACCESS_TOKEN",
                "META_PHONE_NUMBER_ID", "ACTIVEMQ_BROKER_URL",
                "ACTIVEMQ_BROKER_QUEUE", "WEBHOOK_VERIFY_TOKEN",
                "META_APP_SECRET", "SESSION_TIMEOUT_MINUTES"
        };

        String[] values = {
                port, accessToken, phoneNumberId,
                broker, queue, webhookToken,
                metaAppSecret, timeout
        };

        String missing = missingKeys(keys, values);

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required config: " + missing);
        }

        return new Config(
                Integer.parseInt(port), accessToken,
                phoneNumberId, broker,
                queue, webhookToken,
                metaAppSecret, Integer.parseInt(timeout)
        );
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value != null ? value : fallback;
    }

    private static String missingKeys(String[] keys, String[] values) {
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (values[i] == null) {
                if (!missing.isEmpty()) missing.append(", ");
                missing.append(keys[i]);
            }
        }
        return missing.toString();
    }
}
