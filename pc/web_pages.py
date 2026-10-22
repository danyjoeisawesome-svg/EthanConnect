"""Static pages served by EthanConnect PC: a phone remote (trackpad + keyboard) and a browser viewer.
They contain no secrets; access is granted by the token a device gets by pairing with the PIN."""

COMMON_JS = r"""
const $ = (s) => document.querySelector(s);
let token = localStorage.getItem('ec_token') || new URLSearchParams(location.search).get('t') || '';

async function pairWithPin(pin) {
  const r = await fetch('/pair-web', {method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({pin: pin, name: navigator.userAgent.slice(0, 40)})});
  if (!r.ok) return false;
  token = (await r.json()).token;
  localStorage.setItem('ec_token', token);
  return true;
}

async function ensureToken(onReady, onNeedPin) {
  const m = location.hash.match(/pin=(\d{6})/);
  if (m) {                                   // arrived from the QR code on the PC
    history.replaceState(null, '', location.pathname);
    if (await pairWithPin(m[1])) return onReady();
  }
  if (token) {
    const r = await fetch('/ping?t=' + encodeURIComponent(token));
    if (r.ok) return onReady();
    localStorage.removeItem('ec_token'); token = '';
  }
  onNeedPin();
}

function bindPinForm(onReady) {
  $('#pinForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const ok = await pairWithPin($('#pin').value.replace(/\s/g, ''));
    if (ok) { $('#pairBox').hidden = true; onReady(); }
    else { $('#pinErr').textContent = 'Wrong or expired PIN. Open "Pair a TV / phone" on the PC for a new one.'; }
  });
}
"""

STYLE = """
:root { color-scheme: dark; --bg:#0b1020; --card:#151c33; --accent:#3b82f6; --text:#e5e7eb; --muted:#9ca3af; }
[hidden] { display: none !important; }
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { margin:0; height:100%; background:var(--bg); color:var(--text);
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif; }
button { font: inherit; color: var(--text); background: var(--card); border: 1px solid #263154; border-radius: 12px;
  padding: 12px 14px; min-height: 48px; }
button:active { background: var(--accent); }
.card { background: var(--card); border-radius: 16px; padding: 20px; max-width: 420px; margin: 12vh auto 0; }
input[type=text], input[type=tel] { width:100%; font: inherit; color: var(--text); background: #0f1530;
  border: 1px solid #263154; border-radius: 12px; padding: 12px; }
"""

REMOTE_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>EthanConnect Remote</title>
<style>""" + STYLE + """
#app { display:flex; flex-direction:column; height:100%; padding:10px; gap:10px; }
#pad { flex:1; min-height:160px; border-radius:18px; background:linear-gradient(160deg,#17213f,#0f1530);
  border:1px solid #263154; display:flex; align-items:center; justify-content:center; color:var(--muted);
  touch-action:none; user-select:none; text-align:center; padding:12px; }
.row { display:grid; gap:8px; grid-template-columns:repeat(auto-fit,minmax(64px,1fr)); }
#typed { margin:0; }
a { color: var(--accent); }
</style></head><body>
<div id="pairBox" class="card" hidden>
  <h2 style="margin-top:0">Pair this phone</h2>
  <p style="color:var(--muted)">On the PC, right-click the EthanConnect tray icon and choose <b>Pair a TV / phone</b>, then enter the PIN.</p>
  <form id="pinForm"><input id="pin" type="tel" inputmode="numeric" maxlength="7" placeholder="6-digit PIN" autocomplete="off">
  <p id="pinErr" style="color:#f87171;min-height:1.2em"></p><button style="width:100%">Pair</button></form>
</div>
<div id="app" hidden>
  <div id="pad">Touchpad<br>drag = move &middot; tap = click &middot; two fingers = scroll / right-click</div>
  <div class="row">
    <button data-click="left">Left click</button><button data-click="right">Right click</button>
    <button id="kbd">Keyboard</button><a href="/view" style="align-self:center;text-align:center">View screen</a>
  </div>
  <div id="kbdBox" hidden>
    <input id="typed" type="text" autocomplete="off" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="Type here - sent to the PC as you type">
    <div class="row" style="margin-top:8px">
      <button data-vk="13">Enter</button><button data-vk="8">&#9003;</button><button data-vk="9">Tab</button>
      <button data-vk="27">Esc</button><button data-vk="37">&larr;</button><button data-vk="38">&uarr;</button>
      <button data-vk="40">&darr;</button><button data-vk="39">&rarr;</button>
    </div>
  </div>
  <div class="row">
    <button data-vk="179">&#9199;</button><button data-vk="177">&#9198;</button><button data-vk="176">&#9197;</button>
    <button data-vk="173">Mute</button><button data-vk="174">Vol &minus;</button><button data-vk="175">Vol +</button>
  </div>
</div>
<script>""" + COMMON_JS + r"""
function send(ev) {
  return fetch('/input?t=' + encodeURIComponent(token), {method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(ev), keepalive: true}).then((r) => { if (r.status === 401) location.reload(); }).catch(() => {});
}

function start() {
  $('#pairBox').hidden = true; $('#app').hidden = false;
  const pad = $('#pad');
  let touches = {}, moved = 0, startT = 0, fingers = 0, pending = {dx: 0, dy: 0, sy: 0}, raf = 0;
  const flush = () => {
    raf = 0;
    if (pending.dx || pending.dy) send({type: 'move', dx: Math.round(pending.dx), dy: Math.round(pending.dy)});
    if (pending.sy) send({type: 'scroll', dy: Math.round(pending.sy)});
    pending = {dx: 0, dy: 0, sy: 0};
  };
  const queue = () => { if (!raf) raf = requestAnimationFrame(flush); };
  pad.addEventListener('touchstart', (e) => {
    e.preventDefault();
    if (e.touches.length >= fingers) { fingers = e.touches.length; if (fingers === 1) { moved = 0; startT = Date.now(); } }
    for (const t of e.changedTouches) touches[t.identifier] = {x: t.clientX, y: t.clientY};
  }, {passive: false});
  pad.addEventListener('touchmove', (e) => {
    e.preventDefault();
    for (const t of e.changedTouches) {
      const p = touches[t.identifier]; if (!p) continue;
      const dx = t.clientX - p.x, dy = t.clientY - p.y;
      p.x = t.clientX; p.y = t.clientY;
      moved += Math.abs(dx) + Math.abs(dy);
      if (e.touches.length >= 2) pending.sy += -dy * 4; else { pending.dx += dx * 1.8; pending.dy += dy * 1.8; }
    }
    queue();
  }, {passive: false});
  pad.addEventListener('touchend', (e) => {
    e.preventDefault();
    for (const t of e.changedTouches) delete touches[t.identifier];
    if (e.touches.length === 0) {
      if (moved < 12 && Date.now() - startT < 300) send({type: 'click', button: fingers >= 2 ? 'right' : 'left'});
      fingers = 0;
    }
  }, {passive: false});

  document.querySelectorAll('[data-click]').forEach((b) => b.addEventListener('click', () => send({type: 'click', button: b.dataset.click})));
  document.querySelectorAll('[data-vk]').forEach((b) => b.addEventListener('click', () => send({type: 'key', vk: +b.dataset.vk})));
  $('#kbd').addEventListener('click', () => { const box = $('#kbdBox'); box.hidden = !box.hidden; if (!box.hidden) $('#typed').focus(); });
  $('#typed').addEventListener('input', (e) => {
    if (e.inputType === 'deleteContentBackward') send({type: 'key', vk: 8});
    else if (e.data) send({type: 'text', text: e.data});
    else if (e.target.value) send({type: 'text', text: e.target.value});
    if (e.inputType !== 'deleteContentBackward') e.target.value = '';
  });
  $('#typed').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); send({type: 'key', vk: 13}); } });
}

bindPinForm(start);
ensureToken(start, () => { $('#pairBox').hidden = false; });
</script></body></html>
"""

VIEW_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>EthanConnect Viewer</title>
<style>""" + STYLE + """
html, body { background:#000; overflow:hidden; }
#screen { width:100vw; height:100vh; object-fit:contain; display:block; }
#bar { position:fixed; top:8px; right:8px; display:flex; gap:8px; opacity:.85; }
#bar a, #bar button { background:#151c33cc; color:#e5e7eb; border:1px solid #263154; border-radius:10px; padding:8px 12px; text-decoration:none; font-size:14px; }
</style></head><body>
<div id="pairBox" class="card" hidden>
  <h2 style="margin-top:0">Pair this browser</h2>
  <p style="color:var(--muted)">On the PC, right-click the EthanConnect tray icon, choose <b>Pair a TV / phone</b> and enter the PIN.</p>
  <form id="pinForm"><input id="pin" type="tel" inputmode="numeric" maxlength="7" placeholder="6-digit PIN" autocomplete="off">
  <p id="pinErr" style="color:#f87171;min-height:1.2em"></p><button style="width:100%">Pair</button></form>
</div>
<img id="screen" alt="" hidden>
<div id="bar" hidden><a href="/remote">Remote</a><button id="fs">Fullscreen</button></div>
<script>""" + COMMON_JS + r"""
function start() {
  $('#pairBox').hidden = true;
  const img = $('#screen');
  img.src = '/stream?t=' + encodeURIComponent(token);
  img.hidden = false; $('#bar').hidden = false;
  img.onerror = () => setTimeout(() => { img.src = '/stream?t=' + encodeURIComponent(token) + '&r=' + Date.now(); }, 2000);
  $('#fs').onclick = () => (document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen());
}
bindPinForm(start);
ensureToken(start, () => { $('#pairBox').hidden = false; });
</script></body></html>
"""
