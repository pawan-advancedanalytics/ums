package com.ums.messaging.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.ums.messaging.contract.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Demo only: logs metadata and performs no external business side effects. */
@Component
public class DemoConsumers {
    private static final Logger log = LoggerFactory.getLogger(DemoConsumers.class);

    @RabbitListener(id = "audit", queues = "ums.audit")
    public void audit(EventMessage<JsonNode> event) {
        log.info("Demo audit eventId={} correlationId={}", event.eventId(), event.correlationId());
    }

    @RabbitListener(id = "notification", queues = "ums.notification")
    public void notification(EventMessage<JsonNode> event) {
        log.info("Demo notification eventId={} correlationId={}", event.eventId(), event.correlationId());
    }
}
