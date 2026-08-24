# Fully-Qualified Class Name Usages

Places that reference a class by its fully-qualified (package-prefixed) name inline in code instead of importing it — like `StreamState.java:25` (`io.nats.json.LazyJsonParser.parse("{}")`). Grouped by how worth fixing each is.

## A. Un-imported — would be fixed by adding a normal import (same case as the StreamState example)

- `jetstream/src/main/java/io/synadia/client/api/StreamState.java:25` — `io.nats.json.LazyJsonParser` (the example). Only used once.
- `core/src/main/java/io/synadia/client/Options.java:121, 465` — `java.util.function.Consumer<HttpRequest>`. No `Consumer` import in the file.
- `core/src/main/java/io/synadia/client/OptionsProperties.java:300, 307, 314, 321, 327, 334, 344, 351, 361, 379` — `java.util.function.Consumer<...>` on 10 method signatures. No `Consumer` import; one import cleans all ten.
- `jetstream/src/test/java/io/synadia/client/api/ApiCreatorsToDtoRoundTripTests.java:230` — `io.nats.json.JsonSerializable` as a generic bound.
- `core/src/test/java/io/synadia/client/OptionsTests.java:195, 196` and `core/src/test/java/io/synadia/client/impl/WebsocketConnectTests.java:83` — `java.util.function.Consumer<HttpRequest>` in tests; no import.
- `core/src/test/java/io/synadia/client/utils/ssl/HandshakeEvent.java:30`, `DiagnosticSslContext.java:221, 242`, `ExpiringClientCertUtil.java:78, 118` — `java.security.cert.Certificate[]`. These files import `X509Certificate` but use the **base** `Certificate` type here, which is not imported.

## B. Gratuitous — the type is ALREADY imported and used in short form elsewhere in the same file (inconsistent)

- `core/src/main/java/io/synadia/client/OptionsBuilder.java:119, 1029` — `java.util.function.Consumer<HttpRequest>`, yet `Consumer` is imported (line 28) and used short at line 1043.
- `core/src/main/java/io/synadia/client/utils/SSLUtils.java:27, 34` — `java.security.cert.X509Certificate[]`, yet `X509Certificate` is imported (line 10) and used short at lines 28, 29, 38, 44.

## C. Deliberate name-clash avoidance (qualifying is a defensible choice — leave unless you want a shadowing import)

- `jetstream/src/test/java/io/synadia/client/api/ApiResponseTests.java:27, 165` — `io.synadia.client.api.Error` is qualified because it would clash with `java.lang.Error`. (The file already does `import static io.synadia.client.api.Error.*` for the statics.)

## NOT the pattern — string literals (reflection / property class names, intentional)

- `core/src/test/java/io/synadia/client/ConnectTests.java:389` — `"io.synadia.client.impl.SimulateSocketDataPortException"`
- `core/src/test/java/io/synadia/client/OptionsTests.java:506` — `"io.synadia.client.utils.CoverageServerPool"`
- `core/src/test/java/io/synadia/client/utils/TestBase.java:75` — `"io.nats.nkey.LtsNKeyProvider"`
