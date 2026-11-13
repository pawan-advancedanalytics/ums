package com.ums.messaging.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ums.messaging.application.PublisherService;
import com.ums.messaging.contract.EventMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    private final PublisherService publisher;

    public MessageController(PublisherService publisher) { this.publisher = publisher; }

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Publish an event", description = "202 follows broker confirmation and routing, not consumer completion. Unknown routing keys return 503. A timeout has an uncertain delivery outcome.")
    public PublishResponse publish(@Valid @RequestBody EventMessage<JsonNode> event) {
        publisher.publish(event);
        return new PublishResponse(event.eventId(), "BROKER_CONFIRMED");
    }

    public record PublishResponse(UUID eventId, String status) {}
}
