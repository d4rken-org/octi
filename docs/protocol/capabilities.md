# Device capabilities

Each device publishes a set of feature tags describing what it supports. Peers read that set
instead of guessing from a version string.

## Tag grammar

```
<namespace>:<value>
```

matching the regular expression

```
[a-z][a-z0-9]*:[A-Za-z0-9._\-]+
```

The namespace is lowercase ASCII. The value part also allows uppercase, digits, dot, underscore and
hyphen.

### The `_reported` marker

Every producer that participates in a namespace emits a marker tag `<namespace>:_reported` next to
its value tags.

The marker separates two states that would otherwise look identical. Without it, a missing
`encryption:AES256_GCM_SIV` could mean "this peer does not support GCM-SIV" or "this peer's client
predates the encryption namespace and has told us nothing". The first is a definite incompatibility
to warn about, the second a fall back to whatever heuristic the consumer has.

## Limits

| Limit | Value |
|---|---|
| Tags per device | 64 |
| Characters per tag | 128 |
| Bytes per header value | 4096 |

Android checks the first two limits when it encodes (`CapabilitiesCodec.encodeToHeader` validates
the tag count, the tag grammar and the per-tag length), but never measures the serialized header.
Only decoders and the server's header parser check the 4096 limit.

So Android can send a header the server then throws away. 32 tags of 128 characters come to 4193
bytes. The server keeps the old capability set and returns no error.

Measure the serialized array yourself before sending it. The per-tag limits do not keep the header
under 4096 on their own.

## Rejection semantics

Validation is all or nothing. One tag that is too long, malformed, not a string, or one tag too
many, discards the entire advertised set. Every consumer sees a set that is either wholly valid or
absent.

Rejection does not fail the HTTP request. A request carrying a malformed
`Octi-Device-Capabilities` header still executes normally; only the capability update is dropped.
It does not clear a previously stored set either, because the server applies metadata only for
headers that parse. A device keeps whatever set it last advertised successfully.

An absent header also means "no update", never "clear". A device cannot retract its capability set
by omitting the header.

## Authority table

For a peer's capability set, a namespace `X`, and a value tag `<X>:V`:

| Peer's `capabilities` | `<X>:_reported` | `<X>:V` | Verdict |
|---|---|---|---|
| absent or null | n/a | n/a | **Unknown.** The peer reports nothing. Fall back to another heuristic or skip. |
| present | absent | n/a | **Namespace unknown.** The peer says nothing about `X`. Fall back. |
| present | present | absent | **Known unsupported.** The peer speaks `X` and does not support `V`. |
| present | present | present | **Known supported.** |

A peer can participate in one namespace while knowing nothing about another. Evaluate authority per
namespace, never for the set as a whole.

The Android client has one fallback for the unknown rows: for peers whose `platform` is `android`
(or absent, for clients predating the field) it compares the reported version against a minimum
version. Peers on other platforms without capabilities are skipped, not guessed at. That fallback
is Android policy, not protocol.

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
backend is out of scope here, but the tag vocabulary and semantics are shared across transports.

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

The one consumer today is the check that warns a user when a peer on the account cannot read what
this device writes. The account's encryption mode is fixed, so that is a permanent incompatibility,
not something to retry.

## Trust model

Capabilities live on server-visible device metadata, not inside the encrypted payload. The
compatibility check has to decide whether a peer can decrypt before anything is decrypted, so the
signal must be readable without a key.

The price is that capabilities are advisory and unauthenticated. A hostile or compromised server
could rewrite a peer's tag set, most usefully to suppress a warning. It cannot manufacture
compatibility: a peer that cannot decrypt still fails at decrypt time. Treat capabilities as a
diagnostic hint, never as an authorization or security decision.

## Adding a namespace

Adding one touches every implementation, since a tag nobody else understands accomplishes nothing.
The procedure, the file-by-file checklist and the cross-repository coordination live in
[`.claude/rules/device-capabilities.md`](../../.claude/rules/device-capabilities.md). In outline:
define the marker and value tags plus a tri-state `supports` helper, publish the tags from the
local capability provider, replace the consumer's version heuristic, extend the tests, then land
the matching declarations in the desktop and web clients. The server needs no change; it stores and
echoes tags without interpreting them.
