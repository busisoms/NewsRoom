package com.newsroom.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsroom.config.Config;
import com.newsroom.conversation.*;
import com.newsroom.session.*;
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
    private final SessionStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JMSContext context;
    private final Logger log = LoggerFactory
            .getLogger(InboundMessageConsumer.class);

    /**
     * Creates a consumer. Nothing connects until {@link #start()}.
     *
     * @param config supplies the broker URL and queue name
     * @param client sends replies back to WhatsApp
     * @param store holds each caller's conversation state
     */
    public InboundMessageConsumer(Config config, WhatsAppClient client, SessionStore store) {
        this.config = config;
        this.client = client;
        this.store = store;

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
        try {
            String json = m.getBody(String.class);
            WebhookMessage msg = objectMapper.readValue(json, WebhookMessage.class);
            handle(msg);
            m.acknowledge();
        } catch (Exception e) {
            log.warn("Processing failed for message; will be redelivered", e);
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
     * <p>Sends before updating state, so a crash in between means a redelivery
     * resends the reply (harmless) rather than skipping it.
     *
     * @param message the caller's inbound message
     */
    public void handle(WebhookMessage message){
        String user = message.from();
        ConversationState currentState = store.onMessage(user);
        Decision decision = ConversationEngine.decide(currentState, message);
        if (decision.reply() != null) {
            send(user, decision.reply());
        }
        store.updateState(user, decision.nextState());
    }

    private void send(String to, Reply reply) {
        switch (reply) {
            case Reply.Text text -> client.sendText(to, text.body());
            case Reply.Buttons buttons -> client.sendButtons(to, buttons.body(), buttons.buttons());
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
