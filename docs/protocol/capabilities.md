# Device capabilities

Each device publishes a set of feature tags describing what it supports. Peers read that set instead
of guessing from a version string, which is meaningless across clients with independent release
trains.

## Tag grammar

```
<namespace>:<value>
```

matching the regular expression

```
[a-z][a-z0-9]*:[A-Za-z0-9._\-]+
```

The namespace is lowercase ASCII; the value part additionally allows uppercase, digits, dot,
underscore and hyphen.

### The `_reported` marker

Every producer that participates in a namespace emits a marker tag `<namespace>:_reported` alongside
its value tags.

The marker exists to separate two states that are otherwise indistinguishable. Without it, a missing
`encryption:AES256_GCM_SIV` could mean either "this peer does not support GCM-SIV" or "this peer's
client predates the whole encryption namespace and has told us nothing". Those call for opposite
behavior: the first is a definite incompatibility to warn about, the second is a fall back to
whatever heuristic the consumer has.

## Limits

| Limit | Value |
|---|---|
| Tags per device | 64 |
| Characters per tag | 128 |
| Bytes per header value | 4096 |

The first two are enforced identically on the client and on the server. The third is not. The
Android encoder (`CapabilitiesCodec.encodeToHeader`) validates the tag count, the tag grammar and
the per-tag length, but never measures the serialized header; only the decoder and the server's
header parser check the 4096 limit. A set the encoder accepts can therefore be too long to be
accepted anywhere: 32 tags at the maximum 128 characters serialize to 4193 bytes, which Android
would send and the server would discard whole, leaving the device's previously stored capability
set in place with no error anyone can observe.

A producer must serialize its tag set and check the length of the result before sending it. The
per-tag limits do not keep the header under 4096 on their own.

## Rejection semantics

Validation is **all or nothing**. One tag that is too long, malformed, not a string, or one tag too
many, discards the entire advertised set. There is no partial acceptance, so every consumer sees a
set that is either wholly valid or absent.

What that does **not** mean:

- It does not fail the HTTP request. A request carrying a malformed `Octi-Device-Capabilities`
  header still executes normally; only the capability update is dropped.
- It does not clear a previously stored set. The server applies metadata only for headers that parse,
  so an existing device keeps whatever capability set it last advertised successfully.

The same applies to a header that is simply absent: absent means "no update", never "clear". A
device cannot retract its capability set by omitting the header.

## Authority table

For a peer's capability set, a namespace `X`, and a value tag `<X>:V`:

| Peer's `capabilities` | `<X>:_reported` | `<X>:V` | Verdict |
|---|---|---|---|
| absent or null | n/a | n/a | **Unknown.** The peer reports nothing. Fall back to another heuristic or skip. |
| present | absent | n/a | **Namespace unknown.** The peer says nothing about `X`. Fall back. |
| present | present | absent | **Known unsupported.** The peer speaks `X` and does not support `V`. |
| present | present | present | **Known supported.** |

The distinction between rows one and two matters: a peer can participate in one namespace while
knowing nothing about another. Evaluate authority per namespace, never for the set as a whole.

The Android client applies one fallback for the unknown rows: for peers whose `platform` is `android`
(or absent, for clients predating the field) it compares the reported version against a minimum
version. Peers on other platforms without capabilities are skipped rather than guessed at. That
fallback is Android-specific policy, not part of the protocol.

## Transport

| Direction | Carrier |
|---|---|
| Outbound | `Octi-Device-Capabilities` request header, a JSON-stringified array of tag strings |
| Inbound | The `capabilities` field on each device in `GET /v1/devices`, a real JSON array |

The header is a JSON array serialized into a header value, for example:

```http
Octi-Device-Capabilities: ["encryption:AES256_GCM_SIV","encryption:AES256_SIV","encryption:_reported"]
```

The encoder sorts tags before serializing, so an unchanged set produces a byte-identical header.
Consumers must not depend on that ordering.

In the device list the field is a proper array element, not a string containing an array. It is
omitted entirely for devices that have never reported a valid set. See
[http-api.md](http-api.md).

Google Drive sync carries the same tag set in the `capabilities` field of its device manifest. That
backend is otherwise out of scope here; the point is that the tag vocabulary and semantics are
shared across transports.

## The `encryption` namespace

The only namespace defined at this revision. Values are the encryption mode identifiers from
[encryption.md](encryption.md):

| Tag | Meaning |
|---|---|
| `encryption:_reported` | This peer participates in the namespace |
| `encryption:AES256_GCM_SIV` | This peer can read and write AES-256-GCM-SIV |
| `encryption:AES256_SIV` | This peer can read and write AES-256-SIV |

The Android client always advertises `encryption:_reported` and `encryption:AES256_SIV`, and adds
`encryption:AES256_GCM_SIV` when its runtime check confirms a working AES-GCM-SIV implementation.

The single consumer today is the compatibility check that warns a user when a peer on the account
cannot read what this device writes. Because the account's encryption mode is fixed, that condition
is a permanent incompatibility and not something to retry.

## Trust model

Capabilities live on server-visible device metadata, not inside the encrypted payload. That is
deliberate: the compatibility check has to decide whether a peer can decrypt **before** anything is
decrypted, so the signal must be readable without a key.

The price is that capabilities are advisory and unauthenticated. A hostile or compromised server
could rewrite a peer's tag set, most usefully to suppress a warning. It cannot manufacture
compatibility: a peer that genuinely cannot decrypt still fails at decrypt time. Treat capabilities
as a hint that improves diagnostics, never as an authorization or security decision.

## Adding a namespace

Adding one touches every implementation, since a tag nobody else understands accomplishes nothing.
The procedure, the file-by-file checklist, and the cross-repository coordination are documented in
[`.claude/rules/device-capabilities.md`](../../.claude/rules/device-capabilities.md). In outline:
define the marker and value tags plus a tri-state `supports` helper, publish the tags from the local
capability provider, replace the consumer's version heuristic, extend the tests, then land the
matching declarations in the desktop and web clients. The server needs no change; it stores and
echoes tags without interpreting them.
