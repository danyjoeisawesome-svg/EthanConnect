# EthanConnect

Use your PC as a wireless **second screen on your Android TV**, control it with the TV remote or your phone, and keep everything on your own network - no accounts, no cloud, no ads.

```
 Windows PC (EthanConnect PC)  <--- Wi-Fi / LAN, TLS --->  Android TV (EthanConnect)
   capture one monitor  ───────── MJPEG video ────────────▶  shows it full-screen
   mouse / keyboard     ◀──────── remote keys ─────────────  D-pad = mouse, OK = click
        ▲
        └──── phone browser: /remote (trackpad + keyboard) and /view (any screen)
```

## Parts

| Folder | What | Build output |
|---|---|---|
| `android/` | TV app + optional launcher (package `com.ethanconnect.tv`) | `app-release.aab` (Play) / `.apk` (sideload) |
| `pc/` | Windows tray app: capture, TLS server, pairing, input, virtual display helper | `dist/EthanConnect.exe` |
| `installer/` | Inno Setup script for a normal Windows installer | `dist/EthanConnect-Setup-1.0.0.exe` |
| `store/` | Play listing text, data-safety answers, generated graphics | - |
| `docs/` | Privacy policy (publish with GitHub Pages: Settings > Pages > `/docs`) | - |

## Using it

1. Install and run **EthanConnect PC** on Windows. Its tray icon shows a pairing window with a 6-digit PIN and a QR code.
2. **TV:** open *Second Screen* > *Pair with PC*. The PC's address is pre-filled when it is found on the network. Enter the PIN (use Tab/Next between the fields, Done to pair).
3. **Phone:** scan the QR code in the pairing window. The page pairs itself and becomes a touchpad + keyboard (`/remote`). `/view` shows the screen in any browser. Accept the one-time certificate warning - the certificate is created on your PC.
4. **Real second screen (optional):** tray menu > *Set up second screen*. This installs the signed *usbmmidd* virtual display driver (Windows asks for permission); then set Windows Display to *Extend*. The app streams the virtual monitor automatically.

TV remote: D-pad = mouse, OK = click, hold OK = right-click, CH+/CH- = scroll, Back = leave, Menu = re-pair.

## Security model

* TLS with a self-signed certificate; the TV pins its SHA-256 fingerprint during pairing and refuses other certificates.
* Pairing needs a one-time PIN (5 minutes, 5 attempts). The TV sends `HMAC(PIN, fingerprint-it-saw)`, so a man-in-the-middle with a different certificate cannot obtain a token. Browsers cannot pin a certificate, so they send the PIN inside the TLS tunnel instead.
* Every request needs the per-device token. "Forget paired TVs" in the tray revokes them all.
* The PC listens on TCP 8443 and broadcasts a UDP discovery beacon (port 8082) containing only the PC's address, certificate fingerprint prefix and name.
* Limitations: anyone on your LAN who can read the PIN window within 5 minutes could pair; the video is JPEG frames (no DRM content handling); input is sent as the logged-in Windows user, so keep the PC locked when unattended.

## Build

```powershell
# TV app (JDK 17+ and the Android SDK, API 34)
cd android
.\gradlew.bat assembleDebug                      # debug APK
.\gradlew.bat bundleRelease assembleRelease      # needs keystore.properties (see below)

# PC app (Python 3.9+)
pip install pyinstaller pystray cryptography pillow mss qrcode
cd pc
pyinstaller --noconfirm --clean --onefile --noconsole --name EthanConnect --icon ethanconnect.ico `
  --version-file version_info.txt --hidden-import web_pages --hidden-import pystray._win32 ethanconnect_pc.py

# Installer (Inno Setup 6)
iscc installer\EthanConnect.iss

# Graphics
python store\make_assets.py
```

**Signing:** create an upload key and `android/keystore.properties` (never commit either):

```
storeFile=ethanconnect-upload.jks
storePassword=...
keyAlias=ethanconnect
keyPassword=...
```

`keytool -genkeypair -v -keystore ethanconnect-upload.jks -alias ethanconnect -keyalg RSA -keysize 2048 -validity 10000`

Back the keystore up. Use Play App Signing so Google holds the app signing key and this is only the upload key.

## Play Store checklist

- [x] Package id `com.ethanconnect.tv`, versionCode 1 / 1.0.0, targetSdk 34 (Android TV minimum per Play policy - re-check the current requirement before each release)
- [x] Leanback launcher intent filter, TV banner, `android.software.leanback` required, touchscreen not required
- [x] Only INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE; cleartext traffic disabled
- [x] Signed release AAB, minified, no third-party SDKs
- [x] Icon 512, feature graphic 1024x500, TV banner, TV screenshots (`store/play/`)
- [x] Privacy policy (`docs/privacy.md`), data-safety answers (`store/data-safety.md`), listing text (`store/listing.md`)
- [ ] Publish the privacy policy at a public URL (GitHub Pages) and paste it into Play Console
- [x] TV screenshots (`store/play/screenshots/`): mirror, pairing, settings, inputs - no third-party logos or personal content
- [ ] Publish the PC installer under GitHub Releases and link it in the listing; give reviewers the "App access" text from `store/listing.md`
- [ ] Create the Play Console developer account, complete content rating + data safety + target audience, run an internal test on a real TV, then production
- [ ] Test on at least one other Android TV (different vendor) - this build has only been run on a Xiaomi Mi TV (Android 9)

## License

MIT - see `LICENSE`. The optional virtual display driver (usbmmidd) is a separate product by Amyuni, downloaded from their site on request and not redistributed here.
