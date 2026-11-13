package com.ums.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper mapper) {
        var converter = new Jackson2JsonMessageConverter(mapper, "com.ums.messaging.contract");
        return converter;
    }

    @Bean
    Declarables topology() {
        var events = new TopicExchange("ums.events", true, false);
        var deadLetters = new DirectExchange("ums.dead-letter", true, false);
        var audit = subscriberQueue("ums.audit");
        var notification = subscriberQueue("ums.notification");
        var auditDlq = QueueBuilder.durable("ums.audit.dlq").build();
        var notificationDlq = QueueBuilder.durable("ums.notification.dlq").build();
        return new Declarables(events, deadLetters, audit, notification, auditDlq, notificationDlq,
                BindingBuilder.bind(audit).to(events).with("user.created"),
                BindingBuilder.bind(notification).to(events).with("user.created"),
                BindingBuilder.bind(auditDlq).to(deadLetters).with("ums.audit"),
                BindingBuilder.bind(notificationDlq).to(deadLetters).with("ums.notification"));
    }

    private Queue subscriberQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("ums.dead-letter").deadLetterRoutingKey(name).build();
    }
}
