// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.api.ServerInfo;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.ByteArrayBuilder;
import io.synadia.client.utils.NatsRequestCompletableFuture;
import io.synadia.client.utils.NatsUri;
import io.synadia.client.utils.ScheduledTask;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import static io.synadia.client.ConnectionStatus.*;
import static io.synadia.client.utils.NatsConstants.*;
import static io.synadia.client.utils.NatsRequestCompletableFuture.CancelAction;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The rewritten connection, chosen with {@link OptionsBuilder#connectionImplementation(ConnectionImplementation)}.
 * <p>It extends {@link NatsConnection} so every API that takes a connection accepts it. The public API,
 * subscriptions, dispatchers, request/reply, drain and the reader's parsing are the classic ones; what is
 * replaced is the outgoing path and the connect / reconnect / close lifecycle.
 * <p>Behavior differences from {@link NatsConnection}, each deliberate:
 * <ol>
 * <li>A publish is serialized when it is called. Changing the message's headers or data after publish
 *     returns no longer changes what is sent.</li>
 * <li>PONG is written ahead of queued outgoing data instead of behind it.</li>
 * <li>The connect handshake (CONNECT, PING, wait for PONG) is read on the connecting thread. A server
 *     {@code -ERR} during the handshake fails the attempt as soon as it is read, instead of when the
 *     connection timeout runs out waiting for the PONG. The exception is an authentication error that
 *     counts toward the rule that two consecutive identical authentication errors from a server stop
 *     the reconnect (a reconnect attempt, or an initial attempt with reconnect on connect): that attempt
 *     still ends at its connection timeout, as in the classic implementation, so the time a server has
 *     to start accepting again between the two is unchanged.</li>
 * <li>{@link #outgoingPendingMessageCount()} and {@link #outgoingPendingBytes()} never block. They do
 *     not count a PONG waiting to be written.</li>
 * <li>A reader or writer belonging to a socket that has already been replaced cannot trigger a reconnect.</li>
 * <li>{@link #close()} during a reconnect stops the reconnect and returns once the connection is closed
 *     or the connection timeout passes.</li>
 * <li>The test hooks {@code getReader()} and {@code getWriter()} return objects this implementation does
 *     not use.</li>
 * </ol>
 */
public class NatsConnectionV3 extends NatsConnection {

    /**
     * One socket and the reader and writer bound to it. A new one is made for every connection attempt.
     */
    static final class Generation {
        final DataPort port;
        NatsConnectionReader reader;
        OutboundWriter writer;

        Generation(DataPort port) {
            this.port = port;
        }
    }

    private final OutboundBuffer outbound;
    private volatile @Nullable Generation generation;

    // A connect attempt in progress. An error reported during it fails the attempt.
    private final Object attemptMonitor = new Object();
    private boolean inAttempt;
    private @Nullable Exception attemptFailure;
    private volatile @Nullable DataPort attemptPort;

    // A reconnect asked for while another transition was running. The transition runs it when it ends.
    private volatile @Nullable Generation pendingReconnect;

    private volatile boolean lameDuck;

    // volatile copies of NatsConnection.closing and reconnectWaiter, which are read across threads here
    private volatile boolean closeRequested;
    private volatile @Nullable CompletableFuture<Boolean> reconnectWait;
    private volatile boolean readListenerSet;
    private volatile @Nullable ReadListener readListener;

    /**
     * Construct the connection. Use {@link Nats#connect(Options)} with
     * {@link OptionsBuilder#connectionImplementation(ConnectionImplementation)} set to
     * {@link ConnectionImplementation#V3}.
     * @param options the options
     */
    protected NatsConnectionV3(@NonNull Options options) {
        super(options);
        outbound = new OutboundBuffer(options);
    }

    @Override
    public void setReadListener(@Nullable ReadListener rl) {
        readListener = rl;
        readListenerSet = true;
        Generation g = generation;
        if (g != null && g.reader != null) {
            g.reader.setReadListener(rl);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Transitions. tryingToConnect is the single guard: whoever wins it runs connect, reconnect or close.
    // ----------------------------------------------------------------------------------------------------

    @Override
    protected void connect(boolean reconnectOnConnect) throws InterruptedException, IOException {
        if (!tryingToConnect.compareAndSet(false, true)) {
            return;
        }
        try {
            connectV3(reconnectOnConnect);
        }
        finally {
            endTransition();
        }
    }

    @Override
    protected void connectImpl(boolean reconnectOnConnect) throws InterruptedException, IOException {
        connectV3(reconnectOnConnect);
    }

    @Override
    public void forceReconnect(@Nullable ForceReconnectOptions frOptions) throws IOException, InterruptedException {
        if (isClosed() || closeRequested) {
            return;
        }
        if (!tryingToConnect.compareAndSet(false, true)) {
            return;
        }
        try {
            forceReconnectImpl(frOptions == null ? ForceReconnectOptions.DEFAULT_INSTANCE : frOptions);
        }
        finally {
            endTransition();
        }
    }

    @Override
    protected void forceReconnectImpl(@NonNull ForceReconnectOptions frOpts) throws InterruptedException {
        if (frOpts.isFlush()) {
            try {
                flush(frOpts.getFlushWait());
            }
            catch (TimeoutException e) {
                // Ignored, as in the classic implementation
            }
        }
        reconnectFromConnected(frOpts.isForceClose());
    }

    @Override
    protected void reconnect() throws InterruptedException {
        if (!tryingToConnect.compareAndSet(false, true)) {
            return;
        }
        try {
            reconnectAndResume();
        }
        finally {
            endTransition();
        }
    }

    @Override
    protected void reconnectImpl() throws InterruptedException {
        reconnectAndResume();
    }

    @Override
    protected void closeSocket(boolean tryReconnectIfConnected, boolean forceClose) throws InterruptedException {
        if (tryReconnectIfConnected && isConnected()) {
            reconnectFromConnected(forceClose);
        }
        else {
            updateStatus(DISCONNECTED);
            teardown(forceClose);
            if (closeRequested) {
                finalClose(false);
            }
        }
    }

    @Override
    protected void closeSocketImpl(boolean forceClose) {
        teardown(forceClose);
    }

    @Override
    protected void close(boolean checkDrainStatus, boolean forceClose) throws InterruptedException {
        if (checkDrainStatus && isDraining()) {
            waitForDisconnectOrClose(options.getConnectionTimeout());
            return;
        }
        if (isClosed()) {
            return;
        }

        markClosing();
        CompletableFuture<Boolean> waiter = reconnectWait;
        if (waiter != null) {
            waiter.cancel(true);
        }
        DataPort ap = attemptPort;
        if (ap != null) {
            try {
                ap.forceClose(); // an attempt in progress fails now instead of at its timeout
            }
            catch (IOException ignore) {
                // nothing to do
            }
        }

        long timeoutNanos = options.getConnectionTimeout() * NANOS_PER_MILLI;
        long start = NatsSystemClock.nanoTime();
        while (!isClosed()) {
            if (tryingToConnect.compareAndSet(false, true)) {
                try {
                    finalClose(forceClose);
                }
                finally {
                    endTransition();
                }
                return;
            }
            // Another transition is running. It sees closeRequested and closes when it ends.
            long remaining = timeoutNanos - (NatsSystemClock.nanoTime() - start);
            if (remaining <= 0) {
                return;
            }
            long slice = 10 * NANOS_PER_MILLI;
            waitWhile(remaining < slice ? remaining : slice, v -> !isClosed());
        }
    }

    // Release the transition guard, then run anything that was asked for while it was held.
    private void endTransition() {
        while (true) {
            tryingToConnect.set(false);
            boolean wantClose = closeRequested && !isClosed();
            Generation p = pendingReconnect;
            boolean wantReconnect = !wantClose && p != null && p == generation && isConnected();
            if (!wantClose && !wantReconnect) {
                return;
            }
            if (!tryingToConnect.compareAndSet(false, true)) {
                return; // another thread took the guard and will do it
            }
            try {
                if (wantClose) {
                    finalClose(false);
                }
                else {
                    runPendingReconnect();
                }
            }
            catch (InterruptedException e) {
                processException(e);
                Thread.currentThread().interrupt();
                tryingToConnect.set(false);
                return;
            }
        }
    }

    private void runPendingReconnect() throws InterruptedException {
        Generation p = pendingReconnect;
        pendingReconnect = null;
        if (p != null && p == generation && isConnected() && !closeRequested) {
            reconnectFromConnected(true);
        }
    }

    // A reader or writer reports only for its own socket. A report from one whose socket was already
    // torn down or replaced is ignored, so it cannot count a failure against a server or reconnect a
    // healthy connection.
    @Override
    protected void handleCommunicationIssue(NatsConnectionReader source, Exception io) {
        Generation g = generation;
        if (g != null && g.reader == source) {
            handleCommunicationIssue(io);
        }
    }

    void handleWriterIssue(Generation source, Exception io) {
        if (generation == source) {
            handleCommunicationIssue(io);
        }
    }

    @Override
    protected void handleCommunicationIssue(Exception io) {
        Generation current = generation;

        synchronized (attemptMonitor) {
            if (inAttempt) {
                if (attemptFailure == null) {
                    attemptFailure = io;
                }
                return;
            }
        }

        if (isClosed() || closeRequested || isDraining()) {
            return;
        }

        processException(io);
        NatsUri cs = currentServer;
        if (cs != null) {
            serverPool.connectFailed(cs);
        }

        if (current == null || !isConnected()) {
            return;
        }

        pendingReconnect = current;
        ExecutorService ex = executor;
        if (ex != null) {
            try {
                ex.submit(() -> {
                    if (tryingToConnect.compareAndSet(false, true)) {
                        try {
                            runPendingReconnect();
                        }
                        catch (InterruptedException e) {
                            processException(e);
                            Thread.currentThread().interrupt();
                        }
                        finally {
                            endTransition();
                        }
                    }
                });
            }
            catch (RejectedExecutionException ignore) {
                // closing
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Connect and reconnect
    // ----------------------------------------------------------------------------------------------------

    private void connectV3(boolean reconnectOnConnect) throws InterruptedException, IOException {
        if (options.getServers().isEmpty()) {
            throw new IllegalArgumentException("No servers provided in options");
        }

        lastError.set("");

        Set<NatsUri> failList = new HashSet<>();
        boolean keepGoing = true;
        NatsUri first = null;
        NatsUri cur;
        while (keepGoing && (cur = serverPool.peekNextServer()) != null) {
            if (first == null) {
                first = cur;
            }
            else if (cur.equals(first)) {
                break;  // connect only goes through loop once
            }
            serverPool.nextServer(); // b/c we only peeked.

            for (NatsUri resolved : resolveHost(cur)) {
                if (isClosed()) {
                    keepGoing = false;
                    break;
                }
                connectError.set(""); // new on each attempt

                updateStatus(CONNECTING, resolved, cur);

                // with reconnectOnConnect the errors recorded here count toward the double auth error rule
                tryToConnectV3(cur, resolved, reconnectOnConnect);

                if (isConnected()) {
                    serverPool.connectSucceeded(cur);
                    keepGoing = false;
                    break;
                }

                updateStatus(DISCONNECTED, resolved, cur);

                String err = connectError.get();
                if (isAuthenticationError(err)) {
                    serverAuthErrors.put(resolved, err);
                }
            }

            if (!isConnected() && !isClosed()) {
                failList.add(cur);
                serverPool.connectFailed(cur);
            }
        }

        if (isConnected()) {
            outbound.openData();
            return;
        }

        if (!isClosed()) {
            if (reconnectOnConnect) {
                reconnectAndResume();
            }
            else {
                finalClose(false);

                String err = connectError.get();
                if (isAuthenticationError(err)) {
                    throw new AuthenticationException("Authentication error connecting to NATS server: " + err);
                }
                throw new IOException("Unable to connect to NATS servers: " + failList);
            }
        }
    }

    // From CONNECTED: report the disconnect, drop the socket, then reconnect or close.
    private void reconnectFromConnected(boolean forceClose) throws InterruptedException {
        updateStatus(DISCONNECTED);
        teardown(forceClose);
        if (closeRequested) {
            finalClose(false);
            return;
        }
        reconnectAndResume();
    }

    // From not connected: run the reconnect campaign, then resubscribe and release the held back data.
    private void reconnectAndResume() throws InterruptedException {
        if (isClosed()) {
            return;
        }

        if (options.getMaxReconnects() == 0) {
            finalClose(false);
            return;
        }

        if (!isConnected() && !closeRequested) {
            reconnectLoop();
        }

        if (!isConnected() || closeRequested) {
            finalClose(false);
            return;
        }

        // CONNECTED is already set, so a subscription made from here on sends its own SUB.
        // These resubscribes go to the control lane, ahead of everything held back in the data lane.
        subscriptions.forEach((sid, subscription) -> {
            if (subscription.dispatcher == null && !subscription.sub.isDraining()) {
                sendSubscriptionMessage(sid, subscription.sub.getSubject(), subscription.sub.getQueueName(), true);
            }
        });

        dispatchers.forEach(d -> {
            if (!d.isDraining()) {
                d.resendSubscriptions();
            }
        });

        outbound.openData();

        processConnectionEvent(ConnectionEvent.RESUBSCRIBED, uriDetail(currentServer));
    }

    private void reconnectLoop() {
        long round = 0;
        NatsUri first = null;
        NatsUri cur;
        while ((cur = serverPool.nextServer()) != null) {
            if (first == null) {
                first = cur;
                ++round;   // round becomes 1
                // Consume the lame duck signal whatever the behavior does with it, so a signal received
                // under a behavior that ignores it does not survive into a later campaign.
                boolean ld = lameDuck;
                lameDuck = false;
                if (delayBeforeFirstRound(ld)) {
                    waitReconnectDelay(round, ld);
                }
            }
            else if (first.equals(cur)) {
                waitReconnectDelay(++round, false); // went around the pool an entire time
            }

            for (NatsUri resolved : resolveHost(cur)) {
                if (isClosed() || closeRequested) {
                    return;
                }
                connectError.set(""); // reset on each loop
                updateStatus(RECONNECTING, resolved, cur);

                tryToConnectV3(cur, resolved, true);

                if (isConnected()) {
                    serverPool.connectSucceeded(cur);
                    statistics.incrementReconnects();
                    return;
                }

                String err = connectError.get();
                if (isAuthenticationError(err)) {
                    if (err.equals(serverAuthErrors.get(resolved))) {
                        return; // double auth error
                    }
                    serverAuthErrors.put(resolved, err);
                }
            }

            if (!isConnected() && !isClosed()) {
                serverPool.connectFailed(cur);
            }
        }
    }

    private boolean delayBeforeFirstRound(boolean lameDuckTriggered) {
        return switch (options.reconnectDelayBehavior()) {
            case BeforeAllRounds -> true;
            case LameDuckAware -> lameDuckTriggered;
            default -> false;   // BeforeSubsequentRounds
        };
    }

    private void waitReconnectDelay(long round, boolean lameDuckTriggered) {
        long waitMillis = options.getReconnectDelayHandler()
            .getWaitTimeMillis(round, options, serverPool.hasSecureServer(), lameDuckTriggered);

        CompletableFuture<Boolean> waiter = new CompletableFuture<>();
        reconnectWait = waiter;
        reconnectWaiter = waiter;
        if (closeRequested) {
            waiter.complete(Boolean.TRUE); // close may have cancelled the previous waiter before this one existed
        }

        long start = NatsSystemClock.nanoTime();
        while (waitMillis > 0 && !closeRequested && !isClosed() && !isConnected() && !waiter.isDone()) {
            try {
                waiter.get(waitMillis, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception ignore) {
                // timeout or cancel, loop checks
            }
            long now = NatsSystemClock.nanoTime();
            waitMillis -= (now - start) / NANOS_PER_MILLI;
            start = now;
        }

        waiter.complete(Boolean.TRUE);
    }

    @Override
    protected void tryToConnect(NatsUri cur, NatsUri resolved, long nowNanos) {
        tryToConnectV3(cur, resolved, true);
    }

    // One connect attempt. On return the status is CONNECTED with reader and writer running and the
    // data lane still held back, or the attempt failed and the status is DISCONNECTED.
    // holdOnAuthError: the attempt counts toward the double auth error rule (two consecutive identical
    // auth errors from a server stop the reconnect). Such an attempt rejected for authentication ends at
    // its connection timeout, as it does in the classic implementation, so the time between the two
    // counted rejections - the time a server has to start accepting again - is the same as classic.
    private void tryToConnectV3(NatsUri cur, NatsUri resolved, boolean holdOnAuthError) {
        clearCurrentServer();

        long end = NatsSystemClock.nanoTime() + options.getConnectionTimeout() * NANOS_PER_MILLI;
        DataPort port = null;
        synchronized (attemptMonitor) {
            inAttempt = true;
            attemptFailure = null;
        }
        try {
            timeCheck(end);
            cleanUpPongQueue();

            port = options.createDataPort();
            attemptPort = port;
            if (closeRequested) {
                throw new IOException("Connection is closing.");
            }
            port.connect(this, resolved, timeCheck(end));
            this.dataPort = port; // readInitialInfo and upgradeToSecureIfNeeded use the field

            final DataPort handshakePort = port;
            Callable<byte[]> handshake = () -> {
                if (!options.isTlsFirst()) {
                    readInitialInfo();
                    checkVersionRequirements();
                }
                upgradeToSecureIfNeeded(resolved);
                if (options.isTlsFirst()) {
                    readInitialInfo();
                    checkVersionRequirements();
                }
                throwAttemptFailure(); // a TLS handshake failure is reported, not thrown
                return connectAndPing(handshakePort, resolved);
            };

            Future<byte[]> future = connectExecutor.submit(handshake);
            byte[] leftover;
            try {
                leftover = future.get(timeCheck(end), TimeUnit.NANOSECONDS);
            }
            finally {
                future.cancel(true);
            }

            Generation gen = new Generation(port);
            DataPort readPort = leftover.length == 0 ? port : new PrefixedDataPort(port, leftover);
            gen.reader = new NatsConnectionReader(this);
            if (readListenerSet) {
                gen.reader.setReadListener(readListener);
            }
            gen.writer = new OutboundWriter(this, gen, outbound, port, statistics);

            timeCheck(end);
            generation = gen;
            gen.reader.start(CompletableFuture.completedFuture(readPort));
            gen.writer.start();

            if (pingTask == null) {
                long pingMillis = options.getPingInterval();
                if (pingMillis > 0) {
                    pingTask = new ScheduledTask(scheduledExecutor, pingMillis, () -> {
                        if (isConnected() && !isClosing()) {
                            try {
                                softPing(); // The timer always uses the standard queue
                            }
                            catch (Exception e) {
                                // it's running in a thread, there is no point throwing here
                            }
                        }
                    });
                }

                long cleanMillis = options.getRequestCleanupInterval();
                if (cleanMillis > 0) {
                    cleanupTask = new ScheduledTask(scheduledExecutor, cleanMillis, () -> cleanResponses(false));
                }
            }

            statusLock.lock();
            try {
                throwAttemptFailure();
                this.currentServer = cur;
                serverAuthErrors.clear(); // reset on successful connection
                updateStatus(CONNECTED);
            }
            finally {
                statusLock.unlock();
            }

            Exception late = endAttempt();
            if (late != null) {
                processException(late);
                pendingReconnect = gen; // run when this transition ends
            }
        }
        catch (Exception exp) {
            endAttempt();
            processException(exp);
            Generation g = generation;
            if (g != null && g.port == port) {
                generation = null;
                outbound.disconnected();
                teardownGeneration(g, true);
            }
            else if (port != null) {
                try {
                    port.forceClose();
                }
                catch (IOException ignore) {
                    // nothing to do
                }
            }
            dataPort = null;
            cleanUpPongQueue();
            if (holdOnAuthError && isAuthenticationError(connectError.get())) {
                holdUntil(end);
            }
            updateStatus(DISCONNECTED);
        }
        finally {
            attemptPort = null;
        }
    }

    // Wait until the deadline, or until close is requested.
    private void holdUntil(long endNanos) {
        CompletableFuture<Boolean> waiter = new CompletableFuture<>();
        reconnectWait = waiter; // close cancels it
        if (closeRequested) {
            return;
        }
        long remaining = endNanos - NatsSystemClock.nanoTime();
        while (remaining > 0 && !closeRequested && !waiter.isDone()) {
            try {
                waiter.get(remaining, TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception ignore) {
                // timeout or cancel, loop checks
            }
            remaining = endNanos - NatsSystemClock.nanoTime();
        }
    }

    private void throwAttemptFailure() throws Exception {
        Exception f;
        synchronized (attemptMonitor) {
            f = attemptFailure;
        }
        if (f != null) {
            throw f;
        }
    }

    private @Nullable Exception endAttempt() {
        synchronized (attemptMonitor) {
            inAttempt = false;
            Exception late = attemptFailure;
            attemptFailure = null;
            return late;
        }
    }

    // Send CONNECT and PING, then read until PONG. Returns any bytes read past the PONG.
    private byte[] connectAndPing(DataPort port, NatsUri resolved) throws IOException {
        ByteArrayBuilder bab;
        int connectLen;
        try {
            ServerInfo info = serverInfo.get();
            CharBuffer connectOptions = options.buildProtocolConnectOptionsString(resolved.toString(), true, info.getNonce());
            bab = new ByteArrayBuilder(OP_CONNECT_SP_LEN + connectOptions.limit() + 8, UTF_8)
                .append(CONNECT_SP_BYTES).append(connectOptions).append(CRLF_BYTES);
            connectLen = bab.length();
            bab.append(OP_PING_BYTES).append(CRLF_BYTES);
        }
        catch (Exception exp) {
            throw new IOException("Error sending connect string", exp);
        }

        port.write(bab.internalArray(), bab.length());
        port.flush();
        statistics.registerWrite(bab.length());
        statistics.incrementOut(connectLen);
        statistics.incrementOut(bab.length() - connectLen);
        statistics.incrementPingCount();

        return readUntilPong(port);
    }

    private byte[] readUntilPong(DataPort port) throws IOException {
        byte[] buf = new byte[4096];
        int len = 0;
        int pos = 0;
        while (true) {
            int lineEnd = indexOfCrlf(buf, pos, len);
            if (lineEnd < 0) {
                if (pos > 0) {
                    System.arraycopy(buf, pos, buf, 0, len - pos);
                    len -= pos;
                    pos = 0;
                }
                if (len == buf.length) {
                    buf = Arrays.copyOf(buf, buf.length * 2);
                }
                int n = port.read(buf, len, buf.length - len);
                if (n < 0) {
                    throw new IOException("Read channel closed.");
                }
                statistics.registerRead(n);
                len += n;
                continue;
            }

            String line = new String(buf, pos, lineEnd - pos, UTF_8);
            pos = lineEnd + 2;

            int sp = 0;
            while (sp < line.length() && line.charAt(sp) != ' ' && line.charAt(sp) != '\t') {
                sp++;
            }
            String op = line.substring(0, sp).toUpperCase();
            String rest = sp < line.length() ? line.substring(sp + 1) : "";

            switch (op) {
                case OP_PONG:
                    notifyReadListener(OP_PONG, null);
                    return Arrays.copyOfRange(buf, pos, len);
                case OP_PING:
                    notifyReadListener(OP_PING, null);
                    byte[] pong = (OP_PONG + CRLF).getBytes(UTF_8);
                    port.write(pong, pong.length);
                    statistics.registerWrite(pong.length);
                    statistics.incrementOut(pong.length);
                    break;
                case OP_OK:
                    processOK();
                    notifyReadListener(OP_OK, null);
                    break;
                case OP_INFO:
                    handleInfo(rest);
                    notifyReadListener(OP_INFO, null);
                    break;
                case OP_ERR:
                    String errorText = rest.replace("'", "");
                    processError(errorText);
                    notifyReadListener(OP_ERR, errorText);
                    throw new IOException(errorText);
                default:
                    throw new IOException("Unexpected protocol during connect: " + op);
            }
        }
    }

    private static int indexOfCrlf(byte[] buf, int from, int to) {
        for (int i = from; i < to - 1; i++) {
            if (buf[i] == CR && buf[i + 1] == LF) {
                return i;
            }
        }
        return -1;
    }

    private void notifyReadListener(String op, @Nullable String text) {
        ReadListener rl = readListenerSet ? readListener : options.getReadListener();
        if (rl != null) {
            makeCallback(() -> rl.protocol(op, text));
        }
    }

    @Override
    protected void handleInfo(String infoJson) {
        super.handleInfo(infoJson);
        // servers in a cluster are configured alike, so the first server's limit is used for the life of the connection
        long maxPayload = serverInfo.get().getMaxPayload();
        if (maxPayload > 0) {
            outbound.setLargePoolMax(maxPayload + options.getMaxControlLine() + 2);
        }
        if (serverInfo.get().isLameDuckMode()) {
            lameDuck = true;
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Teardown and close
    // ----------------------------------------------------------------------------------------------------

    // Drop the current socket. Publishes stay held back for the next connection.
    private void teardown(boolean forceClose) {
        Generation g = generation;
        generation = null;
        outbound.disconnected();
        if (g != null) {
            teardownGeneration(g, forceClose);
        }
        cleanUpPongQueue();
        clearCurrentServer();
        dataPort = null;
    }

    private void teardownGeneration(Generation g, boolean forceClose) {
        Future<Boolean> writerStopped = g.writer == null ? null : g.writer.stop();
        Future<Boolean> readerStopped = g.reader == null ? null : g.reader.stop(false);
        try {
            if (forceClose) {
                g.port.forceClose();
            }
            else {
                g.port.close();
            }
        }
        catch (IOException e) {
            processException(e);
        }
        long timeoutMillis = options.getConnectionTimeout();
        join(readerStopped, timeoutMillis);
        join(writerStopped, timeoutMillis);
    }

    private void join(@Nullable Future<Boolean> f, long timeoutMillis) {
        if (f != null) {
            try {
                f.get(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                processException(e);
            }
        }
    }

    // Must be called by the holder of the transition guard.
    private void finalClose(boolean forceClose) throws InterruptedException {
        if (isClosed()) {
            return;
        }
        markClosing();
        CompletableFuture<Boolean> waiter = reconnectWait;
        if (waiter != null) {
            waiter.cancel(true);
        }

        teardown(forceClose);
        outbound.close();

        dispatchers.forEach(d -> d.stop(false));
        subscriptions.forEach((sid, subscription) -> subscription.sub.invalidate());
        dispatchers.clear();
        subscriptions.clear();

        if (pingTask != null) {
            pingTask.shutdown();
            pingTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.shutdown();
            cleanupTask = null;
        }

        cleanResponses(true);
        cleanUpPongQueue();

        updateStatus(CLOSED);

        callbackExecutor = null;
        executor = null;
        connectExecutor = null;
        readerExecutor = null;
        writerExecutor = null;
        scheduledExecutor = null;
        options.shutdownExecutors();
    }

    // ----------------------------------------------------------------------------------------------------
    // Outgoing
    // ----------------------------------------------------------------------------------------------------

    private void markClosing() {
        closeRequested = true;
        closing = true; // NatsConnection.isClosing() reads this
    }

    private void requireOpen() {
        if (isClosed()) {
            throw new IllegalStateException("NatsConnection is Closed");
        }
        else if (blockPublishForDrain.get()) {
            throw new IllegalStateException("NatsConnection is Draining"); // Ok to publish while waiting on subs
        }
    }

    // MUST CALL requireOpen() BEFORE CALLING THIS
    private void publishV3(String subject, @Nullable String replyTo, @Nullable Headers headers, byte @Nullable [] data, boolean flush) {
        Headers h = headers == null || headers.isEmpty() ? null : headers;
        if (h != null && !serverInfo.get().isHeadersSupported()) {
            throw new IllegalArgumentException("Headers are not supported by the server, version: " + serverInfo.get().getVersion());
        }
        ConnectionStatus st = getStatus();
        boolean checkReconnectBuffer = st == RECONNECTING || st == DISCONNECTED;
        byte[] payload = data == null ? EMPTY_BODY : data;
        if (!outbound.publish(subject, replyTo, h, payload, flush, checkReconnectBuffer,
            getMaxPayload(), options.getMaxControlLine(), options.clientSideLimitChecks()))
        {
            NatsMessage discarded = new NatsMessage(subject, replyTo, h, payload);
            notifyErrorListener((c, el) -> el.messageDiscarded(c, discarded));
        }
    }

    @Override
    public void publish(@NonNull String subject, byte @Nullable [] data) {
        publish(subject, null, null, data, false);
    }

    @Override
    public void publish(@NonNull String subject, @Nullable Headers headers, byte @Nullable [] data) {
        publish(subject, null, headers, data, false);
    }

    @Override
    public void publish(@NonNull String subject, @Nullable String replyTo, byte @Nullable [] data) {
        publish(subject, replyTo, null, data, false);
    }

    @Override
    public void publish(@NonNull String subject, @Nullable String replyTo, @Nullable Headers headers, byte @Nullable [] data) {
        publish(subject, replyTo, headers, data, false);
    }

    @Override
    public void publish(@NonNull String subject, @Nullable String replyTo, @Nullable Headers headers, byte @Nullable [] data, boolean flushImmediatelyAfterPublish) {
        subject = subjectValidate(subject);
        replyTo = replyValidate(replyTo);
        requireOpen();
        publishV3(subject, replyTo, headers, data, flushImmediatelyAfterPublish);
    }

    @Override
    public void publish(@NonNull NatsMessage message) {
        publish(message, false);
    }

    @Override
    public void publish(@NonNull NatsMessage message, boolean flushImmediatelyAfterPublish) {
        Validator.validateNotNull(message, "Message");
        subjectValidate(message.getSubject());
        replyValidate(message.getReplyTo());
        requireOpen();
        publishV3(message.getSubject(), message.getReplyTo(), message.getHeaders(), message.getData(), flushImmediatelyAfterPublish);
    }

    @Override
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull String subject,
                                                   @Nullable Headers headers,
                                                   byte @Nullable [] data,
                                                   long timeoutMillis,
                                                   @NonNull CancelAction cancelAction,
                                                   boolean flushImmediatelyAfterPublish)
    {
        requireOpen();
        subjectValidate(subject);

        if (inboxDispatcher.get() == null) {
            inboxDispatcherLock.lock();
            try {
                if (inboxDispatcher.get() == null) {
                    NatsDispatcher d = dispatcherFactory.createDispatcher(this, this::deliverReply);

                    // Ensure the dispatcher is started before publishing messages
                    dispatchers.add(d);
                    d.start();
                    d.subscribe(mainInbox);
                    inboxDispatcher.set(d);
                }
            }
            finally {
                inboxDispatcherLock.unlock();
            }
        }

        String responseInbox = createResponseInbox(mainInbox);
        String responseToken = getResponseToken(responseInbox);
        NatsRequestCompletableFuture future =
            new NatsRequestCompletableFuture(cancelAction,
                timeoutMillis <= 0 ? options.getRequestCleanupInterval() : timeoutMillis, options.useTimeoutException());

        responsesAwaiting.put(responseToken, future);
        statistics.incrementOutstandingRequests();

        try {
            publishV3(subject, responseInbox, headers, data, flushImmediatelyAfterPublish);
        }
        catch (RuntimeException e) {
            responsesAwaiting.remove(responseToken);
            statistics.decrementOutstandingRequests();
            throw e;
        }

        statistics.incrementRequestsSent();

        return future;
    }

    @Override
    protected void sendSubscriptionMessage(String sid, String subject, String queueGroup, boolean treatAsInternal) {
        if (!isConnected()) {
            return; // We will set up sub on reconnect or ignore
        }
        if (!outbound.sub(subject, queueGroup, sid, treatAsInternal, options.getMaxControlLine(), options.clientSideLimitChecks())) {
            ByteArrayBuilder bab = new ByteArrayBuilder(UTF_8).append(SUB_SP_BYTES).append(subject);
            if (queueGroup != null) {
                bab.append(SP).append(queueGroup);
            }
            bab.append(SP).append(sid);
            ProtocolMessage pm = new ProtocolMessage(bab, true);
            notifyErrorListener((c, el) -> el.messageDiscarded(c, pm));
        }
    }

    @Override
    protected void sendUnsub(@NonNull NatsSubscription sub, int after) {
        if (!outbound.unsub(sub.getSID(), after)) {
            ByteArrayBuilder bab = new ByteArrayBuilder().append(UNSUB_SP_BYTES).append(sub.getSID());
            if (after > 0) {
                bab.append(SP).append(after);
            }
            ProtocolMessage pm = new ProtocolMessage(bab, true);
            notifyErrorListener((c, el) -> el.messageDiscarded(c, pm));
        }
    }

    @Override
    protected @Nullable CompletableFuture<Boolean> sendPing(boolean treatAsInternal) {
        if (!isConnected()) {
            CompletableFuture<Boolean> retVal = new CompletableFuture<>();
            retVal.complete(Boolean.FALSE);
            return retVal;
        }

        if (!treatAsInternal && !needPing.get()) {
            CompletableFuture<Boolean> retVal = new CompletableFuture<>();
            retVal.complete(Boolean.TRUE);
            needPing.set(true);
            return retVal;
        }

        int max = options.getMaxPingsOut();
        if (max > 0 && pongQueue.size() + 1 > max) {
            handleCommunicationIssue(new IllegalStateException("Max outgoing Ping count exceeded."));
            return null;
        }

        CompletableFuture<Boolean> pongFuture = new CompletableFuture<>();
        pongQueue.add(pongFuture);
        boolean queued;
        try {
            queued = outbound.protocol(OP_PING_BYTES, OP_PING_BYTES.length, true, treatAsInternal, false);
        }
        catch (RuntimeException e) {
            pongQueue.remove(pongFuture); // a future left in the queue would take the next PONG
            throw e;
        }
        if (!queued) {
            NatsMessage pm = new ProtocolMessage(PING_PROTO);
            notifyErrorListener((c, el) -> el.messageDiscarded(c, pm));
            return pingNotQueued(pongFuture);
        }

        needPing.set(true);
        statistics.incrementPingCount();
        return pongFuture;
    }

    @Override
    protected void sendPong() {
        outbound.protocol(OP_PONG_BYTES, OP_PONG_BYTES.length, true, true, true);
    }

    @Override
    protected boolean queueOutgoing(NatsMessage msg) {
        return queueMessage(msg, false);
    }

    @Override
    protected boolean queueInternalOutgoing(NatsMessage msg) {
        return queueMessage(msg, true);
    }

    private boolean queueMessage(NatsMessage msg, boolean internal) {
        validatePayloadAndControlLineSizes(msg);
        boolean queued;
        if (msg.isProtocol()) {
            ByteArrayBuilder bab = msg.getProtocolBab();
            queued = outbound.protocol(bab.internalArray(), bab.length(), msg.isFilterOnStop(), internal, false);
        }
        else {
            Headers h = msg.getHeaders();
            queued = outbound.publish(msg.getSubject(), msg.getReplyTo(), h == null || h.isEmpty() ? null : h,
                msg.getData(), msg.flushImmediatelyAfterPublish, false, getMaxPayload(), options.getMaxControlLine(), false);
        }
        if (!queued) {
            notifyErrorListener((c, el) -> el.messageDiscarded(c, msg));
        }
        return queued;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("NatsConnection is not active.");
        }
        Generation g = generation;
        if (g != null && g.writer != null) {
            try {
                g.writer.flushPort();
            }
            catch (Exception e) {
                // NOOP, as in the classic writer
            }
        }
    }

    // For testing: stop the writer sending, so outgoing data stays queued
    void pauseWriterForTest() {
        outbound.pauseForTest();
    }

    // For testing: undo pauseWriterForTest
    void resumeWriterForTest() {
        outbound.resumeForTest();
    }

    @Override
    public long outgoingPendingMessageCount() {
        return outbound.pendingCount();
    }

    @Override
    public long outgoingPendingBytes() {
        return outbound.pendingBytes();
    }
}
