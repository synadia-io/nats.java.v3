# Plan: TLS context policy in V3

Written 2026-10-08 from the V2 session. Companion to `PLAN_SNI_HOSTNAME_VERIFICATION_V3.md`, which carries the SNI and verification port of nats.java PR #1638. This document carries what the V2 session did about `opentls` (nats.java PR #1639), what is already sitting in this working tree, and the two decisions Scott wants to make for V3, with the measured facts each one rests on. Sections 2 and 3 were the decisions.

**Status 2026-10-09: implemented.** `SSLUtils.createOpenTLSContext()` was removed with the rest; `SSLUtils.setDefaultTrustManagerDelegate` stays (it applies to `createTrustAllTlsContext`).

**Decision 2026-10-08 (Scott): option C.** Remove every recognition of `opentls`: the `opentls://` scheme, `OptionsBuilder.openTls()` / `openTls(boolean)`, and the `openTls` property. A user who wants a trust-all context supplies it with `sslContext(ctx)` and, if the certificate does not name the host, also turns hostname verification off with `tlsVerifyHostname(false)`; a supplied trust-all context does not imply verification off. Documented in a side document in the style of `TLS_CONFIGURATION.md` (nats.java PR #1639). Measured 2026-10-08 from the default branches of nats.go and nats.rs: neither has an `opentls` scheme or a trust-all option; skipping is only a user-built TLS config (Go `InsecureSkipVerify`, Rust a custom rustls `ServerCertVerifier`). Not yet implemented.

## 1. Already in this working tree, uncommitted, from the V2 session on 2026-10-08

- `core/src/main/java/io/synadia/client/OptionsBuilder.java`: after the scheme loop in `build()`, `if (useDefaultTls && useTrustAllTls) throw new IllegalStateException(...)`. It rejects `secure()` with `openTls()`, both properties true, and a bootstrap list that has an `opentls://` server next to a `tls://` or `wss://` server. Before, the trust-all context won in all three cases, so one `opentls://` entry or one property silently removed the verification the other side asked for. One explicit choice is unchanged, `sslContext(ctx)` is used whatever the flags and schemes are, and plain `nats://` mixes with either. Port of nats.java PR #1639, commit `aa96e115`.
- `core/src/test/java/io/synadia/client/OptionsTests.java`: `testSslContextIsProvided` extended with the rejected and accepted combinations. Run from the V2 session: 73 of 73 passed, BUILD SUCCESSFUL.
- `TLS_CONFIGURATION.md` at the repo root, and the `secure` and `openTls` rows of the README options table now link to it instead of saying "See notes on SSL configuration", which pointed at nothing. Section 3 of the file explains the trust-all context, the hand-supplied form, what it removes, what an impersonated server can do, and when it is acceptable.
- In TODO terms these are Under Review. Review with `git diff`; the V2 session will not commit here.

## 2. Decision: hostname verification on by default

Already the position in `PLAN_SNI_HOSTNAME_VERIFICATION_V3.md`: `OptionsBuilder.tlsVerifyHostname(boolean)` defaulting to true, property `tlsVerifyHostname`, `tlsVerifyHostname(false)` the opt-out. To be exact about words: hostname *resolution* already defaults to `ResolveToAll` in V3 and is not changing; this is hostname *verification*, the check that the certificate is issued for the name the connection was made with.

The Go precedent, measured from the nats.go history, is the shape proposed here:

| Date | Release | What nats.go did |
|---|---|---|
| 2012-09-03 | v0.92 | TLS added with `InsecureSkipVerify: true` hard-coded. |
| 2015-10-22 | v1.1.6 | A user `tls.Config` is honored; without one, still skip. Comment: "TODO(dlc) - We should make the more secure version the default." |
| 2016-01-16 | v1.2.0 | `RootCAs(file...)`; `ServerName` set from the URL host, so any connection with a user config verifies chain and hostname. |
| 2018-11-24 | v1.7.0 | `tlsName`: a discovered bare ip carries the gossiping server's hostname, so reconnects have a name to verify. |
| 2018-12-18 | v1.7.2 | "[CHANGED] Don't skip TLS hostname verification": the no-config default becomes `&tls.Config{}`; verification is the default; "User will have to provide their own TLSConfig if skipping is desired." |

Go has no skip switch of its own: the only way to skip is the user's `tls.Config.InsecureSkipVerify`, and that disables the chain check and the name check together.

## 3. Decision: require an explicit SSLContext for openTls

### What V3 does today, measured

`openTls()`, `openTls(true)`, the `openTls=true` property, or an `opentls://` bootstrap scheme with no context: `OptionsBuilder.java:1556` builds `SSLUtils.createTrustAllTlsContext()`. That context accepts any certificate chain and presents no client certificate. `SSLUtils.createTrustAllTlsContext()` and `createOpenTLSContext()` are public (`utils/SSLUtils.java:109`, `:93`), so a caller can also pass the same context through `sslContext(ctx)`; the client cannot tell the two apart. `SSLUtils.setDefaultTrustManagerDelegate` (`:38`) is a process-wide static hook that makes the trust-all manager consult a delegate.

With verification on by default, a trust-all context still gets the name check: the JDK wraps a plain `X509TrustManager` and performs the identity check in the wrapper (measured in V2, `verifyHostnameAppliesUnderTheTrustAllContext`). So "trust all" in V3 would mean any issuer, right name, unless `tlsVerifyHostname(false)` is also set.

### The consideration

V3 stops building a trust-all context on its own. Whoever wants one supplies it with `sslContext(ctx)`, and the insecure choice is a line of code that names what it does, as in Go. Three shapes, from least to most change:

- **A. Keep today's behavior.** Auto trust-all for `openTls` and `opentls://`, documented as insecure in `TLS_CONFIGURATION.md`. Parity in capability with every client; weaker in form, because the `opentls://` scheme lets a configuration value alone select it.
- **B. Require the context, keep the flag and scheme as a TLS trigger.** `openTls()` or `opentls://` without `sslContext(ctx)` fails at `build()`: "openTls requires an SSLContext. For a context that trusts every certificate, which is insecure, pass SSLUtils.createTrustAllTlsContext()." With a context supplied, `openTls` means nothing more than `secure` with that context, so the flag and the property become redundant with `sslContext(ctx)` and are candidates for removal; the `opentls://` scheme would stay accepted as a TLS trigger for URL compatibility.
- **C. Remove `openTls` and the property; reject the scheme.** `opentls://` fails at `build()` with the same message. The insecure path exists only as `sslContext(SSLUtils.createTrustAllTlsContext())`, which is the Go shape exactly. This is the loud choice: an old `opentls://` URL fails at startup with a message, rather than connecting insecurely or, under B with a verifying context, failing later with a certificate error.

Under B or C, decide the fate of `SSLUtils.createOpenTLSContext()` (swallows failures, returns null) and `setDefaultTrustManagerDelegate`. If the helper stays, its name and javadoc should say insecure; `createTrustAllTlsContext` already says what it does.

### Consequences to weigh

- Migration. V2 users who connect with `opentls://` or `opentls()` and no context get a build-time failure under B and C. The migration guide already has the `opentls`/`openTls` rename row at `MIGRATION_GUIDE_OPTIONS.md:38` and `:196`; the entry becomes a behavior note with the one-line replacement.
- Interaction with section 2. Under A, `openTls` plus default-on verification is "any issuer, right name". Under B or C, a supplied trust-all context behaves the same, and `tlsVerifyHostname(false)` is the second half of a full skip. Decide whether that is acceptable or whether a supplied trust-all context should imply verification off, to match `InsecureSkipVerify`; open point 4 of the other plan.
- The both-contexts rejection in section 1 stays under all three; under C the `useTrustAllTls` branch and the `OPENTLS_PROTOCOL` case reduce to the rejection or disappear.
- Tests in this repo that use `openTls` without a context, and would need a supplied trust-all context under B or a rewrite under C: `TLSConnectTests.java:110, 117, 129, 136, 171, 178` (`openTls()` and `PROP_OPEN_TLS`), `WebsocketConnectTests.java:111, 116, 121` (`wsBuilder(ts).openTls()` and `wssBuilder`), `ReconnectTests.java:525` onward ("Test 2. opentls Scheme" against `tls_noip.conf`), `OptionsTests.java:390, 580, 1529, 1533, 1535, 1539-1556` (the property and scheme cases, including the ones added in section 1). No example in `examples/` uses `opentls`.
- Documentation: `TLS_CONFIGURATION.md` sections 1, 3 and 4 and the README rows; `MIGRATION_GUIDE.md` under Core.

### Recommendation from the V2 session

C, with the message naming the replacement. The capability every client has stays available, as one explicit line of code that cannot be reached from a URL string. The V2 side keeps `opentls` because it is a released behavior; V3 is where its shape can change.

### Consequences of the decision

- With `opentls` gone, `NatsUri.KNOWN_PROTOCOLS` no longer lists it, so an `opentls://` URL fails with the existing "Unsupported NATS URI scheme." error.
- The section 1 both-contexts rejection has nothing left to reject: the only way to get a trust-all context is `sslContext(ctx)`, which already wins over the flags and schemes. The uncommitted section 1 change in `OptionsBuilder.build()` and its `OptionsTests` cases are replaced by the removal, not committed first.
- `TLS_CONFIGURATION.md` (untracked, from the V2 session) is rewritten: section 3 becomes "supplying a trust-all context" plus `tlsVerifyHostname(false)`, section 4 (both contexts rejected) is dropped.
- Still open: whether `SSLUtils.createOpenTLSContext()` (swallows failures, returns null) and `SSLUtils.setDefaultTrustManagerDelegate` stay. `createTrustAllTlsContext()` stays, as the supported way to build the context.
- `tlsVerifyHostname(false)` exists only after step C of `PLAN_SNI_HOSTNAME_VERIFICATION_V3.md`.

## 4. Order

After the verification default lands (`PLAN_SNI_HOSTNAME_VERIFICATION_V3.md`), because the openTls decision changes what a trust-all context means under verification. The section 1 work can be committed first on its own; it is independent of both decisions.
