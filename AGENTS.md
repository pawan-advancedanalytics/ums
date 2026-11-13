# Codex Instructions — Universal Messaging System

## Mission
Build a reusable, framework-independent messaging integration platform. Start with Java 21, Spring Boot 3.x, Gradle and RabbitMQ. External applications should be able to publish events via REST; internal subscribers may use RabbitMQ directly.

## Development rules
- Work in small, reviewable increments. Explain the plan before large changes.
- Keep business-specific logic out of the messaging gateway.
- Organize packages by responsibility: api, application, contract, broker, config, error.
- Prefer simple interfaces and dependency injection; do not create unnecessary abstractions.
- Never commit passwords, tokens, .env files or production credentials.
- Use environment variables for secrets and provide safe local defaults only for disposable development environments.
- Use stable event envelopes: eventId, eventType, version, source, timestamp, correlationId, payload.
- Support publisher confirms and explicit failure handling. Do not equate HTTP acceptance with successful consumer processing.
- Design for at-least-once delivery. Consumers must be idempotent.
- Use distinct queues for independent subscribers; configure dead-letter handling and bounded retries.
- Do not add Kafka, a dashboard, multi-tenancy, or PostgreSQL without an explicit requirement.
- Include unit tests and integration tests for messaging paths; use Testcontainers where appropriate.
- Update README.md and docs/ARCHITECTURE.md when architecture or setup changes.
- Run ./gradlew test (or gradlew.bat test on Windows) when possible and report results honestly.
- Do not commit generated build artifacts.

## Phase 1 acceptance
1. REST publisher accepts a versioned event envelope.
2. RabbitMQ topic exchange routes an event to two independent durable queues.
3. Java consumer examples process and acknowledge messages.
4. Broker restart and consumer restart scenarios are documented.
5. Retry/dead-letter behavior is tested.
6. Swagger/OpenAPI and health endpoints are available.
7. Docker Compose and local setup instructions work.

## Workflow
Propose a short plan, implement one coherent increment, run checks, summarize changed files and limitations, then wait for review before major scope expansion.
