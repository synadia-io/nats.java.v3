# OpenTelemetry Integration

## Goal

Add optional OpenTelemetry support for tracing and metrics in the NATS Java client.

## Approach

Separate optional module (`nats-telemetry` or similar) — NOT inline in core.
Core library has zero OTel overhead. No if-checks, no no-ops, no dependencies.

The module decorates or intercepts at the connection/subscription level:
- Publish: create a span, inject trace context into headers
- Subscribe/message delivery: extract trace context from headers, create span
- Connection metrics: expose Statistics as OTel gauges/counters

## Concerns

- **Performance:** Inline if-checks in core hot path are nearly free (branch prediction + JIT eliminates dead branches), but object allocation for spans/attributes is not. Keeping it out of core entirely avoids the question.
- **Dependency:** OTel API is a compile-time dependency of the module, not core. Users who don't want OTel don't pull it in.
- **Compatibility:** Target `io.opentelemetry:opentelemetry-api` (the stable API, not the SDK). Users bring their own SDK/exporter.

## Prior Art

- Go NATS client: no built-in OTel
- Most NATS users instrument at the application layer, not the client library
- Java Kafka client: has optional interceptors that OTel hooks into
- The interceptor/decorator pattern is the standard approach

## Status

Not started. Planned as a future optional module.
