package com.newsroom.queue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsroom.config.Config;
import com.newsroom.webhook.WebhookMessage;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;


/**
 * Puts inbound WhatsApp messages on the ActiveMQ queue as persistent JSON
 * text messages, so the webhook can ack Meta without waiting on any reply logic.
 *
 * <p>Holds one connection for the life of the app and opens a short-lived
 * session per message, because sessions aren't thread-safe and Javalin serves
 * requests on several threads.
 */
public class InboundMessagePublisher implements AutoCloseable{
    private final Config config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JMSContext root;


    /**
     * Connects to the broker at {@link Config#activeMqBrokerUrl()}.
     * Throws if the broker is unreachable, so the app fails at startup.
     *
     * @param config supplies the broker URL and queue name
     */
    public InboundMessagePublisher(Config config) {
        this.config = config;
        ConnectionFactory factory =
                new ActiveMQConnectionFactory(config.activeMqBrokerUrl());
        this.root = factory.createContext();
    }

    /**
     * Serialises the message to JSON and sends it to the queue.
     * Failures are thrown, not swallowed, so the webhook can answer 500
     * and Meta retries instead of the message being lost.
     *
     * @param message the parsed inbound message
     * @throws JMSRuntimeException if the broker can't be reached
     */
    public void enqueue(WebhookMessage message){
        String strMessage;
        try {
            strMessage = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e){
            throw new RuntimeException("Failed to serialize message", e);
        }

        try (JMSContext ctx = root.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue queue = ctx.createQueue(config.activeMqBrokerQueue());
            ctx.createProducer()
                    .setDeliveryMode(DeliveryMode.PERSISTENT)
                    .send(queue, strMessage);

        }
    }

    /**
     * Closes the shared connection. Call once, at shutdown.
     */
    @Override
    public void close() {
        root.close();
    }
}
