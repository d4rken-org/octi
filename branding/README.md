# Octi branding

Octi's code is GPL-3.0. **The artwork in this directory is not.** The GPL covers code and grants
no rights to trademarks or logos, which is why this note exists: so the next person building an
Octi client doesn't have to open an issue to ask.

## Files

| File | What it is |
|---|---|
| `octi-icon-512.png` | The app icon, full colour, 512x512 |
| `octi-icon-256.png` | The same icon at 256x256 |
| `octi-icon-mono.svg` | The Octi mark as a monochrome vector, for small or single-colour use |

## What you may do

Use the icon to identify **Octi as the service your project connects to**. Concretely, that covers
things like a `home-assistant/brands` entry, an integration directory listing, or a screenshot of
a client talking to Octi. In those places the icon stands for Octi, so it should look like Octi.

For a Home Assistant brands submission, `octi-icon-512.png` works directly as `icon@2x.png` and
`octi-icon-256.png` as `icon.png`.

## What you may not do

Don't use the icon or the name as **your own project's** logo or wordmark, and don't use either in
a way that suggests the project is official or that I maintain it. Please don't modify the mark,
recolour it, or build a derivative logo from it.

## Naming

Name community clients along the lines of **"&lt;something&gt; for Octi"**, for example "Home Assistant
Integration for Octi". A project called plainly "Octi" reads as first-party, which is the one
thing this is trying to avoid.

Using "Octi" as a technical identifier is fine: a package name, an integration domain, a config
key. The restriction is about the name users see.

## Anything else

If you want to use the artwork some other way, [open an issue](https://github.com/d4rken-org/octi/issues)
and ask. The answer is usually yes.
