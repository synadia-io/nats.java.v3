# Connection Interface Removal Plan

## Overview
Remove the `Connection` interface (`io.synadia.client.Connection`) and use the concrete `NatsConnection` class directly.

---

## Category 1: Interface Declaration (delete)
- `core/src/main/java/io/synadia/client/Connection.java`

## Category 2: `implements Connection` (remove)
- `core/src/main/java/io/synadia/client/impl/NatsConnection.java` — line 32

## Category 3: Factory Methods Returning `Connection` (change to `NatsConnection`)
- `core/src/main/java/io/synadia/client/Nats.java`
  - `connect()` — line 102
  - `connectReconnectOnConnect()` — line 114
  - `connect(String)` — line 146
  - `connectReconnectOnConnect(String)` — line 159
  - `connect(String, AuthHandler)` — line 181
  - `connectReconnectOnConnect(String, AuthHandler)` — line 195
  - `connect(Options)` — line 229
  - `connectReconnectOnConnect(Options)` — line 240
  - `createConnection(Options, boolean)` — line 330
- `core/src/main/java/io/synadia/client/impl/NatsImpl.java`
  - `createConnection(Options, boolean)` — line 14

## Category 4: Listener Interfaces — Parameter Type `Connection` (change to `NatsConnection`)
- `core/src/main/java/io/synadia/client/ConnectionListener.java` — lines 88, 100
- `core/src/main/java/io/synadia/client/ErrorListener.java` — lines 30, 42, 58, 66, 77, 86, 96, 106, 131, 138

## Category 5: Listener Implementations — Parameter Type `Connection`
- `core/src/main/java/io/synadia/client/impl/ErrorListenerLoggerImpl.java` — lines 65, 73, 81, 89
- `core/src/main/java/io/synadia/client/impl/ErrorListenerConsoleImpl.java` — lines 61, 69, 77, 85

## Category 6: Debug/Support Classes — Parameter Type `Connection`
- `core/src/main/java/io/synadia/client/support/DebugListener.java` — lines 47, 52, 89, 94, 99, 104
- `core/src/main/java/io/synadia/client/support/DebugConnectionListener.java` — lines 22, 29
- `core/src/main/java/io/synadia/client/support/DebugErrorListener.java` — lines 34, 102, 110, 118, 126
- `core/src/main/java/io/synadia/client/support/Debug.java` — import

## Category 7: Core Main Source — Import / Type Usage
- `core/src/main/java/io/synadia/client/impl/NatsMessage.java` — import, line 10
- `core/src/main/java/io/synadia/client/impl/NatsImpl.java` — import, line 4
- `core/src/main/java/io/synadia/client/impl/NatsJetStreamMessage.java` — import, lines 10, 35, 104, 119
- `core/src/main/java/io/synadia/client/impl/NatsConnection.java` — internal `ErrorListenerCaller` interface, line 2152

## Category 8: Service Module — Fields, Parameters, Imports
- `service/src/main/java/io/synadia/service/Service.java` — field line 47, import
- `service/src/main/java/io/synadia/service/ServiceBuilder.java` — field line 27, import
- `service/src/main/java/io/synadia/service/Discovery.java` — field line 31, import
- `service/src/main/java/io/synadia/service/EndpointContext.java` — field line 19, import
- `service/src/main/java/io/synadia/service/ServiceMessage.java` — import

## Category 9: Javadoc `{@link Connection}` References
- `core/src/main/java/io/synadia/client/Dispatcher.java` — lines 15, 16
- `core/src/main/java/io/synadia/client/JetStream.java` — lines 67, 413, 430
- `core/src/main/java/io/synadia/client/Nats.java` — lines 19, 56

## Category 10: Examples — Import + Local Variable Type (56 files total)

### Top-level examples
- `examples/src/main/java/io/synadia/examples/NatsPub.java`
- `examples/src/main/java/io/synadia/examples/NatsPubMany.java`
- `examples/src/main/java/io/synadia/examples/NatsReply.java`
- `examples/src/main/java/io/synadia/examples/NatsReq.java`
- `examples/src/main/java/io/synadia/examples/NatsReqFuture.java`
- `examples/src/main/java/io/synadia/examples/NatsSub.java`
- `examples/src/main/java/io/synadia/examples/NatsSubDispatch.java`
- `examples/src/main/java/io/synadia/examples/NatsSubQueue.java`
- `examples/src/main/java/io/synadia/examples/ReportNoResponders.java`
- `examples/src/main/java/io/synadia/examples/ConnectTime.java`

### stability examples
- `examples/src/main/java/io/synadia/examples/stability/StabilityPub.java`
- `examples/src/main/java/io/synadia/examples/stability/StabilitySub.java`

### chaosTestApp examples
- `examples/src/main/java/io/synadia/examples/chaosTestApp/ChaosTestApp.java`
- `examples/src/main/java/io/synadia/examples/chaosTestApp/Monitor.java`
- `examples/src/main/java/io/synadia/examples/chaosTestApp/Publisher.java`

### autobench examples
- `examples/src/main/java/io/synadia/examples/autobench/AutoBenchmark.java`
- `examples/src/main/java/io/synadia/examples/autobench/PubBenchmark.java`
- `examples/src/main/java/io/synadia/examples/autobench/PubWithHeadersBenchmark.java`
- `examples/src/main/java/io/synadia/examples/autobench/ThrottledBenchmark.java`

### jetstream examples
- `examples/src/main/java/io/synadia/examples/jetstream/NatsJsPub.java`
- `examples/src/main/java/io/synadia/examples/jetstream/NatsJsPubAsync.java`
- `examples/src/main/java/io/synadia/examples/jetstream/NatsJsPubAsync2.java`
- `examples/src/main/java/io/synadia/examples/jetstream/NatsJsManageStreams.java`
- `examples/src/main/java/io/synadia/examples/jetstream/NatsJsManageConsumers.java`

### natsIoDoc examples
- `examples/src/main/java/io/synadia/examples/natsIoDoc/BasicsPublish.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/BasicsSubscribe.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/GettingStartedPublish.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/GettingStartedSubscribe.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/PublishSubscribeBasic.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/QueueGroupsBasic.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/QueueGroupsMixedSubscribers.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/QueueGroupsRequestReply.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/RequestReplyBasic.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/RequestReplyCalculator.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/RequestReplyHeaders.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/RequestReplyMultipleResponders.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/RequestReplyNoResponders.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/RequestReplyTimeout.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/SubjectsMonitoring.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/SubjectsMultiWildcard.java`
- `examples/src/main/java/io/synadia/examples/natsIoDoc/SubjectsSingleWildcard.java`

### benchmark / service examples
- `examples/src/main/java/io/synadia/examples/benchmark/NatsBench2.java`
- `examples/src/main/java/io/synadia/examples/service/ServiceExample.java`

## Category 11: Test Code — Import + Type Usage

### Test helpers
- `core/src/test/java/io/synadia/client/utils/TestBase.java` — interface methods, fields, utility methods
- `core/src/test/java/io/synadia/client/utils/ConnectionUtils.java` — parameters and return types throughout
- `core/src/test/java/io/synadia/client/utils/OptionsUtils.java` — lines 48, 57
- `core/src/test/java/io/synadia/client/utils/VersionUtils.java` — line 13

### Test classes
- `core/src/test/java/io/synadia/client/AuthTests.java` — many local variables
- `core/src/test/java/io/synadia/client/PublishTests.java` — many local variables
- `core/src/test/java/io/synadia/client/BadHandler.java` — parameter types
- `core/src/test/java/io/synadia/client/impl/NatsPackageScopeWorkarounds.java`
- `core/src/test/java/io/synadia/client/impl/JetStreamTestingContext.java`
- `core/src/test/java/io/synadia/client/impl/JetStreamManagementWithConfTests.java`
- `core/src/test/java/io/synadia/client/impl/InfoHandlerTests.java`
- `core/src/test/java/io/synadia/client/impl/ValidateIssue1426Test.java`
- `core/src/test/java/io/synadia/client/impl/WebsocketConnectTests.java`
- `core/src/test/java/io/synadia/client/other/FlushBenchmark.java`
- `core/src/test/java/io/synadia/client/other/PublishBenchmarkWithStats.java`
- `core/src/test/java/io/synadia/client/other/RequestBenchmarkWithStats.java`
- `core/src/test/java/io/synadia/compatibility/Command.java`
- `core/src/test/java/io/synadia/compatibility/ObjectStoreCommand.java`

### Service tests
- `service/src/test/java/io/synadia/service/ServiceTests.java` — many local variables, fields, constructor params
