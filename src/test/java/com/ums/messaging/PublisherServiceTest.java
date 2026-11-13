package com.ums.messaging;

import com.ums.messaging.application.PublisherService;
import com.ums.messaging.contract.EventMessage;
import com.ums.messaging.error.PublishException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublisherServiceTest {
    final RabbitTemplate template = mock(RabbitTemplate.class);
    final PublisherService service = new PublisherService(template, Duration.ofMillis(20));
    final EventMessage<?> event = new EventMessage<>(UUID.randomUUID(), "user.created", "1.0",
            "test", Instant.now(), "request-1", Map.of("userId", "101"));

    void reply(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            Message message = new Message(new byte[0], new MessageProperties());
            invocation.getArgument(3, MessagePostProcessor.class).postProcessMessage(message);
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(event.correlationId());
            CorrelationData data = invocation.getArgument(4);
            if (returned) data.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", "ums.events", "unknown"));
            data.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(template).convertAndSend(eq("ums.events"), eq("user.created"), eq(event),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test void acceptsBrokerAck() { reply(true, false); assertThatCode(() -> service.publish(event)).doesNotThrowAnyException(); }
    @Test void rejectsNack() { reply(false, false); assertThatThrownBy(() -> service.publish(event)).isInstanceOf(PublishException.class); }
    @Test void rejectsReturnedEvenWithAck() { reply(true, true); assertThatThrownBy(() -> service.publish(event)).isInstanceOf(PublishException.class); }
    @Test void timesOut() { assertThatThrownBy(() -> service.publish(event)).isInstanceOf(PublishException.class).hasMessageContaining("uncertain"); }
    @Test void translatesConnectionFailure() {
        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("offline")))
                .when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThatThrownBy(() -> service.publish(event)).isInstanceOf(PublishException.class);
    }
}
