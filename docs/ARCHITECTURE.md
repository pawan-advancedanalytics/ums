# Universal Messaging System — Architecture v0.1

## Goal
Offer a reusable, language-agnostic messaging integration layer for existing and future applications, starting as a RabbitMQ-backed proof of concept.

## Scope: Phase 1
- Spring Boot gateway exposing a REST publish endpoint.
- Stable, versioned event envelope and validation.
- RabbitMQ topic exchange with independent subscriber queues.
- Publisher confirms, acknowledgments, bounded retries, dead-letter queues.
- Health, logs, correlation IDs and local Docker Compose deployment.
- Example Java subscribers; Python example in a later increment.

## Component boundaries
```text
Java / Python / other publisher
              |
         REST /api/v1/messages/publish
              |
      Spring Boot Gateway
      - validate event envelope
      - authorize publisher (before non-local use)
      - publish through broker adapter
              |
        RabbitMQ topic exchange
           /             \
    subscriber-A       subscriber-B
       queue              queue
         |                  |
    Consumer A         Consumer B
```

## Message envelope
```json
{
  "eventId": "uuid",
  "eventType": "user.created",
  "version": "1.0",
  "source": "user-service",
  "timestamp": "2026-11-13T08:00:00Z",
  "correlationId": "request-123",
  "payload": {"userId": "101"}
}
```

## Delivery semantics
- Target at-least-once delivery, not exactly-once processing.
- A publish acknowledgment from RabbitMQ does not imply consumer completion.
- Each independent subscriber receives its own queue; competing workers can share a queue.
- Consumers should deduplicate by eventId when processing has side effects.
- Use bounded retries and dead-letter handling for poison messages.
- Keep queue configuration explicit and observable.

## API sketch
`POST /api/v1/messages/publish`: validate and publish an event; return an event ID and acceptance status. Precisely document whether acceptance follows broker confirmation. Never claim that subscribers have completed.

## Non-goals for Phase 1
Kafka, multi-tenant control plane, UI dashboard, distributed transactions, generic webhook delivery, production HA.

## Proposed package structure
```text
src/main/java/.../ums/
  api/
  application/
  contract/
  broker/rabbitmq/
  config/
  error/
src/test/java/.../ums/
```

## Future design decisions
- Authentication/authorization and per-source routing permissions.
- Event schema compatibility and registry.
- Transactional outbox for publishers requiring database/event consistency.
- Webhook delivery for applications without broker clients.
- Kafka integration only when replay or stream retention is required.

## Implemented first vertical slice

The Java 21 Spring Boot application uses package `com.ums.messaging`, organized into
`api`, `application`, `contract`, `broker`, `config`, and `error`. `EventMessage<T>`
keeps the payload generic. REST binds payloads as JSON and validates envelope fields.
Version format is major.minor (for example `1.0`); this is envelope validation, not
business payload schema validation.

`POST /api/v1/messages/publish` sends persistent JSON via RabbitTemplate with mandatory
routing, correlated publisher confirms and returns. It returns 202 only after a positive
confirm with no return. Unroutable publishes, nacks, timeouts and broker errors return
503; timeout/disconnect outcomes may be uncertain. CorrelationData uses a unique attempt
ID, while AMQP messageId/correlationId preserve the envelope IDs. A broker confirmation
proves routing to at least one queue, not completion or existence of every subscriber.

The durable topic exchange `ums.events` binds `user.created` to `ums.audit` and
`ums.notification`. Each queue dead-letters through durable direct exchange
`ums.dead-letter` using its queue name as routing key into `<queue>.dlq`.
Listeners use container AUTO acknowledgment after successful processing and bounded
stateless retry (3 attempts). Exhaustion rejects without requeue; fatal conversion errors
also dead-letter. No automatic DLQ replay is configured.

Demo consumers only log event metadata; no user-specific business behavior or durable
deduplication is implemented. Real consumers require idempotent effects and durable
eventId tracking. Stateless retries can restart after process failure; the three-attempt
limit applies per delivery within a process, not across crashes. Single-node classic
queues do not guarantee lossless dead-letter transfer under broker failure.

Compose exposes RabbitMQ AMQP/management on loopback and persists data in a named
volume. The gateway also defaults to loopback; security must be implemented before
external exposure. Swagger and Actuator health are available. Unit tests use no broker;
the separate `integrationTest` task provisions RabbitMQ with Testcontainers. See README
for Windows setup, restart scenarios, response semantics and local credentials.
