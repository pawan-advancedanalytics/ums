package com.ums.messaging.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record EventMessage<T>(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 200) @Pattern(regexp = "[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*") String eventType,
        @NotBlank @Pattern(regexp = "[1-9][0-9]*\\.[0-9]+") String version,
        @NotBlank @Size(max = 200) String source,
        @NotNull Instant timestamp,
        @NotBlank @Size(max = 200) String correlationId,
        @NotNull T payload) {
}
