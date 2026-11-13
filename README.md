# Universal Messaging System

Java 21 / Spring Boot 3.5 / Gradle REST-to-RabbitMQ demo, package `com.ums.messaging`.
The gateway validates a generic envelope and publishes persistent messages to a durable topic
exchange. Independent audit and notification queues subscribe to `user.created`.
The sample consumers only log metadata; they do not send notifications or store audit records.

## Windows and IntelliJ startup

1. Install a JDK 21 and Docker Desktop (Linux containers); start Docker Desktop.
2. Open this directory in IntelliJ as a Gradle project. Set Project SDK and
   Settings > Build Tools > Gradle > Gradle JVM to JDK 21; use the Gradle wrapper.
3. In PowerShell, select JDK 21 if the shell uses another version:
   `$env:JAVA_HOME = 'C:\path\to\jdk-21'`.
4. Run `docker compose up -d --wait`.
5. Run `MessagingApplication` from IntelliJ, or `.\gradlew.bat bootRun`.

The application binds to `127.0.0.1:8080`. Swagger UI is at
http://localhost:8080/swagger-ui/index.html, OpenAPI at http://localhost:8080/v3/api-docs,
health at http://localhost:8080/actuator/health, and RabbitMQ management at
http://localhost:15672. Disposable local broker defaults are `ums_dev` / `ums_dev_only`.
Set `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`
to override application connection settings. Compose reads the same username/password
variables. Changing defaults does not change credentials in an existing broker volume.
Do not use these defaults outside disposable development. No authentication is implemented;
authorization and source/routing permissions remain required before non-local deployment.

Publish a sample from PowerShell:

```powershell
$event = @{
    eventId = [guid]::NewGuid().ToString()
    eventType = 'user.created'
    version = '1.0'
    source = 'local-demo'
    timestamp = [DateTimeOffset]::UtcNow.ToString('o')
    correlationId = 'request-123'
    payload = @{ userId = '101' }
} | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/messages/publish `
    -ContentType 'application/json' -Body $event
```

`202` with `BROKER_CONFIRMED` means the broker acknowledged a publish that routed to
at least one queue. It does **not** guarantee every intended queue still exists or any
consumer completed. The generic gateway accepts other event types, but only `user.created`
is bound in this demo: an unbound routing key returns `503`. Invalid envelopes return `400`.
Broker rejection, connection failure, return, or confirmation timeout returns `503`.
Timeouts/disconnects can leave delivery uncertain: retry with the same eventId and expect
duplicates. The gateway does not deduplicate publishes. Publish confirmation waits up to
5 seconds after the send; connection establishment can add time.

## Tests and build

```powershell
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat bootJar
```

`test` uses mocks and needs no broker or Docker. `integrationTest` is explicitly tagged,
starts its own disposable RabbitMQ with Testcontainers, and requires Docker Desktop;
it does not use the Compose broker. It fails if Docker is unavailable, rather than silently
skipping tests. Reports are under `build/reports/tests/`. Linux/macOS use `./gradlew`.

## Acknowledgments, retry, and recovery

Spring AMQP AUTO acknowledgment means the container acknowledges **after** the listener
returns successfully, not RabbitMQ auto-ack on delivery. Exceptions retry up to three total
attempts (500ms then 1s backoff), then reject without requeue into that subscriber's DLQ.
Conversion failures are also rejected. Inspect `ums.audit.dlq` and `ums.notification.dlq`
in the management UI; there is no automatic DLQ replay. Investigate/fix the cause before
controlled replay. Independent subscriber failures do not block the other queue.

- Broker restart: run `docker compose restart rabbitmq`. Durable exchanges/queues and
  persistent messages survive ordinary restarts through the named volume; application
  connections/listeners recover. Publishes during downtime can return 503.
- Consumer/application restart: stop the application, restart it, then publish again.
  Existing queues remain while it is down. Broker clients can publish during the outage;
  queued messages are consumed after startup. Unacknowledged deliveries are requeued and
  may be delivered more than once. The REST gateway is unavailable while the app is down.
- `docker compose down` preserves the named volume. `docker compose down -v` deletes
  local messages and topology data; use only when intentionally resetting the demo.

Delivery is at least once. Repeated demo log lines are harmless; production consumers must
persist deduplication by eventId atomically with business effects. These logging samples
provide no durable deduplication. Classic queues and a single local broker are not an HA
or lossless dead-lettering design. No outbox, database, Kafka, or production security is included.

See [architecture](docs/ARCHITECTURE.md) and [roadmap](docs/ROADMAP.md).
