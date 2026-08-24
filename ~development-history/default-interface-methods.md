# Default Interface Methods Report

24 default methods across 8 interfaces in `core/src/main/java/`.

## ErrorListener.java (11 methods)
`io/synadia/client/ErrorListener.java`

| Line | Method |
|------|--------|
| 31 | `default void errorOccurred(NatsConnection conn, String error)` |
| 43 | `default void exceptionOccurred(NatsConnection conn, Exception exp)` |
| 59 | `default void slowConsumerDetected(NatsConnection conn, Consumer consumer)` |
| 67 | `default void messageDiscarded(NatsConnection conn, Message msg)` |
| 78 | `default void heartbeatAlarm(NatsConnection conn, JetStreamSubscription sub, long lastStreamSequence, long lastConsumerSequence)` |
| 87 | `default void unhandledStatus(NatsConnection conn, JetStreamSubscription sub, Status status)` |
| 97 | `default void pullStatusWarning(NatsConnection conn, JetStreamSubscription sub, Status status)` |
| 107 | `default void pullStatusError(NatsConnection conn, JetStreamSubscription sub, Status status)` |
| 132 | `default void flowControlProcessed(NatsConnection conn, JetStreamSubscription sub, String subject, FlowControlSource source)` |
| 139 | `default void socketWriteTimeout(NatsConnection conn)` |
| 151 | `default String supplyMessage(String label, NatsConnection conn, Consumer consumer, Subscription sub, Object... pairs)` |

## NatsInetAddressProvider.java (6 methods)
`io/synadia/client/support/NatsInetAddressProvider.java`

| Line | Method |
|------|--------|
| 17 | `default InetAddress getByAddress(String host, byte[] addr) throws UnknownHostException` |
| 31 | `default InetAddress getByName(String host) throws UnknownHostException` |
| 46 | `default InetAddress[] getAllByName(String host) throws UnknownHostException` |
| 54 | `default InetAddress getLoopbackAddress()` |
| 64 | `default InetAddress getByAddress(byte[] addr) throws UnknownHostException` |
| 74 | `default InetAddress getLocalHost() throws UnknownHostException` |

## DataPort.java (2 methods)
`io/synadia/client/impl/DataPort.java`

| Line | Method |
|------|--------|
| 24 | `default void afterConstruct(Options options)` |
| 50 | `default void forceClose() throws IOException` |

## NatsSystemClockProvider.java (2 methods)
`io/synadia/client/NatsSystemClockProvider.java`

| Line | Method |
|------|--------|
| 11 | `default long currentTimeMillis()` |
| 17 | `default long nanoTime()` |

## ReadListener.java (2 methods)
`io/synadia/client/ReadListener.java`

| Line | Method |
|------|--------|
| 14 | `default void protocol(String op, String text)` |
| 21 | `default void message(String op, Message message)` |

## Watcher.java (1 method)
`io/synadia/client/api/Watcher.java`

| Line | Method |
|------|--------|
| 30 | `default String getConsumerNamePrefix()` |

## Message.java (1 method)
`io/synadia/client/Message.java`

| Line | Method |
|------|--------|
| 160 | `default long consumeByteCount()` |
