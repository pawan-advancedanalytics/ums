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
