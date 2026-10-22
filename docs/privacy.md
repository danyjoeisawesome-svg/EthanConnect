# EthanConnect Privacy Policy

_Last updated: 22 October 2026_

EthanConnect lets you use a PC as a second screen on an Android TV, and control that PC from the TV remote or a phone. It has two parts: **EthanConnect** (the Android TV app) and **EthanConnect PC** (a Windows app).

## Short version

EthanConnect has no accounts, no advertising, no analytics and no servers of its own. **Nothing you do in EthanConnect is sent to the developer or to any third party.** Your screen and your key presses travel only between your own devices on your local network, encrypted.

## What the apps handle

| Data | Where it goes | Kept? |
|---|---|---|
| The picture of the PC screen you choose to share | From your PC to your paired TV or browser over your local network (HTTPS/TLS) | Not stored. Only the current frame exists in memory. |
| Remote-control key presses and phone touchpad / keyboard input | From your TV or phone to your PC over your local network (HTTPS/TLS) | Not stored |
| Pairing information (the PC's address, its certificate fingerprint and a random access token) | Stored on the TV (app-private storage) and on the PC (`%APPDATA%\EthanConnect`) | Until you choose "Forget", clear app data or uninstall |

## Permissions on the TV

* **Network access** - to reach your PC on your local network.
* **Wi-Fi state / multicast** - to receive the PC's local discovery broadcast so the TV can find the PC again if its IP address changes.

The apps do not use the camera, microphone, contacts, location or storage, and do not read other apps' data. As a launcher, the TV app lists the apps installed on the TV so you can open them; this list never leaves the TV.

## Optional second-screen driver (Windows)

If you choose "Set up second screen" in EthanConnect PC, the app asks for your confirmation, then downloads the signed *usbmmidd* virtual-display driver directly from its publisher (amyuni.com) and installs it. That download is a plain file request to the publisher; EthanConnect sends them no information about you.

## Security

Connections use TLS with a self-signed certificate created on your PC. The TV remembers ("pins") the certificate during pairing and refuses to talk to anything else. A new device must be paired with a one-time 6-digit PIN that expires after 5 minutes. You can remove all paired devices from the EthanConnect PC tray menu, or the paired PC from the TV's Settings.

## Children

EthanConnect is not directed at children and collects no personal information from anyone.

## Users in India

EthanConnect is built to be usable in India and processes no personal data on any server of the developer, so there is nothing to disclose, correct or erase on our side. For the purposes of India's Digital Personal Data Protection Act, 2023 and the DPDP Rules, 2025: the developer does not collect or store users' personal data; all pairing data stays on your own TV and PC and can be deleted by you at any time ("Forget" in the TV Settings or the PC tray menu, clearing app data, or uninstalling). If you have a privacy question or complaint, contact the developer through the GitHub issue tracker below and you will receive a reply.

## Changes and contact

If this policy changes, the new version will be published at this address with an updated date. Questions: open an issue at <https://github.com/danyjoeisawesome-svg/EthanConnect/issues>.
