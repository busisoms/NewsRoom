package com.newsroom.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsroom.config.Config;
import com.newsroom.conversation.*;
import com.newsroom.session.*;
import com.newsroom.sports.SportsClient;
import com.newsroom.sports.SportsResult;
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
    private final SportsClient sportsClient;
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
     * @param weatherClient fetches current conditions for a {@link Lookup.Weather}
     * @param sportsClient fetches recent results and upcoming fixtures for a {@link Lookup.Sports}
     * @param store holds each caller's conversation state
     * @param dedupe tracks which wamids have already been processed
     */
    public InboundMessageConsumer(Config config, WhatsAppClient client,
                                  WeatherClient weatherClient, SportsClient sportsClient,
                                  SessionStore store, DedupeStore dedupe) {
        this.config = config;
        this.client = client;
        this.weatherClient = weatherClient;
        this.sportsClient = sportsClient;
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
     * <p>A {@link Lookup} on the decision is resolved and its result sent right after
     * any immediate reply. A failed lookup sends the {@link LookupException}'s fallback
     * message and still counts as handled, so it isn't redelivered and retried.
     *
     * <p>Logs one line per message with the wamid, state transition, reply kind,
     * lookup kind and outcome, and delivery count. Never the message text, the
     * looked-up city or topic, or the phone number.
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

        String outcome = "none";
        if (decision.lookup() != null){
            LookupResult result = resolve(decision.lookup());
            send(user, result.reply());
            outcome = result.outcome();
        }
        store.updateState(user, decision.nextState());
        dedupe.markProcessed(message.wamId());

        log.info("Processed {} {} -> {} reply={} lookup={} outcome={} delivery={}", message.wamId(),
                currentState, decision.nextState(), replyKind(decision.reply()), lookupKind(decision.lookup()),
                outcome, delivery);
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

    /**
     * Runs a lookup and turns it into a reply. Never throws {@link LookupException};
     * any other exception escapes so the message is redelivered.
     */
    private LookupResult resolve(Lookup lookup) {
        return switch (lookup) {
            case Lookup.Weather weather -> resolveWeather(weather);
            case Lookup.Sports sports -> resolveSports(sports);
            case Lookup.News news -> new LookupResult(
                    new Reply.Text("News updates aren't ready yet. Try Weather."), "not_built");
        };
    }

    /**
     * A not-found city is an ordinary outcome (usually a typo), so it's logged
     * quietly. Only an unavailable service is a warning with the stack trace.
     * The exception message is the client's technical detail, never the city.
     */
    private LookupResult resolveWeather(Lookup.Weather lookup) {
        try {
            CurrentWeather weather = weatherClient.current(lookup.city());
            return new LookupResult(ReplyFormatter.weather(weather), "ok");
        } catch (LookupException e) {
            if (e.reason() == LookupException.Reason.NOT_FOUND) {
                log.info("Weather lookup found nothing: {}", e.getMessage());
            } else {
                log.warn("Weather lookup failed: {}", e.getMessage(), e);
            }
            return new LookupResult(new Reply.Text(e.fallbackMessage()), e.reason().name());
        }
    }

    /**
     * A not-found competition (no matches in the window around today) is an ordinary outcome, so it's
     * logged quietly. Only an unavailable service is a warning with the stack trace.
     * The exception message is the client's technical detail, never the competition code.
     */
    private LookupResult resolveSports(Lookup.Sports lookup) {
        try {
            SportsResult result = sportsClient.matches(lookup.competitionCode());
            return new LookupResult(ReplyFormatter.sports(result), "ok");
        } catch (LookupException e) {
            if (e.reason() == LookupException.Reason.NOT_FOUND) {
                log.info("Sports lookup found nothing: {}", e.getMessage());
            } else {
                log.warn("Sports lookup failed: {}", e.getMessage(), e);
            }
            return new LookupResult(new Reply.Text(e.fallbackMessage()), e.reason().name());
        }
    }

    /**
     * What a lookup produced: the reply to send, and a short outcome for the log.
     *
     * @param reply the formatted result or the fallback message
     * @param outcome {@code ok}, a {@link LookupException.Reason} name, or {@code not_built}
     */
    private record LookupResult(Reply reply, String outcome) {}


    /**
     * Stops listening and closes the connection. Safe to call if
     * {@link #start()} never ran.
     */
    @Override
    public void close(){
        if (context != null) context.close();
    }
}
