package com.ums.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ums.messaging.broker.DemoConsumers;
import com.ums.messaging.contract.EventMessage;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class MessagingIntegrationTest {
    @Container static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management");
    @DynamicPropertySource static void broker(DynamicPropertyRegistry properties) {
        properties.add("spring.rabbitmq.host", rabbit::getHost);
        properties.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        properties.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        properties.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }
    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate template;
    @Autowired RabbitAdmin admin;
    @Autowired ObjectMapper mapper;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @MockitoSpyBean DemoConsumers consumers;

    @BeforeEach void clean() {
        for (String queue : new String[]{"ums.audit", "ums.notification", "ums.audit.dlq", "ums.notification.dlq"}) {
            admin.purgeQueue(queue);
        }
    }
    @AfterEach void stop() { listeners.stop(); reset(consumers); }

    EventMessage<JsonNode> event(String type) {
        return new EventMessage<>(UUID.randomUUID(), type, "1.0", "integration", Instant.now(), "request-1",
                mapper.createObjectNode().put("userId", "101"));
    }

    @Test void restPublishFansOutPersistentEnvelopeToIndependentQueues() throws Exception {
        var event = event("user.created");
        var response = http.postForEntity("/api/v1/messages/publish", event, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        for (String queue : new String[]{"ums.audit", "ums.notification"}) {
            Message message = template.receive(queue, 5000);
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                    .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            assertThat(mapper.readTree(message.getBody())).isEqualTo(mapper.valueToTree(event));
        }
    }

    @Test void unknownRoutingKeyFailsExplicitly() {
        assertThat(http.postForEntity("/api/v1/messages/publish", event("unknown.event"), String.class)
                .getStatusCode().value()).isEqualTo(503);
    }

    @Test void bothDemoListenersProcessAndAcknowledge() {
        listeners.start();
        var event = event("user.created");
        assertThat(http.postForEntity("/api/v1/messages/publish", event, String.class).getStatusCode().value()).isEqualTo(202);
        await().untilAsserted(() -> {
            verify(consumers).audit(argThat(e -> e.eventId().equals(event.eventId())));
            verify(consumers).notification(argThat(e -> e.eventId().equals(event.eventId())));
            assertThat(admin.getQueueInfo("ums.audit").getMessageCount()).isZero();
            assertThat(admin.getQueueInfo("ums.notification").getMessageCount()).isZero();
        });
    }

    @Test void processingFailureRetriesThreeTimesThenDeadLettersOnlyFailedSubscriber() {
        var attempts = new AtomicInteger();
        doAnswer(invocation -> { attempts.incrementAndGet(); throw new IllegalStateException("sample failure"); })
                .when(consumers).audit(any());
        listeners.start();
        var event = event("user.created");
        assertThat(http.postForEntity("/api/v1/messages/publish", event, String.class).getStatusCode().value()).isEqualTo(202);
        Message dead = template.receive("ums.audit.dlq", 10000);
        assertThat(dead).isNotNull();
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(dead.getMessageProperties().getXDeathHeader()).isNotEmpty();
        await().untilAsserted(() -> verify(consumers).notification(any()));
        assertThat(template.receive("ums.notification.dlq")).isNull();
    }

    @Test void malformedJsonIsDeadLettered() {
        listeners.start();
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        template.send("", "ums.audit", new Message("{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties));
        assertThat(template.receive("ums.audit.dlq", 10000)).isNotNull();
    }

    @Test void operationalEndpointsAvailable() {
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(200);
    }
}
