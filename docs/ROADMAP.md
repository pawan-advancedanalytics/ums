# UMS Implementation Roadmap

## Milestone 0 — Repository foundation
- [x] Architecture document and Codex instructions
- [ ] Spring Boot Gradle skeleton
- [ ] Docker Compose for RabbitMQ
- [ ] CI build and basic tests

## Milestone 1 — Messaging POC
- [ ] Event envelope and validation
- [ ] REST publisher
- [ ] RabbitMQ topic exchange and two independent queues
- [ ] Two sample Java consumers
- [ ] Publish and consume integration test

## Milestone 2 — Reusable MVP
- [ ] Broker publisher confirms and error handling
- [ ] Manual acknowledgment, bounded retries, DLQ
- [ ] OpenAPI and health endpoints
- [ ] Python publisher/consumer example
- [ ] Integration guide and local demo instructions

## Milestone 3 — Hardening
- [ ] Idempotency and deduplication strategy
- [ ] Security and routing permissions
- [ ] Observability, tracing, metrics
- [ ] Failure and load tests
- [ ] Deployment and recovery documentation

Keep scope incremental; review each milestone before expanding.
