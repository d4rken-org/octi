---
layout: plain
permalink: /privacy
title: "Privacy Policy"
---

# Privacy policy

This is the privacy policy for the Android app "Octi - Multi-Device Monitor" by Matthias Urhahn (darken).

## Preamble

I do not collect, share or sell personal information.

Send a [quick mail](mailto:support@darken.eu) if you have questions.

My underlying privacy principle is the [Golden Rule](https://en.wikipedia.org/wiki/Golden_Rule).

## Permissions 

Some of the more senstive permissions are further explained here.

### Location permission

One of Octi's features allows you to view information about your current WiFi network. This requires location perrmissions (`ACCESS_COARSE_LOCATION`/ `ACCESS_FINE_LOCATION`) on newer Android versions, because theoretically by knowing which WiFi networks are near you, one could determine your location (e.g. Google does this to improve location accuracy).
Granting this permission is optional, but then you won't see things like WiFi names.

### Camera permission

You can use the camera (`CAMERA`) to scan QR-Codes within Octi. Some of the sync services use QR-Codes to link new devices. One device shows a QR-Code with account data, while the other device scans it to be linked to your account.
Camera data is not stored and only used for processing the QR-Code.

### Query installed apps

Octi allows you to see which apps are installed on your other devices, to do this, the `QUERY_ALL_PACKAGES` is required.
This information is only available to you and encrypted when exchanged between devices. Two edge cases exist:
Information about installed apps may be contained in manually generated [debug logs](#debug-log)
and [automatic error reports](#automatic-error-reports).

## Sync services

Octi provides different mechanisms to syncronize data across different devices. The following explains the different
mechanisms and how your data is handled.

### K-Server

K-Server is an end to end encrypted open-source sync server hosted by me.

Synced data can't be viewed by me. Data is encrypted client-side. The encryption key is only available on your devices
and is unknown to the server.

Some meta data like access times and IP addresses are temporarily stored to allow for anti-abuse mechanisms.

Any stored data can be deleted from within the app by deleting your account. If your account is not accessed at least
once within 30 days, your data is also deleted.

### J-Server

*Removed in 0.10.0-rc0*

J-Server is an end to end encrypted open-source sync server hosted by me.

Synced data can't be viewed by me. Data is encrypted client-side. The encryption key is only available on your devices
and is unknown to the server.

Some meta data like access times and IP addresses are temporarily stored to allow for anti-abuse mechanisms.

Any stored data can be deleted from within the app by deleting your account. If your account is not accessed at least
once within 30 days, your data is also deleted.

### Google Drive

You can sync devices using your own Google account. If each devices is logged into the same Google account, then Octi
can exchange information using Google Drive.

Octi can only access it's own files on Google Drive.

If you sync data using Google Drive, only you and Google have access to it.

Google Drive's privacy policy applies:
https://support.google.com/drive/answer/10375054?hl=en

## Automatic error reports

*Removed in 0.8.3*

Anonymous device information may be collected in the event of an app crash or error.

To do this the app uses the service "Bugsnag":
https://www.bugsnag.com/

Bugsnag's privacy policy can be found here:
https://docs.bugsnag.com/legal/privacy-policy/

Error reports contain information related to the error that occured, your device and software versions, e.g. what
happened when the error occured, your phone model, Android version and app version.

You can disable automatic reports in the app's settings.

## Debug log

This app has debug log feature that can be used to assist troubleshooting efforts. It is manually triggered by the user through an option in the app settings. This feature creates a log file that contains verbose output of what the app is doing. The recorded log file can be shared with compatible apps (e.g. your email app) using the system's share dialog. As this log file may contain sensitive information (e.g. your installed applications) it should only be shared with trusted parties.
