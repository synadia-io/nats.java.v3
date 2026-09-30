# nats.java.v3 — ObjectStore

> **Work in progress.**

ObjectStore (OS) client APIs. ObjectStore is built on top of JetStream streams and will live in this project as a standalone module.

## Client Error Messages

Grouped and numbered client errors, each a stable id the message begins with. See the [core README](../README.md#client-error-messages) for how they work. Group: `OS` ObjectStore operations.

`ObjectStoreClientError`

| Name | Group-Code | Description |
|---|---|---|
| `OsObjectNotFound` | OS-90201 | The object was not found. |
| `OsObjectIsDeleted` | OS-90202 | The object is deleted. |
| `OsObjectAlreadyExists` | OS-90203 | An object with that name already exists. |
| `OsCantLinkToLink` | OS-90204 | A link cannot link to another link. |
| `OsGetDigestMismatch` | OS-90205 | Digest does not match metadata. |
| `OsGetChunksMismatch` | OS-90206 | Number of chunks does not match metadata. |
| `OsGetSizeMismatch` | OS-90207 | Total size does not match metadata. |
| `OsGetLinkToBucket` | OS-90208 | Cannot get object, it is a link to a bucket. |
| `OsLinkNotAllowOnPut` | OS-90209 | Link not allowed in metadata when putting an object. |
| `OsCantLinkToDeletedObject` | OS-90210 | Cannot link to a deleted object. |

## Migrating from v2

See the [top-level migration guide](../MIGRATION_GUIDE.md#objectstore) once content lands.
