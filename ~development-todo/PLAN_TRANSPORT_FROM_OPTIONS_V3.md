# Plan: the transport comes from the options, the pool serves host:port

Written 2026-10-21. Status: implemented 2026-10-21, uncommitted (section 7). Questions Q1-Q9 answered 2026-10-21 (section 3); Q7 and Q9 confirmed against nats-server `f8f6db89` and nats.go `94863ca2` (section 5). Method: reading the V3 code at `57baa409`; nothing was run.

## 1. What I understood

The user chooses one transport for the whole connection. There are four: plain (`nats`), TLS (`tls`), websocket (`ws`) and secure websocket (`wss`). Two ideas are independent: secure or not, websocket or not.

The transport is chosen in two ways, and they must agree:

1. Option setters: `secure()` and a new `ws()`. `secure()` alone is `tls`; `secure()` plus `ws()` is `wss`; `ws()` alone is `ws`; neither is `nats`. There is no `insecure()` and no `secure(boolean)` / `ws(boolean)`: not secure and not websocket are the defaults, and `secure()` / `ws()` only turn them on.
2. The schemes on the bootstrap urls. A bootstrap entry can also have no scheme: `host`, `host:port`, or for websocket `host[:port]/path`. I call that a blank entry. `nats://` counts as a blank entry when the transport is `nats` or `tls`, and is rejected when it is `ws` or `wss` (Q2).

`build()` resolves the transport once and rejects the whole bootstrap list if anything disagrees. After that the scheme is never read from a url again: the pool holds and serves only host:port, and the connection puts the transport on the address when it takes it from the pool.

Resolution when no setter is used:

| Bootstrap contains (besides blanks) | Transport | Blanks become |
|---|---|---|
| nothing but blanks and `nats://` | `nats` | `nats` |
| `tls://` (one or more) | `tls` | `tls` |
| `ws://` | `ws` | `ws` |
| `wss://` | `wss` | `wss` |
| two or more of `tls`, `ws`, `wss` | rejected at `build()` | — |
| `nats://` together with `ws://` or `wss://` | rejected at `build()` | — |

With setters, the setters decide the transport, blanks take it, and every explicit scheme must be allowed for it; anything else is rejected at `build()`:

| Setters | Transport | Allowed explicit schemes | Rejected |
|---|---|---|---|
| `secure()` (or TLS material, Q3) | `tls` | `nats`, `tls` | `ws`, `wss` |
| `secure()` + `ws()` | `wss` | `wss` | `nats`, `tls`, `ws` (Q4) |
| `ws()` | `ws` | `ws` | `nats`, `tls`, `wss` |

Any url with user info (`nats://user:pass@host`, `nats://token@host`) is rejected at `build()` (Q5).

## 2. What changes

### Options

- New `OptionsBuilder.ws()`, plus its property key and README Properties row. `secure(boolean)` is removed; `secure()` stays. No `insecure()`, no `ws(boolean)`.
- Credentials in a url are no longer accepted (Q5). The single-server convenience in the `build()` javadoc (user/password or token taken from the url) and the uri override in `Options.buildProtocolConnectOptionsString` (`Options.java:1158-1161`) are removed.
- `build()` computes the transport and stores it in `Options` (a 4-value enum, for example `Transport { NATS, TLS, WS, WSS }`, with `isSecure()` / `isWebsocket()`).
- A secure transport with no SSL context gets `SSLContext.getDefault()`, as `tls://` and `secure()` do today (`OptionsBuilder.java:1536-1553`).
- The present build check "SSL context with a `ws://` server" (`OptionsBuilder.java:1557-1565`) becomes one case of the new mismatch rule.
- `tlsFirst` still requires a secure transport (`:1567`).

### Server pool

- Pool entries are addresses: host, port, optional path (websocket only, Q6), and the TLS host name carried for a discovered IP (`NatsUri.tlsHost`, from the SNI work). No scheme, no user info. `NatsUri` may become obsolete: the address type plus the options' transport replaces it (Q8).
- `NatsServerPool.defaultScheme` (`:30`, `:74-75`, `:110`) goes away; discovered `connect_urls` are parsed as host:port.
- Equality between entries is host and port.
- `hasSecureServer()` (`ServerPool.java:91`, used for the reconnect delay) is answered by the options instead of the pool.

### Connection

- `tryServers` takes an address from the pool and builds the dial target from the options' transport plus the address. This is a `NatsUri` with the scheme set, so `SocketDataPort.connect` (`:145-150`, websocket and `wss`), `upgradeToSecureIfNeeded` (`NatsConnection.java:711`) and `resolveHost` (`:2457`, no resolve for websocket) keep working unchanged on the dial target.
- `rejectionReason` (`NatsConnection.java:514`) can no longer trigger, because the pool cannot supply a scheme (see Q8).

### Events and accessors

Connection events (`ConnectionListener.connectionEvent`, through `uriDetail`), `getConnectedUrl()` and `getServers()` show the full uri `scheme://host:port[/path]`. The scheme is the transport: `nats` is not secure and not websocket; then `tls`, `ws`, `wss`.

## 3. Questions for you

- **Q1 `insecure()`.** First answer: with `insecure()`, a secure scheme is a reason to reject. Final answer: no `insecure()` and no `secure(boolean)`; not secure is the default and `secure()` turns it on.
- **Q2 `nats://` when the transport is secure.** Answer: `nats://` with `secure()` is fine. `nats://` is the same as no scheme for `nats` and `tls`, but not for websocket: with a `ws` or `wss` transport, a `nats://` entry is rejected.
- **Q3 TLS material without `secure()`.** Answer: yes, it is the same as calling `secure()`.
- **Q4 "secure + wss".** Answer: yes, `secure()` plus `ws()`, or a bootstrap with `wss://`, gives `wss`. `secure()` with a `ws://` entry is rejected.
- **Q5 Credentials in a bootstrap url.** Answer: no longer allowed. A url with user info is rejected at `build()`; credentials come only from `userInfo(...)`, `token(...)` and the other auth options. It can be added back later; it could not be removed later.
- **Q6 Websocket path.** Answer: allowed, it is part of the websocket protocol. It stays on the bootstrap entry and is sent in the HTTP upgrade as today (`SocketDataPort.java:150`). A path on an entry when the transport is not websocket is rejected at `build()` (confirmed).
- **Q7 Default port.** Answer: websocket should not default to 4222. Confirmed (section 5): nats.go uses 80 for `ws`, 443 for `wss`, 4222 for `nats` and `tls`; nats-server has no default websocket port. Same defaults here. Because the port depends on the transport, an entry without a port gets its port after the transport is resolved at `build()`: always 4222 for `nats` and `tls`. A websocket entry also needs a port number to open the socket, so it gets 80 or 443; the HTTP upgrade's `Host` header is the host alone, as today (`WebSocket.java:83`).
- **Q8 `ServerPool` API.** Answer: clean it up. Secureness and protocol come from the options; host[:port][/path] comes from the pool. The pool interface and the connection code change; `NatsUri` may become obsolete. So: a scheme-free address type for `peekNextServer`, `nextServer`, `connectSucceeded`, `connectFailed` and `acceptDiscoveredUrls(..., origin)`; `hasSecureServer()` removed; `ServerPool.serverRejected`, `NatsConnection.rejectionReason` and the rejected-server handling in `tryServers` and `NatsServerPool` removed. `ErrorListener.serverRejected` stays, for a discovered entry that does not parse (Q9).
- **Q9 Discovered entries that are not host:port.** Answer: discovered entries never have a scheme or a path. Confirmed (section 5): every entry is `host:port`. An entry that does not parse as host:port is skipped and reported through `ErrorListener.serverRejected`, because it points to a problem in the system. The connection parses the entries before the pool sees them, so every pool receives only valid addresses.

## 4. Steps

Replaced by section 6.

## 5. Server facts (read in code, nats-server `f8f6db89`, nats.go `94863ca2`)

- `connect_urls` entries are built by `getConnectURLs` (`server/server.go:4167-4198`) as `net.JoinHostPort(host, port)`: bare `host:port`, no scheme, no user info, no path. The host is the `client_advertise` value or the listen host / resolved IPs. `client_advertise` goes through `parseHostPort` (`server/util.go:155-175`); a bad value is fatal at startup (`server.go:2868-2870`). URLs from cluster peers are passed on as built by the peer (`server/route.go:2337, 2421`).
- A websocket client gets the websocket addresses in `connect_urls`: `info.ClientConnectURLs = info.WSConnectURLs` (`server/client.go:2773-2774`); `ws_connect_urls` is cleared for every client (`:2782`). The websocket list is built by the same `getConnectURLs` (`server/websocket.go:1328`), so it is also bare `host:port`. TLS for websocket is signalled by `tls_required` / `tls_available`, not by the url.
- The server serves websocket at `"/"` (`websocket.go:1336-1337`) and uses the path only to pick the connection kind: a leafnode or MQTT suffix, anything else is a client (`:835-841`). A client path therefore matters to proxies in front of the server, not to the server.
- `connect_info` is sent from v2.2.2, on the async INFO after the first PONG, when CONNECT had `protocol >= 1` (`client.go:2837-2857`).
- Default ports: nats-server has none for websocket (it must be configured, `websocket.go:1141-1143`); client port default 4222 (`server/const.go:78`). nats.go: `defaultWSPortString = "80"`, `defaultWSSPortString = "443"`, `defaultPortString = "4222"` (`nats.go:645-647`), applied in `parseServerURL` (`nats.go:2246-2263`).
- nats.go applies the connection's scheme to a discovered url that has none (`connScheme`, `nats.go:2225-2236`), which is the model of this plan.

## 6. The work

Ordered so the build stays green after each item. File references are to `57baa409`.

### 6.1 Options (`core`)

- `OptionsBuilder`:
  - add `ws()` and a `PROP_WEBSOCKET` property;
  - remove `secure(boolean)` (`:527`); `PROP_SECURE` (`:191`) calls `secure()` when true and does nothing when false;
  - `servers(...)` (`:310`) parses each entry into scheme (or none), host, port (or none), path (or none), user info (or none);
  - `build()` (`:1480`):
    - resolve the transport from `secure()`, `ws()`, TLS material and the entry schemes, per the tables in section 1;
    - reject at `build()`: a mismatched scheme, mixed schemes, `nats://` with a websocket transport, a path without a websocket transport, any user info;
    - apply the default port per transport (4222, 80, 443);
    - create the default SSL context for a secure transport, as now;
    - drop the `ws://` + SSL context check (`:1557-1565`), now covered;
    - drop the single-server "user/password or token from the url" behavior from the javadoc (`:1472-1473`).
- `Options`:
  - hold the transport (new enum, e.g. `Transport { NATS, TLS, WS, WSS }`) and the bootstrap list as addresses;
  - `getServers()` (`:674`), `getNatsServerUris()` (`:686`), `getUnprocessedServers()` (`:694`) change to the address list (and transport) or are removed;
  - `buildProtocolConnectOptionsString` drops the "values from URI override options" branch (`:1158-1166`) and its javadoc (`:1103`).
- `OptionsProperties`: the websocket key.

### 6.2 Address type and pool (`core`)

- New `ServerAddress` (host, port, optional path, optional TLS host name for a discovered IP). It replaces `NatsUri` in the pool; `NatsUri` (`utils/NatsUri.java`) is removed once 6.3 is done. Its parsing rules that stay (host, host:port, IPv6 brackets, IP detection, `reHost` for resolved IPs, `tlsHost` carry-over) move to `ServerAddress`.
- `ServerPool` interface: `peekNextServer`, `nextServer`, `connectSucceeded`, `connectFailed` use `ServerAddress`; `acceptDiscoveredUrls(List<String>, origin)` becomes `acceptDiscoveredUrls(List<ServerAddress>, origin)`, receiving only entries the connection already parsed (6.3); remove `serverRejected` (`:78`) and `hasSecureServer` (`:91`).
- `NatsServerPool`, `ServerPoolEntry`:
  - entries are `ServerAddress`; equality is host and port;
  - remove `defaultScheme` (`:30`, `:74-75`, `:110`), `hasSecureServer` (`:28`, `:173-178`, `:313`), the secure-replaces-insecure merge in `initialize` (`:60-70`), and the rejected set (`isRejected`, `:321`);
  - `acceptDiscoveredUrls` no longer parses (it did `new NatsUri(d, defaultScheme)` and dropped failures silently); it keeps, prunes, orders and counts the addresses it is given.
- `ErrorListener.serverRejected(conn, server, reason)` (default no-op) and `ErrorListenerConsoleImpl.serverRejected` stay, with new javadoc: called when a discovered server entry does not parse and is skipped; `server` is the entry as received, `reason` the parse error. A parse error is the only reason a discovered entry is skipped.

### 6.3 Connection and data port (`core`)

- `DataPort.connect(conn, uri, timeoutNanos)` and `connect(conn, uri, unresolvedUri, timeoutNanos)` take `ServerAddress`; the transport comes from `conn.getOptions()`. Implementations: `SocketDataPort` (websocket and `wss` at `:145-150`), `PrefixedDataPort`, and the test data ports (`SimulateSocketDataPortException`, `SocketDataPortBlockSimulator`, and the one in `SocketDataPortProxyHostnameTest`).
- `NatsConnection`:
  - `handleInfo` parses each `connect_urls` string into a `ServerAddress` (decided 2026-10-21: parsing moves from the pool to the connection). Strict: a host name or IP literal and a numeric port in range, nothing else. A failure is reported through `ErrorListener.serverRejected` and skipped (it points to a problem in the system, such as a misconfigured `advertise` value or an INFO not from a correct nats-server); only parsed addresses go to `serverPool.acceptDiscoveredUrls`;
  - `tryServers` / `tryToConnect`: `ServerAddress` in, `rejectionReason` (`:514`), `rejectServer` and the rejected-server branch removed;
  - `upgradeToSecureIfNeeded` (`:711`) and `resolveHost` (`:2457`) read websocket and secure from the transport;
  - the reconnect delay (`:2678`) passes the transport's secureness to `ReconnectDelayHandler.getWaitTimeMillis`, whose signature stays;
  - `uriDetail` (`:2344`), used for every `ConnectionListener.connectionEvent`, shows the full uri `scheme://host:port[/path]`, where the scheme is the transport: `nats` (not secure, not websocket), `tls`, `ws` or `wss`. The two-argument form (`:2348`, `resolved [original]` when a host was resolved to an ip) uses that format for both parts. `getServers()` (`:2445`) and `getConnectedUrl()` (`:2493`) use the same format;
  - `currentServer`, `connectingServer`, `serverAuthErrors` become `ServerAddress`.
- `NatsConnectionV3`: the same in its `tryToConnect`, its reconnect delay (`:438`) and anywhere it holds a `NatsUri`.

### 6.4 Tests

- `OptionsTests`: every row of both tables in section 1; each rejection; default ports; path rules; user info rejected; `PROP_SECURE` true / false; `PROP_WEBSOCKET`.
- `ServerPoolTests`, `CoverageServerPool`, `MaxReconnectResolvedIpsTest` (custom pool): `ServerAddress`. A connection test: an INFO whose `connect_urls` has a bad entry (`h/p:4222`, `user@h:4222`, an empty string) skips it, reports it once through `serverRejected`, and passes the good entries to the pool.
- `TLSConnectTests`: the `ws://` rejection tests (`testDiscoveredPlainWebsocketServerIsRejectedOnce`, `testPlainWebsocketServerFromThePoolIsSkipped`, the two `...KeepsOnlyARejectedServer` tests) are removed or rewritten as build-time rejections.
- `TlsHostnameTests`, `WebsocketConnectTests`, `NatsProvidersAndImplementationsTests`, the `Listener` test utility: follow the API changes.
- Run the connect, reconnect, TLS and websocket classes on V3 and Classic, then ask for a full suite run.

### 6.5 Docs and API notes

- README Properties table: the websocket row; the `secure` row says only `true` has an effect.
- `TLS_CONFIGURATION.md`: how the transport is chosen; the `ws://` rejection section is replaced.
- `MIGRATION_GUIDE.md`: `secure(boolean)` removed; credentials in urls no longer accepted; mixed scheme lists rejected; `nats://` with `ws()` rejected; websocket default ports 80 / 443; `ServerPool`, `DataPort` and `ErrorListener` API changes; `NatsUri` removed.

### 6.6 Not in this work

- Classic and V3 both change, through the shared code above; no behavior differs between them.
- The server-version question of when INFO carries `connect_info` (section 5) does not affect this work.

## 7. As built, where it differs from the tables above

- **One rule instead of two tables.** `build()` computes secure (from `secure()`, any TLS setting, or a `tls://` / `wss://` server) and websocket (from `ws()` or a `ws://` / `wss://` server), takes `Transport.of(secure, websocket)`, then requires every explicit scheme to equal the transport's scheme, with `nats://` accepted for `nats` and `tls`. This gives every row of both tables in section 1, except two setter rows, where it accepts what the table rejected: `ws()` with `wss://a` gives `wss`, and `secure()` (or TLS material) with `wss://a` gives `wss`. That matches the Q4 answer ("a bootstrap with `wss://` gives `wss`") and keeps an SSL context with a `wss://` list working as before.
- **User info is rejected in `servers(...)`, not in `build()`**, as `IllegalArgumentException`, together with every other url parse error.
- **A bad `connect_urls` entry is reported each time an INFO carries it** (2026-10-21, Scott: a once-per-connection set was built, then removed).
- `Transport` constants are `Nats`, `Tls`, `Ws`, `Wss` (the project's enum naming); `ServerAddress` is in `io.synadia.client.utils`, where `NatsUri` was.
- The pool's `getServerList()` returns `host:port[/path]`; `NatsConnection.getServers()` adds the transport's scheme.
- **`ErrorListener.serverIssue`** (2026-10-21, Scott): the reporting callback became general, `serverIssue(conn, server, ServerIssueType, reason, cause)`, called once per incident: `InvalidDiscoveredServer` (the case in this plan), `ConnectAttemptFailed`, `CountedOut`, `DoubleAuthError`. `ServerPool.connectFailed` returns whether the pool dropped the server.

## 8. serverIssue: is the cause the server? (2026-10-21, code reading, not run)

| Type | Called from | Triggered by | Caused by the server? |
|---|---|---|---|
| `InvalidDiscoveredServer` | `NatsConnection.parseDiscovered`, every INFO | a `connect_urls` entry that is not `host:port` | yes: the server sent it (misconfigured `advertise`, or not a correct nats-server) |
| `ConnectAttemptFailed` | `tryServers`, after each failed attempt, unless the connection is closed | any exception that ended the attempt (table below) | sometimes |
| `CountedOut` | `serverConnectFailed` (after a failed attempt in `tryServers`; classic `handleCommunicationIssue`; V3 `handleCommunicationIssue`) | the pool reached `maxReconnects + 1` failures for the server | as much as the failures that counted |
| `DoubleAuthError` | `tryServers`, reconnect mode | the same authentication `-ERR` from the same server twice in a row | the server rejected the credentials; usually the credentials are wrong, sometimes the server's auth config |

`ConnectAttemptFailed` causes:

| Cause | Where | Caused by the server? |
|---|---|---|
| connection refused, socket timeout, host unreachable | `SocketDataPort.connect` | the server or the network (down, wrong address, firewall) |
| hostname does not resolve (`UnknownHostException`) | `SocketDataPort.connect` | no: client DNS or configuration |
| websocket upgrade response wrong (`WebSocket.java:101-150`) | websocket handshake | the server or a proxy in front of it |
| "Received non-info initial message.", "Failed to read initial info message.", read past INFO, EOF | `readInitialInfo` | yes: not a NATS server, or it closed |
| "Server does not support no echo." | `checkVersionRequirements` (`NatsConnection.java:681`) | the server is older than the option needs |
| "SSL connection wanted by client." / "SSL required by server." | `upgradeToSecureIfNeeded` (`:703`, `:708`) | a mismatch between the server's TLS config and the client's transport |
| TLS handshake failure: untrusted chain, hostname mismatch, expired, client certificate rejected | `SocketDataPort.upgradeToSecure` | either: the server's certificate or the client's trust store / certificate |
| `-ERR` in reply to CONNECT (authorization violation, maximum connections, ...) | reader, `processError` | the server refused; for auth, usually the client's credentials |
| no PONG before the connection timeout (`TimeoutException`) | V3 PONG wait, classic future | the server or network is slow, or the timeout is too short |
| "Connection is closing." (V3), interrupted, closed during the attempt before the status is CLOSED | `tryToConnect` | no: the application closed the connection |
| `options.createDataPort()` fails (bad `dataPortType`) | `tryToConnect` | no: client configuration |

Decided 2026-10-21 (Scott): (1) an attempt ended by the application's close is not reported (it is already reported elsewhere): `tryServers` checks `isCloseInProgress()` (classic `isClosed() || isClosing()`, V3 `isClosed() || closeRequested`). (2) A client configuration error seems fatal and should be reported, but not through `serverIssue`: `build()` now creates one instance of a custom `dataPortType` and throws `IllegalStateException` when that fails (class not found, no public no-argument constructor, abstract or interface, not a `DataPort`, the constructor or `afterConstruct` throws). A hostname that does not resolve stays a `ConnectAttemptFailed` (a warning, and the attempt cannot connect). (3) `ConnectAttemptFailed` stays "every other failed attempt", the cause says what went wrong.
