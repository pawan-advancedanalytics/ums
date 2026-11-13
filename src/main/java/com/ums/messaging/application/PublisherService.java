package com.ums.messaging.application;

import com.ums.messaging.contract.EventMessage;
import com.ums.messaging.error.PublishException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublisherService {
    private final RabbitTemplate template;
    private final Duration confirmTimeout;

    public PublisherService(RabbitTemplate template,
            @Value("${ums.publisher.confirm-timeout:5s}") Duration confirmTimeout) {
        this.template = template;
        this.confirmTimeout = confirmTimeout;
    }

    public void publish(EventMessage<?> event) {
        // Unique per attempt, even when a caller retries the same eventId.
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            template.convertAndSend("ums.events", event.eventType(), event, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                message.getMessageProperties().setMessageId(event.eventId().toString());
                message.getMessageProperties().setCorrelationId(event.correlationId());
                return message;
            }, correlation);
            var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new PublishException("Broker rejected or could not route the event");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublishException("Publish interrupted; delivery outcome is uncertain", ex);
        } catch (PublishException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PublishException("Publish failed; delivery outcome may be uncertain", ex);
        }
    }
}
