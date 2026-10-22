#!/usr/bin/env python3
"""
EthanConnect PC: share a Windows display with the EthanConnect TV app over your local network.

* Streams one monitor (the virtual "second screen" if present, else the primary) as MJPEG.
* Receives remote-control key presses from the TV and turns them into mouse / keyboard input.
* Everything is local: TLS with a self-signed certificate that the TV pins, a one-time PIN to
  pair a TV, and a random token per paired TV. Nothing is sent to the internet.
"""
import ctypes
import ctypes.wintypes
import hashlib
import hmac
import io
import json
import os
import secrets
import socket
import ssl
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

APP = "EthanConnect"
VERSION = "1.0.0"
PORT = 8443
DISCOVERY_PORT = 8082
BEACON_MAGIC = "ETHANCONNECT1"
JPEG_QUALITY = 75
FRAME_INTERVAL = 1 / 30
MAX_WIDTH = 1920
PIN_LIFETIME = 300
PIN_MAX_ATTEMPTS = 5
DISPLAY_DRIVER_URL = "https://www.amyuni.com/downloads/usbmmidd_v2.zip"
TASK_NAME = "EthanConnect Virtual Display"

DATA_DIR = Path(os.environ.get("APPDATA", str(Path.home()))) / APP
PROGRAM_DIR = Path(os.environ.get("PROGRAMDATA", "C:/ProgramData")) / APP
DRIVER_DIR = PROGRAM_DIR / "usbmmidd_v2"


def _set_dpi_aware():
    """Per-monitor-v2 so every coordinate is a true physical pixel (monitors can differ in scaling)."""
    try:
        ctypes.windll.user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    except Exception:
        ctypes.windll.user32.SetProcessDPIAware()


_set_dpi_aware()


def log(*args):
    try:
        DATA_DIR.mkdir(parents=True, exist_ok=True)
        with open(DATA_DIR / "log.txt", "a", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + " ".join(str(a) for a in args) + "\n")
    except Exception:
        pass


# --------------------------------------------------------------------------- identity / pairing

def load_or_create_cert():
    """Self-signed EC certificate, created once. Returns (cert_path, key_path, sha256 fingerprint hex)."""
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID
    import datetime

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    cert_path, key_path = DATA_DIR / "cert.pem", DATA_DIR / "key.pem"
    if not (cert_path.exists() and key_path.exists()):
        key = ec.generate_private_key(ec.SECP256R1())
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, f"{APP} ({socket.gethostname()})")])
        now = datetime.datetime.utcnow()
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                .public_key(key.public_key()).serial_number(x509.random_serial_number())
                .not_valid_before(now - datetime.timedelta(days=1))
                .not_valid_after(now + datetime.timedelta(days=3650))
                .sign(key, hashes.SHA256()))
        key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                                               serialization.PrivateFormat.PKCS8,
                                               serialization.NoEncryption()))
        cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    cert = x509.load_pem_x509_certificate(cert_path.read_bytes())
    fp = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    return cert_path, key_path, fp


class Pairing:
    """One-time PIN (valid 5 minutes, 5 tries) and the persistent list of paired-TV tokens."""

    def __init__(self, fingerprint):
        self.fp = fingerprint
        self.lock = threading.Lock()
        self.pin = None
        self.pin_expires = 0.0
        self.attempts = 0
        self.tokens = {}
        self.path = DATA_DIR / "paired.json"
        try:
            self.tokens = json.loads(self.path.read_text())
        except Exception:
            pass

    def _save(self):
        self.path.write_text(json.dumps(self.tokens))

    def new_pin(self):
        with self.lock:
            self.pin = f"{secrets.randbelow(1000000):06d}"
            self.pin_expires = time.time() + PIN_LIFETIME
            self.attempts = 0
            if os.environ.get("ETHANCONNECT_DEBUG"):
                log("DEBUG pin", self.pin)
            return self.pin

    def current_pin(self):
        with self.lock:
            if self.pin and time.time() < self.pin_expires:
                return self.pin, int(self.pin_expires - time.time())
            return None, 0

    def try_pair(self, proof, tv_name):
        """proof = HMAC-SHA256(key=PIN, msg=fingerprint the TV saw). Binding to the fingerprint means a
        man-in-the-middle presenting a different certificate cannot obtain a token."""
        with self.lock:
            if not self.pin or time.time() >= self.pin_expires or self.attempts >= PIN_MAX_ATTEMPTS:
                return None
            self.attempts += 1
            expected = hmac.new(self.pin.encode(), bytes.fromhex(self.fp), hashlib.sha256).hexdigest()
            if not hmac.compare_digest(expected, str(proof)):
                return None
            token = secrets.token_urlsafe(32)
            self.tokens[token] = {"name": str(tv_name)[:40], "created": int(time.time())}
            self.pin = None
            self._save()
            return token

    def try_pair_pin(self, pin, device_name):
        with self.lock:
            if not self.pin or time.time() >= self.pin_expires or self.attempts >= PIN_MAX_ATTEMPTS:
                return None
            self.attempts += 1
            if not hmac.compare_digest(self.pin, str(pin)):
                return None
            token = secrets.token_urlsafe(32)
            self.tokens[token] = {"name": str(device_name)[:40], "created": int(time.time())}
            self.pin = None
            self._save()
            return token

    def valid(self, token):
        ok = False
        for t in list(self.tokens):  # constant-time compare against each, no early exit
            ok |= hmac.compare_digest(t, token or "")
        return ok

    def forget_all(self):
        with self.lock:
            self.tokens = {}
            self._save()


# --------------------------------------------------------------------------- screen capture

current_frame = None
frame_lock = threading.Lock()
monitor_info = {"left": 0, "top": 0, "width": 1920, "height": 1080}
settings = {"monitor": "auto"}  # "auto" = last (virtual/extended) display, "primary"


class CursorInfo(ctypes.Structure):
    _fields_ = [("cbSize", ctypes.c_uint), ("flags", ctypes.c_uint),
                ("hCursor", ctypes.c_void_p), ("pt", ctypes.wintypes.POINT)]


ARROW = [(0, 0), (0, 17), (4, 13), (7, 20), (10, 19), (7, 12), (12, 12)]


def monitor_count():
    return ctypes.windll.user32.GetSystemMetrics(80)  # SM_CMONITORS


def pick_monitor(sct):
    mons = sct.monitors  # [0] is the union of all displays
    n = len(mons) - 1
    idx = 1 if settings["monitor"] == "primary" else n
    mon = dict(mons[idx])
    monitor_info.update(mon)
    log(f"capturing monitor {idx}/{n}: {mon['width']}x{mon['height']} at {mon['left']},{mon['top']}")
    return mon


def draw_cursor(pil, monitor):
    """mss does not capture the mouse pointer, so paint an arrow where it is."""
    from PIL import ImageDraw
    info = CursorInfo()
    info.cbSize = ctypes.sizeof(info)
    if not ctypes.windll.user32.GetCursorInfo(ctypes.byref(info)) or not info.flags & 1:
        return
    x, y = info.pt.x - monitor["left"], info.pt.y - monitor["top"]
    if not (0 <= x < monitor["width"] and 0 <= y < monitor["height"]):
        return
    k = max(1.0, monitor["width"] / 1280)
    pts = [(x + px * k, y + py * k) for px, py in ARROW]
    d = ImageDraw.Draw(pil)
    d.polygon(pts, fill=(255, 255, 255), outline=(0, 0, 0))
    d.line(pts + [pts[0]], fill=(0, 0, 0), width=max(1, int(k)))


_rebuild = threading.Event()


def capture_loop():
    """mss keeps Win32 handles per thread, so it lives here. It is rebuilt whenever a monitor appears or
    disappears (e.g. the virtual display coming up after logon) or the monitor choice changes."""
    try:
        from mss import MSS as mss
    except ImportError:  # older mss
        from mss import mss
    from PIL import Image
    global current_frame
    while True:
        _rebuild.clear()
        count = monitor_count()
        try:
            with mss() as sct:
                monitor = pick_monitor(sct)
                while monitor_count() == count and not _rebuild.is_set():
                    start = time.time()
                    img = sct.grab(monitor)
                    pil = Image.frombytes("RGB", img.size, img.bgra, "raw", "BGRX")
                    draw_cursor(pil, monitor)
                    if pil.width > MAX_WIDTH:
                        pil = pil.resize((MAX_WIDTH, pil.height * MAX_WIDTH // pil.width), Image.BILINEAR)
                    buf = io.BytesIO()
                    pil.save(buf, format="JPEG", quality=JPEG_QUALITY)
                    with frame_lock:
                        current_frame = buf.getvalue()
                    time.sleep(max(0, FRAME_INTERVAL - (time.time() - start)))
        except Exception as e:
            log("capture error:", e)
            time.sleep(1)


# --------------------------------------------------------------------------- remote input

class KeyBdInput(ctypes.Structure):
    _fields_ = [("wVk", ctypes.c_ushort), ("wScan", ctypes.c_ushort), ("dwFlags", ctypes.c_ulong),
                ("time", ctypes.c_ulong), ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class MouseInput(ctypes.Structure):
    _fields_ = [("dx", ctypes.c_long), ("dy", ctypes.c_long), ("mouseData", ctypes.c_ulong),
                ("dwFlags", ctypes.c_ulong), ("time", ctypes.c_ulong),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]


class HardwareInput(ctypes.Structure):
    _fields_ = [("uMsg", ctypes.c_ulong), ("wParamL", ctypes.c_short), ("wParamH", ctypes.c_ushort)]


class InputUnion(ctypes.Union):
    _fields_ = [("ki", KeyBdInput), ("mi", MouseInput), ("hi", HardwareInput)]


class Input(ctypes.Structure):
    _fields_ = [("type", ctypes.c_ulong), ("ii", InputUnion)]


KEYEVENTF_KEYUP = 0x0002
MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP = 0x0002, 0x0004
MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP = 0x0008, 0x0010
MOUSEEVENTF_WHEEL = 0x0800
DPAD = {19: (0, -1), 20: (0, 1), 21: (-1, 0), 22: (1, 0)}
# Android key code -> Windows virtual key (extra remote keys)
KEY_MAP = {4: 0x1B, 85: 0xB3, 87: 0xB0, 88: 0xB1, 164: 0xAD, 24: 0xAF, 25: 0xAE}
_extra = ctypes.c_ulong(0)
_state = {"last_move": 0.0, "streak": 0, "ok_down": 0.0}


def _send(inp):
    ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))


def send_key(vk, down):
    _send(Input(1, InputUnion(ki=KeyBdInput(vk, 0, 0 if down else KEYEVENTF_KEYUP, 0, ctypes.pointer(_extra)))))


def send_mouse(flags, data=0):
    _send(Input(0, InputUnion(mi=MouseInput(0, 0, data & 0xFFFFFFFF, flags, 0, ctypes.pointer(_extra)))))


def move_cursor(dx, dy):
    """Move the pointer, clamped to the shared monitor, accelerating while a key repeats."""
    now = time.time()
    _state["streak"] = _state["streak"] + 1 if now - _state["last_move"] < 0.2 else 0
    _state["last_move"] = now
    step = min(60, 6 + _state["streak"] * 3)
    m = monitor_info
    pt = ctypes.wintypes.POINT()
    ctypes.windll.user32.GetCursorPos(ctypes.byref(pt))
    x = max(m["left"], min(m["left"] + m["width"] - 1, pt.x + dx * step))
    y = max(m["top"], min(m["top"] + m["height"] - 1, pt.y + dy * step))
    ctypes.windll.user32.SetCursorPos(int(x), int(y))


KEYEVENTF_UNICODE = 0x0004
ALLOWED_VK = set(range(8, 255))


def send_text(text):
    """Type text into the focused PC window (Unicode, so any language / emoji works)."""
    data = text[:200].encode("utf-16-le")
    for i in range(0, len(data), 2):
        code = int.from_bytes(data[i:i + 2], "little")
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            _send(Input(1, InputUnion(ki=KeyBdInput(0, code, flags, 0, ctypes.pointer(_extra)))))


def move_relative(dx, dy):
    """Phone trackpad: relative pointer move clamped to the shared monitor."""
    m = monitor_info
    pt = ctypes.wintypes.POINT()
    ctypes.windll.user32.GetCursorPos(ctypes.byref(pt))
    x = max(m["left"], min(m["left"] + m["width"] - 1, pt.x + dx))
    y = max(m["top"], min(m["top"] + m["height"] - 1, pt.y + dy))
    ctypes.windll.user32.SetCursorPos(int(x), int(y))


def handle_event(ev):
    """Input from a phone/browser: {type: move|click|scroll|key|text, ...}. Anything else is a TV key event."""
    kind = ev.get("type")
    if kind == "move":
        move_relative(max(-400, min(400, int(ev.get("dx", 0)))), max(-400, min(400, int(ev.get("dy", 0)))))
    elif kind == "click":
        a, b = (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP) if ev.get("button") == "right" else (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP)
        send_mouse(a)
        send_mouse(b)
    elif kind == "scroll":
        send_mouse(MOUSEEVENTF_WHEEL, max(-1200, min(1200, int(ev.get("dy", 0)))))
    elif kind == "key":
        vk = int(ev.get("vk", 0))
        if vk in ALLOWED_VK:
            send_key(vk, True)
            send_key(vk, False)
    elif kind == "text":
        send_text(str(ev.get("text", "")))
    else:
        handle_key(int(ev.get("keyCode", 0)), bool(ev.get("down", False)))


def handle_key(keycode, down):
    """TV remote -> PC: D-pad moves the mouse, OK clicks (hold = right click), CH+/CH- scroll."""
    if keycode in DPAD:
        if down:
            move_cursor(*DPAD[keycode])
    elif keycode in (23, 66):
        if down:
            _state["ok_down"] = _state["ok_down"] or time.time()
        elif _state["ok_down"]:
            held = time.time() - _state["ok_down"]
            _state["ok_down"] = 0.0
            a, b = (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP) if held > 0.6 else (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP)
            send_mouse(a)
            send_mouse(b)
    elif keycode in (166, 167):
        if down:
            send_mouse(MOUSEEVENTF_WHEEL, 120 if keycode == 166 else -120)
    elif keycode in KEY_MAP:
        send_key(KEY_MAP[keycode], down)


# --------------------------------------------------------------------------- HTTPS server

class App:
    pairing = None
    fingerprint = ""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    timeout = 30

    def setup(self):
        self.request.settimeout(30)
        self.request.do_handshake()  # TLS handshake in the worker thread, not the accept loop
        super().setup()

    def log_message(self, *args):
        pass

    def _reply(self, code, body=b"", ctype="text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _auth(self):
        token = parse_qs(urlparse(self.path).query).get("t", [""])[0]
        if App.pairing.valid(token):
            return True
        self._reply(401, b"unauthorized")
        return False

    def _json_body(self):
        n = min(int(self.headers.get("Content-Length", 0)), 4096)
        return json.loads(self.rfile.read(n) or b"{}")

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/ping":
            if self._auth():
                m = monitor_info
                self._reply(200, json.dumps({"name": socket.gethostname(), "width": m["width"],
                                             "height": m["height"]}).encode(), "application/json")
        elif path == "/stream":
            if self._auth():
                self._stream()
        elif path in ("/remote", "/view"):
            from web_pages import REMOTE_HTML, VIEW_HTML
            self._reply(200, (REMOTE_HTML if path == "/remote" else VIEW_HTML).encode(), "text/html; charset=utf-8")
        else:
            self._reply(404)

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            if path == "/pair":
                data = self._json_body()
                token = App.pairing.try_pair(data.get("proof", ""), data.get("name", "TV"))
                if token:
                    self._reply(200, json.dumps({"token": token, "name": socket.gethostname()}).encode(),
                                "application/json")
                else:
                    self._reply(403, b"bad pin")
            elif path == "/pair-web":
                data = self._json_body()
                token = App.pairing.try_pair_pin(data.get("pin", ""), data.get("name", "Browser"))
                if token:
                    self._reply(200, json.dumps({"token": token}).encode(), "application/json")
                else:
                    self._reply(403, b"bad pin")
            elif path == "/input":
                if self._auth():
                    handle_event(self._json_body())
                    self._reply(200, b"ok")
            else:
                self._reply(404)
        except Exception as e:
            log("request error:", e)
            self._reply(400, b"bad request")

    def _stream(self):
        self.send_response(200)
        self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        last = None
        try:
            while True:
                with frame_lock:
                    frame = current_frame
                if frame is None or frame is last:
                    time.sleep(0.01)
                    continue
                last = frame
                self.wfile.write(b"--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                                 + str(len(frame)).encode() + b"\r\n\r\n" + frame + b"\r\n")
                self.wfile.flush()
        except (OSError, ssl.SSLError):
            pass


class TLSServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 16

    def __init__(self, addr, handler, context):
        self.context = context
        super().__init__(addr, handler)

    def get_request(self):
        sock, addr = self.socket.accept()
        return self.context.wrap_socket(sock, server_side=True, do_handshake_on_connect=False), addr

    def handle_error(self, request, client_address):
        pass  # port scans / dropped TLS handshakes are not interesting


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # no packet is sent; just picks the LAN interface
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def beacon_loop():
    """Broadcast address + certificate fingerprint so a paired TV can follow this PC if its IP changes."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    while True:
        try:
            ip = local_ip()
            msg = f"{BEACON_MAGIC}|{ip}|{PORT}|{App.fingerprint[:16]}|{socket.gethostname()}".encode()
            for dest in (ip.rsplit(".", 1)[0] + ".255", "255.255.255.255"):
                sock.sendto(msg, (dest, DISCOVERY_PORT))
        except OSError:
            pass
        time.sleep(2)


def start_server():
    cert, key, fp = load_or_create_cert()
    App.fingerprint = fp
    App.pairing = Pairing(fp)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(str(cert), str(key))
    server = TLSServer(("0.0.0.0", PORT), Handler, ctx)
    for fn in (server.serve_forever, capture_loop, beacon_loop):
        threading.Thread(target=fn, daemon=True).start()
    log("server started on", PORT, "fingerprint", fp[:16])
    return server


# --------------------------------------------------------------------------- virtual display (admin helper)

def is_admin():
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def run_elevated(*args):
    exe = sys.executable
    params = " ".join(([] if getattr(sys, "frozen", False) else [f'"{os.path.abspath(__file__)}"'])
                      + [f'"{a}"' if " " in a else a for a in args])
    return ctypes.windll.shell32.ShellExecuteW(None, "runas", exe, params, None, 1) > 32


def display_installed():
    return (DRIVER_DIR / "deviceinstaller64.exe").exists()


def install_display():
    """Runs elevated: downloads the Amyuni usbmmidd virtual display driver, installs it and registers a
    logon task that re-enables the virtual monitor (it does not survive a reboot by itself)."""
    PROGRAM_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = PROGRAM_DIR / "usbmmidd_v2.zip"
    urllib.request.urlretrieve(DISPLAY_DRIVER_URL, zip_path)
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(PROGRAM_DIR)
    exe = str(DRIVER_DIR / "deviceinstaller64.exe")
    subprocess.run([exe, "install", str(DRIVER_DIR / "usbmmidd.inf"), "usbmmidd"], cwd=DRIVER_DIR, check=False)
    subprocess.run([exe, "enableidd", "1"], cwd=DRIVER_DIR, check=False)
    subprocess.run(["schtasks", "/create", "/f", "/tn", TASK_NAME, "/sc", "onlogon", "/rl", "highest",
                    "/tr", f'cmd /c "cd /d "{DRIVER_DIR}" && deviceinstaller64.exe enableidd 1"'], check=False)


def remove_display():
    exe = str(DRIVER_DIR / "deviceinstaller64.exe")
    subprocess.run(["schtasks", "/delete", "/f", "/tn", TASK_NAME], check=False)
    subprocess.run([exe, "enableidd", "0"], cwd=DRIVER_DIR, check=False)
    subprocess.run([exe, "remove", "usbmmidd"], cwd=DRIVER_DIR, check=False)


# --------------------------------------------------------------------------- start with Windows

RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"


def autostart_enabled():
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, APP)
            return True
    except OSError:
        return False


def set_autostart(on):
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if on:
            cmd = f'"{sys.executable}"' if getattr(sys, "frozen", False) else f'"{sys.executable}" "{os.path.abspath(__file__)}"'
            winreg.SetValueEx(k, APP, 0, winreg.REG_SZ, cmd + " --background")
        else:
            try:
                winreg.DeleteValue(k, APP)
            except OSError:
                pass


# --------------------------------------------------------------------------- tray UI

def show_pin_window():
    """Small window with the pairing PIN. Runs in its own thread with its own Tk root."""
    import tkinter as tk

    def run():
        pin = App.pairing.new_pin()
        root = tk.Tk()
        root.title(f"{APP} - pair a TV")
        root.geometry("420x330")
        root.attributes("-topmost", True)
        tk.Label(root, text="On your TV (EthanConnect > Second Screen)\nor phone, enter this PIN:", font=("Segoe UI", 12)).pack(pady=(24, 6))
        tk.Label(root, text=f"{pin[:3]} {pin[3:]}", font=("Consolas", 42, "bold")).pack(pady=4)
        info = tk.Label(root, text="", font=("Segoe UI", 10), fg="#555")
        info.pack()
        tk.Label(root, text=f"PC address: {local_ip()}:{PORT}", font=("Segoe UI", 10)).pack(pady=(6, 0))
        tk.Label(root, text=f"Certificate: {App.fingerprint[:8].upper()}", font=("Segoe UI", 9), fg="#777").pack()
        try:  # QR code: a phone camera opens the remote page and pairs with this PIN in one step
            import qrcode
            from PIL import ImageTk
            qr = qrcode.make(f"https://{local_ip()}:{PORT}/remote#pin={pin}").resize((170, 170))
            photo = ImageTk.PhotoImage(qr)
            lbl = tk.Label(root, image=photo)
            lbl.image = photo
            lbl.pack(pady=(10, 0))
            tk.Label(root, text="Phone: scan to use it as a remote & keyboard\n(accept the one-time certificate warning)",
                     font=("Segoe UI", 9), fg="#555").pack()
            root.geometry("420x560")
        except Exception as e:
            log("qr unavailable:", e)

        def tick():
            p, left = App.pairing.current_pin()
            info.config(text=f"Expires in {left // 60}:{left % 60:02d}" if p else "PIN used or expired - close and reopen")
            root.after(1000, tick)

        tick()
        root.mainloop()

    threading.Thread(target=run, daemon=True).start()


def message_box(text, title=APP, flags=0x41):  # MB_OKCANCEL | MB_ICONINFORMATION
    return ctypes.windll.user32.MessageBoxW(None, text, title, flags)


def tray():
    import pystray
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((4, 10, 60, 46), 6, fill=(59, 130, 246, 255))
    d.rectangle((26, 48, 38, 54), fill=(59, 130, 246, 255))
    d.rectangle((18, 54, 46, 58), fill=(59, 130, 246, 255))

    def pair(icon, item):
        show_pin_window()

    def toggle_monitor(icon, item):
        settings["monitor"] = "primary" if settings["monitor"] == "auto" else "auto"
        _rebuild.set()

    def display(icon, item):
        if display_installed():
            if message_box("Remove the virtual second screen?") == 1:
                run_elevated("--remove-display")
        elif message_box("Adds a virtual second monitor so the TV can be an extended screen.\n\n"
                         "This downloads the signed 'usbmmidd' virtual display driver from amyuni.com and "
                         "installs it (Windows will ask for administrator permission). Continue?") == 1:
            run_elevated("--install-display")

    def forget(icon, item):
        if message_box("Forget every paired TV? They will need a new PIN.") == 1:
            App.pairing.forget_all()

    def autostart(icon, item):
        set_autostart(not autostart_enabled())

    menu = pystray.Menu(
        pystray.MenuItem(f"{APP} {VERSION}", None, enabled=False),
        pystray.MenuItem("Pair a TV / phone (show PIN)...", pair, default=True),
        pystray.MenuItem("Share primary screen instead of second screen", toggle_monitor,
                         checked=lambda i: settings["monitor"] == "primary"),
        pystray.MenuItem(lambda i: "Remove second screen..." if display_installed() else "Set up second screen...", display),
        pystray.MenuItem("Start with Windows", autostart, checked=lambda i: autostart_enabled()),
        pystray.MenuItem("Forget paired TVs...", forget),
        pystray.MenuItem("Quit", lambda icon, item: icon.stop()),
    )
    icon = pystray.Icon(APP, img, f"{APP} - {local_ip()}:{PORT}", menu)
    return icon


def main():
    if "--install-display" in sys.argv:
        install_display()
        return
    if "--remove-display" in sys.argv:
        remove_display()
        return
    mutex = ctypes.windll.kernel32.CreateMutexW(None, False, f"Global\\{APP}PC")
    if ctypes.windll.kernel32.GetLastError() == 183:  # already running
        return
    start_server()
    if not App.pairing.tokens and "--background" not in sys.argv:
        show_pin_window()
    tray().run()


if __name__ == "__main__":
    main()
