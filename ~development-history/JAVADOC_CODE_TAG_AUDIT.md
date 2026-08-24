# Audit: `<code>` HTML tags in Javadoc → `{@code …}` (code expressions) or reworded prose (parameter names)

Scope: `main` Javadoc across `core`, `jetstream`, `service`, `examples`. Test sources excluded. **26 `<code>` tags on 23 lines across 14 files.** Found with `grep -rn '<code>' --include=*.java` (23 lines); a `grep -o` tag count returns 26 because three lines carry two `<code>…</code>` each — `ServiceBuilder.java:13`, `Endpoint.java:23`, `ServiceEndpoint.java:21` (those rows below list two fixes apiece). One row per line here.

**Not every `<code>` should become `{@code …}`.** Seven of them wrap a bare **parameter name** that is *also an ordinary English word* (`after` ×6, `fallback` ×1). No tag fixes these — "received `after` messages" still reads as the preposition *after*, and `fallback` reads as a plain noun. The right fix is to **reword the sentence** so it's unambiguous (usually by referring to the value indirectly — "that many messages", "the limit", "the fallback value" — rather than dropping the raw parameter token into prose). Concrete rewrites are in the 📝 note. The rest of the tags are genuine code (method calls, API names, subjects, JSON) and stay `{@code …}`.

Beyond that split, three categories need care and are called out below the table: HTML entities inside the tag, a malformed tag pair, and multi-line blocks (where a bare `{@code}` collapses whitespace and — for the JSON examples — must respect brace balance, so the real fix is `<pre>{@code …}</pre>`).

## Table

| File / line | Fix | Original |
|---|---|---|
| `core/…/client/Dispatcher.java:137` | reword 📝 (drop `after`) | `received <code>after</code> messages` |
| `core/…/client/Dispatcher.java:139` | reword 📝 (drop `after`) | `more than <code>after</code> when unsubscribe` |
| `core/…/client/Dispatcher.java:162` | reword 📝 (drop `after`) | `received <code>after</code> messages` |
| `core/…/client/Dispatcher.java:164` | reword 📝 (drop `after`) | `more than <code>after</code> when unsubscribe` |
| `core/…/client/Subscription.java:113` | reword 📝 (drop `after`) | `received <code>after</code> messages` |
| `core/…/client/Subscription.java:115` | reword 📝 (drop `after`) | `more than <code>after</code> when unsubscribe` |
| `core/…/client/utils/WebSocket.java:168` | reword 📝 (drop `fallback`) | `otherwise <code>fallback</code>` |
| `jetstream/…/api/External.java:29` | `{@code $JS.API.CONSUMER.> subjects}` ⚠️entity | `<code>$JS.API.CONSUMER.&gt; subjects</code>` |
| `jetstream/…/api/ExternalCreator.java:69` | `{@code $JS.API.CONSUMER.> subjects}` ⚠️entity | `<code>$JS.API.CONSUMER.&gt; subjects</code>` |
| `jetstream/…/impl/JetStreamPullSubscription.java:89` | `{@code sub.nextMessage(timeout)}` | `<code>sub.nextMessage(timeout)</code>` |
| `jetstream/…/impl/JetStreamPullSubscription.java:108` | `{@code pullExpiresIn}` | `<code>pullExpiresIn</code>` |
| `jetstream/…/impl/JetStreamPullSubscription.java:109` | `{@code sub.nextMessage(...)}` | `<code>sub.nextMessage(...)</code>` |
| `jetstream/…/impl/JetStreamPullSubscription.java:205` | `{@code pullExpiresIn}` | `<code>pullExpiresIn</code>` |
| `service/…/service/ServiceBuilder.java:13` | `{@code builder()}` … `{@code new ServiceBuilder()}` | `<code>builder()</code>` … `<code>new ServiceBuilder()</code>` |
| `service/…/service/Endpoint.java:23` | `{@code builder()} or {@code new Endpoint.Builder()} to get an instance.` ⚠️malformed | `<code>builder()</code> or <code>new Endpoint.Builder() to get an instance.</code>` |
| `service/…/service/ServiceEndpoint.java:21` | `{@code builder()} or {@code new ServiceEndpoint.Builder()} to get an instance.` ⚠️malformed | `<code>builder()</code> or <code>new ServiceEndpoint.Builder() to get an instance.</code>` |
| `service/…/service/InfoResponse.java:19` | `{@code {"id":…,"type":"…info_response"}}` (balanced → OK inline) | `<code>{"id":…,"type":"…info_response"}</code>` |
| `service/…/service/PingResponse.java:7` | `{@code {"id":…,"type":"…ping_response"}}` (balanced → OK inline) | `<code>{"id":…,"type":"…ping_response"}</code>` |
| `service/…/service/Group.java:57` | `<pre>{@code …}</pre>` ⚠️multi-line (Java snippet, no braces) | `<code>` … 6-line snippet … `</code>` (57–63) |
| `service/…/service/StatsResponse.java:18` | `<pre>{@code …}</pre>` ⚠️multi-line JSON (single balanced object) | `<code>` … 34-line JSON … `</code>` (18–52) |
| `service/…/service/EndpointStats.java:17` | see note ⚠️multi-line + **unbalanced** | `<code>` … JSON fragment … `</code>` (17–31) |
| `service/…/service/EndpointStats.java:32` | see note ⚠️multi-line | `<code>` … JSON fragment … `</code>` (32–41) |
| `service/…/service/EndpointStats.java:42` | see note ⚠️multi-line | `<code>` … JSON fragment … `</code>` (42–54) |

## Notes on the special cases

**📝 Parameter names that are also English words → reword, don't just retag (`Dispatcher.java` ×4, `Subscription.java` ×2, `WebSocket.java` ×1).** `after` is the `@param after` of `unsubscribe(String subject, int after)`; `fallback` is the `@param fallback` of `getPathOrDefault(String path, String fallback)`. The trouble isn't the tag — it's that the sentence drops the raw parameter token into prose where it collides with the ordinary word: "already received *after* messages" reads as the preposition, and "otherwise *fallback*" reads as a noun. Fix the wording so it refers to the value indirectly. Suggested rewrites (tags then become unnecessary):

- **Dispatcher / Subscription** (the identical two-sentence block, at `Dispatcher.java:137-140` & `162-165`, `Subscription.java:113-116`) — the method summary already says "…after the specified number of messages", so refer back to it:
  > If the subscription has already received that many messages, it will not receive more. This limit is a lifetime total for the subscription; if it has already received more than the limit when unsubscribe is called, the client will not travel back in time to stop them.
- **WebSocket `getPathOrDefault`** (`WebSocket.java:168`, the `@return`):
  > `@return` the path if it is not null or empty, otherwise the fallback value

If for some reason you keep the parameter token inline instead of rewording, use an italic tag rather than `{@code}` — `<var>after</var>` (semantic "variable" element) or `<em>after</em>` — since a parameter name isn't a code expression. But rewording is the better fix here.

**Entity (`External.java`, `ExternalCreator.java`).** Inside `{@code …}` the content is literal, so the HTML entity `&gt;` must become a literal `>`: `{@code $JS.API.CONSUMER.> subjects}`. (Leaving `&gt;` would render the five characters `&gt;` verbatim.)

**Malformed pair (`Endpoint.java:23`, `ServiceEndpoint.java:21`).** The second `<code>` closes *after* the prose "to get an instance.", so that sentence is currently rendered as code. The fix both converts to `{@code …}` and repositions the boundary so only the constructor call is code:
`via the static method {@code builder()} or {@code new Endpoint.Builder()} to get an instance.`

**Single-line JSON (`InfoResponse.java:19`, `PingResponse.java:7`).** These are one line, so a bare `{@code …}` is fine — no `<pre>` needed. The JSON braces are balanced, so `{@code` finds its matching close correctly (note the doubled `}}` at the end: the JSON's closing `}` plus the tag's closing `}`).

**Multi-line blocks (`Group.java`, `StatsResponse.java`, `EndpointStats.java`).** A bare `{@code …}` collapses newlines (renders on one line), so a faithful conversion is `<pre>{@code …}</pre>` to preserve the layout. Two extra hazards:
- **Brace balance:** `{@code}` only closes at the matching `}`. `Group.java`'s snippet has no braces (safe). `StatsResponse.java` is one balanced JSON object (safe). But `EndpointStats.java` splits one endpoint list across **three** `<code>` blocks, and the **first block (17–31) is unbalanced** (opens `{` and `[{`, closes only one `}`). Wrapping that block alone in `{@code}` would swallow the rest of the doc comment looking for a matching brace.
- **Recommended fix for `EndpointStats.java`:** consolidate the three fragments into a **single** `<pre>{@code …}</pre>` containing one well-formed (brace-balanced) JSON example, rather than three per-block conversions. This is more than a mechanical swap — flag for manual edit.

## Suggested application order

1. The trivial code rows (JetStreamPullSubscription ×4, ServiceBuilder ×1) — pure `<code>x</code>` → `{@code x}`.
2. The two entity rows (External/ExternalCreator) — swap `&gt;` → `>`.
3. The two malformed rows (Endpoint/ServiceEndpoint) — convert + reposition the boundary.
4. The two single-line JSON rows (InfoResponse/PingResponse) — inline `{@code {…}}`.
5. The multi-line blocks (Group, StatsResponse) — `<pre>{@code …}</pre>`.
6. `EndpointStats.java` — manual consolidation into one balanced `<pre>{@code …}</pre>`.
7. 📝 The seven parameter-name rows (Dispatcher ×4, Subscription ×2, WebSocket ×1) — **reword** the sentences (see the 📝 note); not a tag swap.
