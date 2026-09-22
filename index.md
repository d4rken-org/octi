---
layout: home
---

<img src="https://github.com/d4rken-org/octi/raw/main/fastlane/metadata/android/en-US/images/featureGraphic.jpg" width="400">

# Octi - Multi-Device Sync

**Octi - Multi-Device Sync** is a free, open-source Android app for checking your other Android
devices and sending text and files between them.

Install Octi on two or more of your own devices and link them. Each device shows the others' battery
level, WiFi network and clipboard. You can check your tablet's battery without getting up.

Octi is for your own devices. It is not a social app, and there is nothing to publish or share with
other people.

## What Octi shows you

* **Battery**: charge level, charging speed and estimated time until full or empty
* **Connectivity**: WiFi network name, local IP and public IP address
* **Clipboard**: copy text on one device, paste it on another
* **File transfer**: send a file from one of your devices to another
* **Installed apps**: which apps are installed on your other devices, and what was installed most recently
* **Device details**: model, device type, Android version and security patch date, uptime
* **Battery alerts**: get notified when another device drops below a level you pick
* **Home screen widgets**: battery, network and clipboard, without opening the app

Battery, connectivity, WiFi, clipboard, installed apps and file transfer are each a separate module
with its own on/off switch.

## How your devices are linked

Choose how your devices connect:

* **Octi Server**: devices link by scanning a QR code. Your module data and your files are encrypted
  on your device before upload, so the server cannot read their contents. No sign-up, no password,
  no email address.
* **Google Drive**: sign in with the same Google account on each device. Octi stores your device
  status in the hidden, app-private Drive folder that only Octi can see, not in your normal Drive
  files. See [Google user data](/privacy#google-user-data) in the privacy policy for the permissions
  Octi requests and the data it accesses.
* **Your own server**: Octi Server is open source, so you can run it yourself instead of using mine.

You can mix these: some devices on Drive, others on a server.

## Privacy

No ads, no analytics, no tracking, and I do not sell data. The [privacy policy](/privacy) explains
what each module reads, what each sync service receives, and which Google account data Octi
accesses.

## Get Octi

* [Google Play](https://play.google.com/store/apps/details?id=eu.darken.octi)
* [GitHub Releases](https://github.com/d4rken-org/octi/releases/latest)
* [F-Droid (IzzyOnDroid)](https://apt.izzysoft.de/packages/eu.darken.octi/)

Octi is free. An optional in-app purchase unlocks a few extra features and supports development.

## About

I'm Matthias Urhahn (darken). I write and maintain Octi. The source code lives on
[GitHub](https://github.com/d4rken-org/octi) under the GPL-3.0 licence, and the
[sync protocol](/docs/protocol/) is documented for anyone who wants to build their own client.

Questions or problems: [support@darken.eu](mailto:support@darken.eu), or
[join the Discord](https://discord.gg/s7V4C6zuVy).
