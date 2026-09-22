package com.newsroom.webhook;

import com.newsroom.config.Config;
import com.newsroom.queue.InboundMessagePublisher;
import io.javalin.Javalin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Handles Meta's WhatsApp webhook: the GET verification handshake, and the POST
 * receiver that verifies the signature, parses the inbound message, and puts it
 * on the queue via {@link InboundMessagePublisher}.
 *
 * <p>It never runs conversation logic or calls Meta itself, so the webhook
 * responds promptly; replies are sent by the queue's consumer.
 */
public class WebhookController {

    private final Config config;
    private final InboundMessagePublisher producer;
    private final Logger log = LoggerFactory
            .getLogger(WebhookController.class);

    /**
     * @param config supplies the verify token and app secret
     * @param producer puts parsed messages on the inbound queue
     */
    public WebhookController(Config config, InboundMessagePublisher producer) {
        this.producer = producer;
        this.config = config;
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
     * Registers the POST /webhook receiver. Responds with:
     * <ul>
     *   <li>200 once the message is on the queue, and also for events without a
     *       message (status updates, template updates), which are ignored</li>
     *   <li>401 when the signature is missing or invalid</li>
     *   <li>400 when the body can't be parsed</li>
     *   <li>500 when the message can't be enqueued (e.g. the broker is down),
     *       so Meta retries it later instead of it being lost</li>
     * </ul>
     * Any non-200 makes Meta retry, and sustained failures can get the webhook
     * disabled, so 500 is reserved for failures a retry can fix.
     */
    public void registerMessageReceiver(Javalin app) {
        app.post("/webhook", ctx -> {
            try {
                String body = ctx.body();
                String signature = ctx.header("X-Hub-Signature-256");

                if (!isValidSignature(body, signature)) {
                    log.warn("Invalid webhook signature; rejecting request");
                    ctx.status(401).result("Invalid signature");
                    return;
                }

                Optional<WebhookMessage> payload;
                try {
                    payload = WebhookParser.parse(body);
                } catch (RuntimeException e) {
                    log.warn("Failed to parse payload body", e);
                    ctx.status(400).result("Invalid request body format");
                    return;
                }

                if (payload.isEmpty()) {
                    log.debug("Ignoring webhook event without a message");
                    ctx.status(200);
                    return;
                }

                try {
                    producer.enqueue(payload.get());
                    ctx.status(200);
                } catch (RuntimeException e) {
                    log.error("Failed to enqueue message {}; returning 500 so Meta retries", payload.get().wamId(), e);
                    ctx.status(500);
                }

            } catch (Exception e) {
                ctx.status(400).result("Invalid request body format");
                log.warn("Failed to process payload body", e);
            }
        });
    }

    private boolean isValidSignature(String body, String signatureHeader) {
        if (body == null
                || signatureHeader == null
                || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            String secret = config.metaAppSecret();
            if (secret == null) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));

            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));

            byte[] provided = HexFormat.of()
                    .parseHex(signatureHeader.substring("sha256=".length()));

            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException e) {
            log.warn("Malformed webhook signature header");
            return false;
        } catch (Exception e) {
            log.warn("Failed to validate webhook signature", e);
            return false;
        }
    }
}
