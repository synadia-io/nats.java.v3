# NKeys — User Guide and Migration

NKeys are no longer part of the client. In v2 the whole implementation lived in the client jar as `io.nats.client.NKey`. In v3 it lives in its own repository, [nats-io/nkeys.java](https://github.com/nats-io/nkeys.java), published under the group id `io.nats.nkeys`, and the package is `io.nats.nkey`.

The change is not just a move. **The v2 class was a bag of statics that did its own Ed25519 work; the v3 `NKey` is a value object that delegates every cryptographic operation to an `NKeyProvider`, and you choose the provider.** That is why v3 code reads `NKeyProvider.getProvider().createUser()` where v2 read `NKey.createUser(null)`. The provider indirection is the point: the same client can run on stock BouncyCastle, the long-term-support BouncyCastle build, or the FIPS-certified build, and the client library does not have to pick for you.

`jnats3-core` depends on `io.nats.nkeys:core-jdk21` — the API, the base32/CRC encoding, and the abstract `NKeyProvider`. It does **not** depend on a provider implementation, and there is no default. **If you use nkey or creds-file authentication, or touch `NKey` yourself, you must add a provider artifact and name it.** Skipping that step compiles fine and fails at first use — see §3 and §9.

---

## 1. What changed

| | v2 | v3 |
|---|---|---|
| Repository | in the client (`nats.java`) | [`nkeys.java`](https://github.com/nats-io/nkeys.java) |
| Group / artifact | `io.nats:jnats` | `io.nats.nkeys:core-jdk21` + a provider |
| Package | `io.nats.client` | `io.nats.nkey` |
| Entry point | `NKey` statics | `NKeyProvider.getProvider()` instance methods |
| Crypto | BouncyCastle, bundled, not swappable | supplied by the provider you choose |
| Checked exceptions | `GeneralSecurityException`, `IOException` on most methods | none |
| Key type enum | nested `NKey.Type` | top-level `NKeyType` |

The three provider implementations, all in package `io.nats.nkey`:

| Artifact | Provider class | Crypto dependency | Use when |
|---|---|---|---|
| `io.nats.nkeys:regular-jdkNN` | `io.nats.nkey.RegularNKeyProvider` | `org.bouncycastle:bcprov-jdk18on` | the default choice |
| `io.nats.nkeys:lts-jdkNN` | `io.nats.nkey.LtsNKeyProvider` | `org.bouncycastle:bcprov-lts8on` | your organization standardizes on the BouncyCastle LTS line |
| `io.nats.nkeys:fips-jdkNN` | `io.nats.nkey.FipsNKeyProvider` | `org.bouncycastle:bc-fips` | you need FIPS 140 validated crypto |

All three produce identical keys and signatures — they differ only in which BouncyCastle they call. The FIPS provider additionally registers `BouncyCastleFipsProvider` in a static block and takes its `SecureRandom` from `SecureRandom.getInstance("DEFAULT", "BCFIPS")`. If none of the three suit you — most often because you cannot depend on BouncyCastle at all — `NKeyProvider` is public and you can implement your own; see [§2](#if-you-dont-want-bouncycastle).

---

## 2. Add the dependency

`jnats3-core` already brings `core-jdk21` in transitively. You add **one** provider. Match the `jdkNN` suffix to the one `jnats3-core` pins (currently `jdk21`) — the library publishes `jdk17`, `jdk21`, and `jdk25` variants of every artifact under the same version.

### Gradle

```groovy
dependencies {
    // jnats3-core brings io.nats.nkeys:core-jdk21 transitively
    implementation 'io.nats.nkeys:regular-jdk21:3.0.6'
}
```

### Maven

```xml
<dependency>
    <groupId>io.nats.nkeys</groupId>
    <artifactId>regular-jdk21</artifactId>
    <version>3.0.6</version>
</dependency>
```

Swap `regular-jdk21` for `lts-jdk21` or `fips-jdk21` to use a different provider. Keep the nkeys version aligned with whatever `jnats3-core` pins — mixing a newer provider against an older core is not supported.

Use exactly one provider. The three contain classes with the same names in the same package, so with two on the classpath one replaces the other's classes and the provider fails at runtime. From 3.0.6, Gradle builds fail with a capability conflict on `io.nats.nkeys:nkey-provider` when two providers are in the dependency graph; Maven does not check this.

### If you don't want BouncyCastle

You are not obliged to take any of the three. All three shipped providers are ordinary subclasses of the public `NKeyProvider`, and `core-jdk21` — the artifact `jnats3-core` already pulls in — has no crypto dependency of its own. **If your organization cannot take a BouncyCastle dependency, write your own provider against whatever Ed25519 implementation you are allowed to use, and name your class in `NKEY_PROVIDER_CLASS` / `nkey.provider.class` exactly as you would a built-in one.** Nothing else in the client changes.

Subclass `NKeyProvider`, give it a **public no-argument constructor** (`getProvider` instantiates by reflection), and implement four methods:

| Method | What it must do |
|---|---|
| `NKey createNKey(NKeyType type, byte[] seed)` | derive the public key from the 32-byte seed, then build the NKey from the encoded `seed \|\| publicKey` pair |
| `KeyPair getKeyPair(NKey nkey)` | rebuild a `java.security.KeyPair` from the NKey's decoded seed |
| `byte[] sign(NKey nkey, byte[] input)` | Ed25519 signature over `input` |
| `boolean verify(NKey nkey, byte[] input, byte[] signature)` | Ed25519 verification, and it must work for a public-only NKey |

Everything else — `createUser()` and friends, `fromSeed`, `fromPublicKey`, the `SecureRandom`/`Random` accessors — is inherited and needs no work. `createNKey(NKeyType)` (the no-seed overload the `createX()` methods call) is already implemented: it draws 32 bytes from `getSecureRandom()` and hands them to your `createNKey(type, seed)`.

**Two contracts will bite you if you improvise.**

**Do not reimplement the NKey encoding.** base32, the CRC16, the prefix bytes, and the seed layout live in `NKeyProviderUtils` in `core`, and they are public precisely so that providers can call them. Your `createNKey(type, seed)` should concatenate the 32-byte seed with the 32-byte public key and pass the result to `encodeSeed(type, bytes)` — which is what all three built-ins do. Rolling your own encoding produces keys that do not interoperate with the rest of the NATS ecosystem.

**`getKeyPair()` must hand back raw bytes, not DER.** `NKey.getPublicKey()` calls `getKeyPair().getPublic().getEncoded()` and feeds the result straight into `nkeyEncode(...)`, and the built-in `sign` implementations do the same with `getPrivate().getEncoded()`. So `getEncoded()` must return the **raw 32-byte** Ed25519 public key and the **raw 32-byte** seed respectively — *not* an X.509 `SubjectPublicKeyInfo` and *not* a PKCS#8 `PrivateKeyInfo`. This is the trap with a stock JDK `EdECPublicKey`, whose `getEncoded()` returns DER; used directly it yields a public key the server will reject, with no error until authentication fails. Each built-in provider therefore wraps its library's key objects in tiny `PublicKey` / `PrivateKey` classes whose `getEncoded()` returns the raw bytes (with `getAlgorithm()` = `"EdDSA"` and `getFormat()` = `"NATS-NKey"`). Those wrapper classes are package-private, so write your own — they are about a dozen lines each.

**Start from a reference implementation.** `RegularNKeyProvider` is 89 lines and its two key wrappers are 32 lines apiece; copy them and replace the BouncyCastle calls. `LtsNKeyProvider` is the same code against the BouncyCastle LTS artifact. `FipsNKeyProvider` is the more useful second data point — it satisfies the identical contract through a completely different API (`FipsEdEC.computePublicData`, `AsymmetricEdDSAPublicKey`), which shows exactly where the seam is. All three are in the [nkeys.java repository](https://github.com/nats-io/nkeys.java).

The one capability your crypto library actually has to provide is **deriving the 32-byte public key from a 32-byte seed**; signing and verification are otherwise standard Ed25519. If you also need a specific `SecureRandom`, set it from your constructor (§6).

---

## 3. Name the provider

`NKeyProvider.getProvider()` resolves the implementation class by name, in this order:

1. the **environment variable** `NKEY_PROVIDER_CLASS`
2. the **system property** `nkey.provider.class`

The environment variable wins if both are set. The resolved instance is cached in a static field, so the lookup happens once per JVM; `NKeyProvider.clearInstance()` drops it and forces the next `getProvider()` to resolve again (useful in tests, rarely otherwise).

```bash
# environment variable
export NKEY_PROVIDER_CLASS=io.nats.nkey.RegularNKeyProvider
```

```bash
# system property, on the command line
java -Dnkey.provider.class=io.nats.nkey.RegularNKeyProvider -jar myapp.jar
```

```java
// system property, set in code — must run before the first getProvider() call
System.setProperty("nkey.provider.class", "io.nats.nkey.RegularNKeyProvider");
```

Gradle test configuration, which is how this repository does it:

```groovy
test {
    systemProperty 'nkey.provider.class', 'io.nats.nkey.LtsNKeyProvider'
}
```

**Bypassing the lookup.** `NKeyProvider.getProvider(String className)` constructs a provider directly from a class name. It does **not** populate the static instance, so it does not satisfy a later `getProvider()` call — including the ones inside the client's own auth handlers. Use it only when you deliberately want a second, separate provider; otherwise set the environment variable or the system property. Unlike the no-arg form, this overload declares the reflection exceptions (`ClassNotFoundException`, `NoSuchMethodException`, `InstantiationException`, `IllegalAccessException`, `InvocationTargetException`) rather than wrapping them:

```java
try {
    NKeyProvider provider = NKeyProvider.getProvider("io.nats.nkey.RegularNKeyProvider");
}
catch (ReflectiveOperationException e) {
    // the class is missing, is not an NKeyProvider, or has no public no-arg constructor
}
```

You can also construct a provider with `new RegularNKeyProvider()`, but that has the same limitation — nothing else in the process will find it.

**There is no default and no fallback.** If neither the variable nor the property is set, `getProvider()` throws. See §9.

---

## 4. Everyday usage

Every operation that used to be a static on `NKey` is now an instance method on the provider. Everything that used to be an instance method on `NKey` still is.

### Create a key pair

```java
// v2
NKey user = NKey.createUser(null);          // null = "use a default SecureRandom"
NKey acct = NKey.createAccount(mySecureRandom);
```

```java
// v3
NKeyProvider provider = NKeyProvider.getProvider();
NKey user = provider.createUser();
NKey acct = provider.createAccount();
```

`createOperator()`, `createServer()`, and `createCluster()` follow the same shape.

### Load a key from a seed or a public key

```java
// v2
NKey signer   = NKey.fromSeed(seedChars);
NKey verifier = NKey.fromPublicKey(publicKeyChars);
```

```java
// v3
NKeyProvider provider = NKeyProvider.getProvider();
NKey signer   = provider.fromSeed(seedChars);
NKey verifier = provider.fromPublicKey(publicKeyChars);
```

### Sign and verify

```java
// v2 — both declared throws GeneralSecurityException, IOException
byte[] sig = signer.sign(nonce);
boolean ok = verifier.verify(nonce, sig);
```

```java
// v3 — no checked exceptions
byte[] sig = signer.sign(nonce);
boolean ok = verifier.verify(nonce, sig);
```

Verification works on a public-only NKey in v3 exactly as it did in v2.

### Read the key material out

```java
char[] seed      = nkey.getSeed();        // pair only, throws IllegalStateException if public-only
char[] publicKey = nkey.getPublicKey();   // v2: throws GeneralSecurityException, IOException
NKeyType type    = nkey.getType();        // v2 returned NKey.Type
nkey.clear();                             // unchanged
```

New in v3 and worth using: `nkey.isPair()`, `nkey.isPublicOnly()`, and `nkey.ensurePair()` (throws `IllegalStateException` if the key has no private half). They replace the v2 habit of calling `getSeed()` inside a `try` just to find out whether a key could sign.

---

## 5. API mapping

### Factories — static on `NKey` in v2, instance on the provider in v3

Read the v3 column as `NKeyProvider.getProvider().<method>` unless it names another class.

| v2 `io.nats.client.NKey` | v3 |
|---|---|
| `NKey.createUser(SecureRandom)` | `createUser()` |
| `NKey.createAccount(SecureRandom)` | `createAccount()` |
| `NKey.createOperator(SecureRandom)` | `createOperator()` |
| `NKey.createServer(SecureRandom)` | `createServer()` |
| `NKey.createCluster(SecureRandom)` | `createCluster()` |
| `NKey.fromSeed(char[])` | `fromSeed(char[])` |
| `NKey.fromPublicKey(char[])` | `fromPublicKey(char[])` |
| `NKey.isValidPublicUserKey(char[])` | `NKeyUtils.isValidPublicUserKey(char[])` — **semantics changed, see §6** |
| `NKey.isValidPublicAccountKey(char[])` | `NKeyUtils.isValidPublicAccountKey(char[])` |
| `NKey.isValidPublicOperatorKey(char[])` | `NKeyUtils.isValidPublicOperatorKey(char[])` |
| `NKey.isValidPublicServerKey(char[])` | `NKeyUtils.isValidPublicServerKey(char[])` |
| `NKey.isValidPublicClusterKey(char[])` | `NKeyUtils.isValidPublicClusterKey(char[])` |

### Instance methods — same names, no checked exceptions

| v2 | v3 |
|---|---|
| `getSeed()` | unchanged |
| `getPublicKey() throws GeneralSecurityException, IOException` | `getPublicKey()` |
| `getPrivateKey() throws GeneralSecurityException, IOException` | `getPrivateKey()` |
| `getKeyPair() throws GeneralSecurityException, IOException` | `getKeyPair()` |
| `sign(byte[]) throws GeneralSecurityException, IOException` | `sign(byte[])` |
| `verify(byte[], byte[]) throws GeneralSecurityException, IOException` | `verify(byte[], byte[])` |
| `getType()` returns `NKey.Type` | `getType()` returns `NKeyType` |
| `clear()` | unchanged |
| — | `isPair()`, `isPublicOnly()`, `ensurePair()`, `getDecodedSeed()` (new) |

### Types

| v2 | v3 |
|---|---|
| `NKey.Type` (nested enum) | `io.nats.nkey.NKeyType` (top-level enum, same constants) |
| `NKey.Type.fromPrefix(int)` | `NKeyType.fromPrefix(int)` — **returns null instead of throwing, see §6** |
| `NKey.Type.prefix` (private field) | `NKeyType.prefix` (public final `int`) |
| `DecodedSeed` (package-private) | `io.nats.nkey.NKeyDecodedSeed` (public, fields `prefix` and `bytes`) |
| `NKey.PREFIX_BYTE_*`, `ED25519_*` (package-private / private) | `io.nats.nkey.NKeyConstants` (public interface) |

### Encoding helpers — package-private in v2, public API in v3

These were internal to `NKey` (or to the client's `Encoding` class) in v2, so most applications never touched them. They are public in v3 because provider implementations need them.

| v2 | v3 (`io.nats.nkey.NKeyProviderUtils`) |
|---|---|
| `NKey.encode(Type, byte[])` | `nkeyEncode(NKeyType, byte[])` |
| `NKey.encodeSeed(Type, byte[])` | `encodeSeed(NKeyType, byte[])` |
| `NKey.decode(char[])` | `nkeyDecode(char[])` |
| `NKey.decode(Type, char[], boolean safe)` | `nkeyDecode(NKeyType, char[])` — **no `safe` flag; it always throws on mismatch** |
| `NKey.decodeSeed(char[])` | `decodeSeed(char[])` |
| `NKey.crc16(byte[])` | `crc16(byte[])` |
| `NKey.removePaddingAndClear(char[])` | `removePaddingAndClear(char[])` |
| `NKey.notValidPublicPrefixByte(int)` | `notValidPublicPrefixByte(int)` |
| `io.nats.client.support.Encoding.base32Encode(byte[])` | `base32Encode(byte[])` |
| `io.nats.client.support.Encoding.base32Decode(char[])` | `base32Decode(char[])` |

Note the last two rows. The rest of v2's `Encoding` class moved to the `jnats-json` library as `io.nats.json.Encoding`, but **base32 did not go with it** — base32 exists only to encode NKeys, so it lives in `NKeyProviderUtils`. If you were calling `Encoding.base32Encode`, it is in the nkeys library now, not the json one.

---

## 6. Behavior changes — the traps

These four are the ones that compile clean and change what your program does.

**`NKeyUtils.isValidPublicXKey` no longer returns `false`.** In v2 these returned a `boolean` you branched on. In v3 they return `true` or throw `IllegalArgumentException` — the return value carries no information. Code shaped like `if (NKey.isValidPublicUserKey(k)) { … } else { … }` still compiles and the `else` branch becomes unreachable, with the exception escaping instead.

```java
// v2
if (NKey.isValidPublicUserKey(key)) {
    accept(key);
} else {
    reject(key);
}
```

```java
// v3
try {
    NKeyUtils.isValidPublicUserKey(key);
    accept(key);
}
catch (IllegalArgumentException e) {
    reject(key);
}
```

(Note that v2 was already inconsistent here: it returned `false` for a *wrong type* but threw for a malformed string or a bad CRC. v3 throws for both.)

**`NKeyType.fromPrefix` returns `null` for an unknown prefix.** v2's `NKey.Type.fromPrefix` threw `IllegalArgumentException("Unknown prefix")`. v3 returns `@Nullable NKeyType`. Code that relied on the throw now carries a `null` forward to an NPE somewhere later. Check the result. The prefix-to-type mapping is otherwise identical, including `PREFIX_BYTE_PRIVATE` mapping to `ACCOUNT`.

**The `SecureRandom` parameter is gone.** v2's `createUser(SecureRandom)` let you pass an RNG per call (and accepted `null` to mean "make a default one"). v3 takes the RNG from the provider. To control it, subclass the provider and call the protected `setSecureRandom(...)` / `setRandom(...)` in your constructor, then point `nkey.provider.class` at your subclass:

```java
public class MyNKeyProvider extends RegularNKeyProvider {
    public MyNKeyProvider() {
        setSecureRandom(myConfiguredSecureRandom);
    }
}
```

The no-arg constructor is required — `getProvider` instantiates by reflection through `Class.forName(name).getConstructor()`.

**The checked exceptions are gone, so your `catch` blocks may stop compiling.** `sign`, `verify`, `getPublicKey`, `getPrivateKey`, and `getKeyPair` no longer declare `GeneralSecurityException` or `IOException`. A `try` block whose only throwing call was one of these fails to compile with `error: exception ... is never thrown in body of corresponding try statement`. That is the good kind of break — delete the dead `catch`. Nothing became silently uncaught: the failures that used to arrive as checked exceptions now arrive as `IllegalArgumentException` (bad key material) or `IllegalStateException` (using a public-only key where a pair is required), both unchecked, both programming errors.

One smaller difference, no action needed: `clear()` now overwrites the arrays with random letters before zeroing them, where v2 only zeroed.

---

## 7. Worked example — a custom AuthHandler

This is the shape most applications actually need. The v2 and v3 versions differ in two places: where `NKey` comes from, and the absence of a checked-exception `catch`.

```java
// v2
public class MyAuthHandler implements AuthHandler {
    private final char[] seed;
    private final char[] jwt;

    public byte[] sign(byte[] nonce) {
        try {
            NKey nkey = NKey.fromSeed(seed);
            byte[] sig = nkey.sign(nonce);
            nkey.clear();
            return sig;
        }
        catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("problem signing nonce", e);
        }
    }

    public char[] getID() {
        try {
            NKey nkey = NKey.fromSeed(seed);
            char[] pub = nkey.getPublicKey();
            nkey.clear();
            return pub;
        }
        catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("problem getting public key", e);
        }
    }

    public char[] getJWT() { return jwt; }
}
```

```java
// v3
import io.nats.nkey.NKey;
import io.nats.nkey.NKeyProvider;
import io.synadia.client.AuthHandler;

public class MyAuthHandler implements AuthHandler {
    private final char[] seed;
    private final char[] jwt;

    public byte[] sign(byte[] nonce) {
        NKey nkey = NKeyProvider.getProvider().fromSeed(seed);
        byte[] sig = nkey.sign(nonce);
        nkey.clear();
        return sig;
    }

    public char[] getID() {
        NKey nkey = NKeyProvider.getProvider().fromSeed(seed);
        char[] pub = nkey.getPublicKey();
        nkey.clear();
        return pub;
    }

    public char[] getJWT() { return jwt; }
}
```

The client's own built-in handlers — the ones behind `Nats.credentials(...)` and `Nats.staticCredentials(...)` — already do this internally. Their signatures did not change, so if you use them you write no NKey code at all. **You still have to name a provider**, because those handlers call `NKeyProvider.getProvider()` on your behalf.

---

## 8. Migration checklist

1. Add a provider artifact to your build (§2).
2. Set `NKEY_PROVIDER_CLASS` or `nkey.provider.class` everywhere your application runs — including test tasks, CI, containers, and any IDE run configuration (§3).
3. Change `import io.nats.client.NKey` to `import io.nats.nkey.NKey`, and add `io.nats.nkey.NKeyProvider` / `NKeyType` / `NKeyUtils` as needed.
4. Rewrite the static factories as provider calls and drop the `SecureRandom` argument (§5).
5. Rewrite `NKey.Type` as `NKeyType`, and **check every `fromPrefix` call for `null`** (§6).
6. Rewrite every `isValidPublicXKey` branch as a `try`/`catch` (§6).
7. Delete the `catch (GeneralSecurityException | IOException)` blocks the compiler now rejects (§6).
8. If you called `Encoding.base32Encode` / `base32Decode`, repoint them at `NKeyProviderUtils` (§5).
9. Run your authentication path against a real server. A missing provider does not surface until the first sign.

---

## 9. Troubleshooting

**`java.lang.RuntimeException: java.lang.IllegalArgumentException: NKeyProvider class environment variable or system property is not set.`**
Neither `NKEY_PROVIDER_CLASS` nor `nkey.provider.class` was set. See §3. This is the failure you get when the application was migrated but the runtime configuration was not — it is common to hit it first in CI or in a container, because a developer machine picked it up from a shell profile.

**`java.lang.RuntimeException: java.lang.ClassNotFoundException: io.nats.nkey.RegularNKeyProvider`**
The name is set but the provider artifact is not on the classpath. `jnats3-core` only brings `core-jdk21`. See §2.

**`java.lang.RuntimeException: java.lang.NoSuchMethodException: … .<init>()`**
The class you named is not an `NKeyProvider` with a public no-argument constructor. Custom providers must have one (§2, §6).

**Everything works locally, `getProvider()` throws under `gradle test`.**
Gradle test JVMs are forked and do not inherit system properties from the build JVM. Set it on the `test` task (§3).

**`IllegalStateException: Public-only NKey`**
You called `getSeed()`, `getPrivateKey()`, `getKeyPair()`, `sign(...)`, or `getDecodedSeed()` on a key built with `fromPublicKey(...)`. Guard with `isPair()` / `isPublicOnly()`.

---

## 10. Prompts for Claude Code

Drop this `MIGRATION_GUIDE_NKEY.md` file into your project (or pass its path to Claude Code) and use the prompts below.

### 10.1 Migrate NKey source code

```
Update Java source under <PATH> to use the v3 NKey library per MIGRATION_GUIDE_NKEY.md.

Do all of the following:
1. Replace `import io.nats.client.NKey` with `import io.nats.nkey.NKey`. Add imports for
   `io.nats.nkey.NKeyProvider`, `io.nats.nkey.NKeyType`, `io.nats.nkey.NKeyUtils`, and
   `io.nats.nkey.NKeyProviderUtils` only where they are actually used.
2. Rewrite the static factories as provider instance calls using the section 5 table:
   `NKey.createUser(x)` -> `NKeyProvider.getProvider().createUser()`, and likewise for
   createAccount / createOperator / createServer / createCluster / fromSeed / fromPublicKey.
   Drop the SecureRandom argument. If a call site passed a NON-null SecureRandom, do NOT
   silently drop it — leave a `// TODO:` comment naming section 6 ("The SecureRandom
   parameter is gone") and asking for human review, because that needs a provider subclass.
3. Rewrite `NKey.Type` as `NKeyType`. For every `fromPrefix(...)` call, add a null check —
   v3 returns null where v2 threw IllegalArgumentException. Do not assume non-null.
4. Rewrite every `NKey.isValidPublicXKey(...)` call as `NKeyUtils.isValidPublicXKey(...)`
   wrapped in try/catch (IllegalArgumentException). These now return true or throw; a
   boolean branch on the result is a bug. Preserve the original true/false branches as the
   try body and the catch body respectively.
5. Repoint `io.nats.client.support.Encoding.base32Encode` / `base32Decode` at
   `io.nats.nkey.NKeyProviderUtils`. Leave every other `Encoding` method alone — those went
   to `io.nats.json.Encoding`, not to the nkeys library.
6. Delete `catch` clauses for `GeneralSecurityException` and `IOException` that only existed
   for NKey calls — those methods no longer declare them. If the try block has other
   throwing calls, leave the catch alone.
7. Do not change behavior otherwise. Compile and report anything you could not resolve, plus
   every TODO you left.
```

### 10.2 Add the provider dependency and configuration

```
My project uses the NATS Java v3 client and needs an NKey provider per
MIGRATION_GUIDE_NKEY.md sections 2 and 3.

1. Add the provider dependency to my build file, using the `regular` implementation unless
   you find evidence in this repo that it should be `lts` or `fips` (a BouncyCastle LTS or
   BC-FIPS dependency already present, or a FIPS requirement in the docs). Match the jdkNN
   suffix to whatever jnats3-core pins.
2. Set the provider class name for every way this project runs. Find them all — do not stop
   at the first: Gradle/Maven test tasks, application run tasks or plugins, Dockerfiles and
   compose files, CI workflow files, shell launch scripts, and Kubernetes manifests. Use the
   system property `nkey.provider.class` where a JVM arg is natural and the environment
   variable `NKEY_PROVIDER_CLASS` where an env block is natural.
3. List every file you changed and every launch path you found, and call out any launch path
   you could not configure so I can handle it. A missing provider does not fail until the
   first sign, so an unconfigured path is a production outage, not a build error.
```
