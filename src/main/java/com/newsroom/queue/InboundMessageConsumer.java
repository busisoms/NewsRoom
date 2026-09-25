package com.newsroom.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsroom.config.Config;
import com.newsroom.conversation.*;
import com.newsroom.session.*;
import com.newsroom.weather.CurrentWeather;
import com.newsroom.weather.WeatherClient;
import com.newsroom.webhook.WebhookMessage;
import com.newsroom.whatsapp.WhatsAppClient;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;


/**
 * Reads inbound messages off the ActiveMQ queue, runs them through the
 * {@link ConversationEngine}, and sends the reply via {@link WhatsAppClient}.
 *
 * <p>The only reader of the queue and the only writer to {@link SessionStore}.
 * JMS delivers to the listener on a single thread, so messages are handled
 * in order and the store needs no extra locking.
 *
 * <p>A message is acknowledged only after it's fully handled. On failure the
 * session is recovered, so the broker redelivers it; after 6 failed attempts
 * ActiveMQ moves it to {@code ActiveMQ.DLQ}.
 */
public class InboundMessageConsumer implements AutoCloseable{
    private final Config config;
    private final WhatsAppClient client;
    private final WeatherClient weatherClient;
    private final SessionStore store;
    private final DedupeStore dedupe;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JMSContext context;
    private final Logger log = LoggerFactory
            .getLogger(InboundMessageConsumer.class);

    /**
     * Creates a consumer. Nothing connects until {@link #start()}.
     *
     * @param config supplies the broker URL and queue name
     * @param client sends replies back to WhatsApp
     * @param weatherClient
     * @param store holds each caller's conversation state
     * @param dedupe tracks which wamids have already been processed
     */
    public InboundMessageConsumer(Config config, WhatsAppClient client,
                                  WeatherClient weatherClient,
                                  SessionStore store, DedupeStore dedupe) {
        this.config = config;
        this.client = client;
        this.weatherClient = weatherClient;
        this.store = store;
        this.dedupe = dedupe;
    }

    /**
     * Opens a JMS connection and starts listening on the configured queue.
     * Call once; the listener keeps running on its own thread until the
     * process exits.
     *
     * <p>Uses the three-argument {@code createContext}: in ActiveMQ 5.18 the
     * one-argument {@code createContext(sessionMode)} on the factory throws
     * {@link UnsupportedOperationException}.
     */
    public void start() {
        ConnectionFactory factory =
                new ActiveMQConnectionFactory(config
                        .activeMqBrokerUrl());

        context = factory.createContext(null,
                null, JMSContext.CLIENT_ACKNOWLEDGE);

        context.setExceptionListener(e ->
                log.error("JMS connection error", e));

        Queue queue = context.createQueue(config.activeMqBrokerQueue());
        context.createConsumer(queue)
                .setMessageListener(this::onMessage);
        context.start();
    }

    /**
     * Handles one queued message: acknowledges on success, recovers on any failure.
     * Recovering is what triggers redelivery; skipping it would let the next
     * successful acknowledge also ack this failed message, losing it.
     */
    private void onMessage(Message m) {
        // Until the body is parsed there's no wamid, so failures fall back to the JMS id
        String id = jmsMessageId(m);
        int delivery = deliveryCount(m);
        try {
            String json = m.getBody(String.class);
            WebhookMessage msg = objectMapper.readValue(json, WebhookMessage.class);
            id = msg.wamId();
            handle(msg, delivery);
            m.acknowledge();
        } catch (Exception e) {
            log.warn("Processing failed for {} delivery={}; will be redelivered", id, delivery, e);
            try {
                context.recover();
            } catch (JMSRuntimeException re) {
                log.error("Could not recover session; message stays unacknowledged until reconnect", re);
            }
        }
    }

    /**
     * Runs one message through the conversation: looks up the caller's state,
     * asks the engine for a decision, sends any reply, then stores the next state.
     *
     * <p>Skips the message entirely if its wamid was already processed; covers
     * both a Meta retry (a second, independent queue message with the same wamid) and
     * an ActiveMQ redelivery of the same message. The wamid is only marked processed
     * once handling finishes without error, so a redelivery that follows a genuine
     * failure still goes through instead of being skipped as a false duplicate.
     *
     * <p>Sends before updating state, so a crash in between means a redelivery
     * resends the reply (harmless) rather than skipping it.
     *
     * <p>Logs one line per message with the wamid, state transition, reply kind,
     * lookup kind, and delivery count. Never the message text or phone number.
     *
     * @param message the caller's inbound message
     * @param delivery how many times the broker has delivered this message (1 on first
     *                 delivery, 0 if unknown)
     */
    public void handle(WebhookMessage message, int delivery){
        if (dedupe.isDuplicate(message.wamId())) {
            log.info("Skipped duplicate {} delivery={}", message.wamId(), delivery);
            return;
        }

        String user = message.from();
        ConversationState currentState = store.onMessage(user);
        Decision decision = ConversationEngine.decide(currentState, message);
        if (decision.reply() != null) {
            send(user, decision.reply());
        }

        if (decision.lookup() != null){
            send(user, resolve(decision.lookup()));
        }
        store.updateState(user, decision.nextState());
        dedupe.markProcessed(message.wamId());

        log.info("Processed {} {} -> {} reply={} lookup={} delivery={}", message.wamId(),
                currentState, decision.nextState(), replyKind(decision.reply()), lookupKind(decision.lookup()), delivery);
    }

    private static String replyKind(Reply reply) {
        if (reply == null) {
            return "none";
        }
        return switch (reply) {
            case Reply.Text text -> "TEXT";
            case Reply.Buttons buttons -> "BUTTONS";
        };
    }

    private static String lookupKind(Lookup lookup) {
        if (lookup == null) {
            return "none";
        }
        return switch (lookup) {
            case Lookup.Weather weather -> "WEATHER";
            case Lookup.Sports sports -> "SPORTS";
            case Lookup.News news -> "NEWS";
        };
    }

    private static String jmsMessageId(Message m) {
        try {
            return m.getJMSMessageID();
        } catch (JMSException e) {
            return "unknown";
        }
    }

    private static int deliveryCount(Message m) {
        try {
            return m.getIntProperty("JMSXDeliveryCount");
        } catch (JMSException | NumberFormatException e) {
            return 0;
        }
    }

    private void send(String to, Reply reply) {
        switch (reply) {
            case Reply.Text text -> client.sendText(to, text.body());
            case Reply.Buttons buttons -> client.sendButtons(to, buttons.body(), buttons.buttons());
        }
    }

    private Reply resolve(Lookup lookup) {
        return switch (lookup) {
            case Lookup.Weather weather -> resolveWeather(weather);
            case Lookup.Sports sports ->
                    new Reply.Text("Sports updates aren't ready yet. Try Weather or News.");
            case Lookup.News news ->
                    new Reply.Text("News updates aren't ready yet. Try Weather or Sports.");
        };
    }

    private Reply resolveWeather(Lookup.Weather lookup) {
        try {
            CurrentWeather weather = weatherClient.current(lookup.city());
            return ReplyFormatter.weather(weather);
        } catch (LookupException e) {
            log.warn("Weather lookup for '{}' failed: {}", lookup.city(), e.getMessage(), e);
            return new Reply.Text(e.fallbackMessage());
        }
    }


    /**
     * Stops listening and closes the connection. Safe to call if
     * {@link #start()} never ran.
     */
    @Override
    public void close(){
        if (context != null) context.close();
    }
}
