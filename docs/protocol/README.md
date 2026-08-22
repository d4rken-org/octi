# Octi sync protocol

Reference documentation for the wire protocol Octi clients speak to an
[Octi Server](https://github.com/d4rken-org/octi-server). It exists so a third-party client can be
written from this document instead of from another client's source.

The protocol is end-to-end encrypted. The server stores and relays opaque ciphertext and never
holds the encryption keyset.

## Source revisions

Every statement in these pages was read out of two repositories at fixed commits. Both are
recorded because they evolve independently.

| Component | Repository | Commit |
|---|---|---|
| Android client | [`d4rken-org/octi`](https://github.com/d4rken-org/octi) | `8aeacf4c7e5641716c33a6fd77c54ba73ea45fd7` |
| Sync server | [`d4rken-org/octi-server`](https://github.com/d4rken-org/octi-server) | [`7e813e2b7d198daae30bdb3cc17e5544ab9b3c22`](https://github.com/d4rken-org/octi-server/tree/7e813e2b7d198daae30bdb3cc17e5544ab9b3c22) |

Links into server code below are commit-pinned to that server SHA. Links into client code are
repository-relative and therefore track this branch.

## Contents

| Page | Covers |
|---|---|
| [linking.md](linking.md) | Joining an account: share codes, the link payload, registration |
| [http-api.md](http-api.md) | Endpoints, headers, status codes, write semantics, limits |
| [websocket.md](websocket.md) | Change notifications, frame schema, delivery guarantees |
| [encryption.md](encryption.md) | Keysets, modes, associated data, the gzip and AEAD layering |
| [capabilities.md](capabilities.md) | Per-peer feature tags and their authority semantics |
| [stability.md](stability.md) | What is machine-checked, how to pin fixtures, change policy |

[stability.md](stability.md) is the page to read before relying on anything here. It states per
area whether a claim is backed by a committed test fixture or by prose alone.

## Object model

```
account
 └── device            (one registration per client)
      └── module       (one stored document per module id)
```

- An **account** is a UUID. It owns a set of devices and, through them, all stored data.
- A **device** is a UUID chosen by the client. Registration binds it to exactly one account, and a
  device UUID can be registered only once across the whole server.
- A **module** is a namespaced identifier such as `eu.darken.octi.module.core.power`. For every
  pair of (owner device, module id) the server stores exactly **one** document plus its metadata.
- Every device on an account can read every other device's documents. The account is the unit of
  sharing; there is no per-module access control.

A document is an opaque byte string as far as the server is concerned. Its plaintext contents are
a module-specific structure that only the account's devices can decrypt.

## Single-writer invariant

Every write targets a slot identified by `(target device id, module id)`. The server authorizes any
authenticated device on the account to write any peer's slot. Clients must not use that freedom: a
client writes only the slot belonging to its own device id and treats every other device's slots as
read-only. The Android client enforces this locally and refuses to send a write with a foreign
device id.

Two consequences matter for implementers:

- Data flows one way per slot. A peer publishes, everyone else observes. There is no merge.
- Because a slot holds one document that all peers read, a producer cannot serve different document
  formats to different peers. See the change policy in [stability.md](stability.md).

## Scope of this revision

Specified here:

- The Octi Server HTTP backend and its WebSocket notification channel.
- The payload encryption envelope and the linking payload.
- The device capability tag set.

Not specified here:

- **Google Drive sync.** Octi also synchronizes through a user's Google Drive app-data folder. That
  backend is out of scope; only the Octi Server backend is described.
- **The blob / file-transfer layer.** Endpoints under `/v1/module/{moduleId}/blobs` and
  `/v1/module/{moduleId}/blob-sessions` and their streaming encryption exist and are named in
  [http-api.md](http-api.md) and [encryption.md](encryption.md), but their request and response
  shapes are not specified in this revision. A client that never attaches blobs does not need them.
- **Module document schemas.** The inner structure of the seven known modules' documents is not
  described, only the envelope around them. Unknown module ids must be tolerated, not rejected.

## Conventions

- Every identifier, header name, field name, status code, and limit was read out of the sources at
  the commits above. Where the Android client and the server disagree, the server's behavior is
  documented and the divergence is called out on the spot.
- Values marked as **deployment-configurable defaults** come from the server's command-line
  configuration. They are what a stock deployment does, not protocol constants. An operator can
  change them, so a client must handle the corresponding error rather than assume a number.
- All credentials, codes, keys, and identifiers in examples are obviously fake placeholders.
