# EthanConnect — Community Launch Kit & Distribution Guide

This document contains ready-to-use launch templates, post headlines, and distribution strategies to bring traffic and stars to **EthanConnect** on GitHub.

---

## 1. GitHub Repository Settings (Do This First)

### A. Repository Description (About section)
> **Description:** Turn your Android TV into a wireless second monitor for Windows. Local Wi-Fi, TLS encrypted, zero cloud, TV remote as mouse, and phone trackpad companion. Open-source Spacedesk alternative.
> **Website URL:** `https://github.com/danyjoeisawesome-svg/EthanConnect` (or your GitHub Pages site)

### B. Topics / Tags (Gear icon under "About")
Copy-paste these exact tags into the topics input:
```text
android-tv, second-screen, virtual-display, screen-mirroring, spacedesk-alternative, windows, android, remote-desktop, lan-streaming, open-source, python, productivity, display-driver
```

### C. Social Preview (Link Card Image)
1. Go to **Settings** > **General** > **Social preview**.
2. Click **Edit** > **Upload an image**.
3. Upload `store/play/tv-banner-1280x720.png` or a screenshot showcasing both the TV and PC.

---

## 2. Reddit Launch Posts

### Subreddit 1: `r/AndroidTV`
* **Title:** I built EthanConnect: An open-source, local-only way to turn your Android TV into a wireless second PC monitor (with TV remote mouse control)
* **Post Content:**
```markdown
Hey r/AndroidTV!

I was tired of proprietary second-screen apps (like Spacedesk) or laggy casting protocols that require accounts or cloud servers. I wanted a simple way to use my Android TV as an extra Windows monitor with zero telemetry and without plugging a physical mouse into the TV.

So I built **EthanConnect**, completely free and open-source:

**What it does:**
- 🖥️ **Real extended monitor:** Creates a virtual display on Windows (`Extend desktop`), so your TV acts as a genuine extra screen, not just a mirror.
- 🎯 **TV remote as mouse:** Use your regular TV remote D-pad to move the mouse cursor, OK button to click, long-press OK to right-click, and channel buttons to scroll.
- 📱 **Phone trackpad (No app needed):** Scan a QR code from the PC tray, and your phone's browser instantly becomes a low-latency touchpad and keyboard.
- 🔒 **100% Local & Encrypted:** TLS self-signed with SHA-256 certificate pinning and a 6-digit PIN. Nothing leaves your Wi-Fi/LAN, no accounts, no cloud, no ads.
- 🚀 **Auto-discovery:** The TV automatically discovers the PC on your local Wi-Fi.

The project is fully open source (MIT):
GitHub: https://github.com/danyjoeisawesome-svg/EthanConnect

Pre-compiled Windows `.exe` and Android TV `.apk` are available under GitHub Releases.

Would love any feedback, suggestions, or bug reports!
```

---

### Subreddit 2: `r/selfhosted` & `r/opensource`
* **Title:** EthanConnect: Turn any Android TV into a private, local-only wireless second monitor for Windows (TLS pinned, zero cloud, open source)
* **Post Content:**
```markdown
Hi everyone,

Sharing an open-source tool I built called **EthanConnect**.

Most wireless display solutions either require proprietary drivers with closed accounts, route traffic through third-party servers, or inject ads. I wanted something strictly local, private, and self-contained.

**Core features:**
* Streams Windows display (including a virtual second monitor via signed driver) over local Wi-Fi/LAN.
* TV remote D-pad translates to mouse & keyboard input on Windows.
* Instant phone companion web app: scan QR code to use your phone as a trackpad (zero install required on the phone).
* Security model: TLS with self-signed certificate, TV pins the SHA-256 fingerprint, and pairing is gated behind a 5-minute PIN with HMAC verification. No external connections.

GitHub: https://github.com/danyjoeisawesome-svg/EthanConnect
License: MIT

Feedback and PRs are very welcome!
```

---

### Subreddit 3: `r/pcmasterrace` / `r/windows`
* **Title:** Don't let your spare TV go to waste: I made a free open-source app to turn your Android TV into a wireless PC monitor with remote control
* **Post Content:**
```markdown
If you have an Android TV near your desk or couch and wish you could use it as a wireless secondary display for discord, monitoring, or videos, I built a free open-source project called **EthanConnect**.

It lets you extend your Windows desktop to your Android TV with:
- Full TV remote control (D-pad moves cursor, OK clicks, CH scrolls)
- Phone trackpad & keyboard companion (just scan the QR code, no phone app needed)
- Completely free, open source, no accounts, and no laggy cloud relay

Check it out here: https://github.com/danyjoeisawesome-svg/EthanConnect
```

---

## 3. Hacker News — "Show HN"

* **Title:** Show HN: EthanConnect – Turn an Android TV into a wireless PC monitor with remote control
* **URL:** `https://github.com/danyjoeisawesome-svg/EthanConnect`
* **Text / Comment:**
```markdown
Hi HN,

I built EthanConnect to turn any Android TV into a wireless second screen for Windows over local Wi-Fi.

Why another display streamer?
1. Privacy & Local Only: Most commercial solutions (Spacedesk, AirServer) are closed-source or call home. EthanConnect runs purely over LAN using TLS with SHA-256 certificate pinning and a one-time pairing PIN.
2. TV-First Input: Instead of requiring a wireless mouse paired to the TV, the TV remote's D-pad directly controls the PC cursor, with channel buttons for scrolling.
3. Phone Companion without installation: Scanning a local QR code opens an HTML5 canvas trackpad and keyboard directly in your mobile browser.
4. Virtual Display Driver: Integrates with the Amyuni virtual display driver to create an authentic extended desktop (not just mirror mode).

The TV app is built with Java (Leanback), and the PC client is Python/PyInstaller.

Code & pre-built binaries: https://github.com/danyjoeisawesome-svg/EthanConnect

Would love to hear your thoughts and suggestions!
```

---

## 4. AlternativeTo.net Listing

Go to [AlternativeTo.net/software/new](https://alternativeto.net/) and submit:
* **Name:** EthanConnect
* **Website:** https://github.com/danyjoeisawesome-svg/EthanConnect
* **License:** Open Source (MIT)
* **Platforms:** Windows, Android TV
* **Tagline:** Open-source wireless second monitor for Android TV with remote control.
* **Alternatives to:**
  * Spacedesk
  * Deskreen
  * Duet Display
  * Air Display

---

## 5. Curated Awesome Lists (Submit PRs)

Submit PRs adding EthanConnect under:
1. `awesome-selfhosted` (under Multimedia / Screen Sharing)
2. `awesome-android`
3. `awesome-windows`
