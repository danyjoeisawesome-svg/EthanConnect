import ctypes, ctypes.wintypes, http.client, json, ssl, sys, time
sys.path.insert(0, '.')
import ethanconnect_pc as e
e.start_server()
time.sleep(3)
ctx = ssl._create_unverified_context()


def req(method, path, body=None):
    c = http.client.HTTPSConnection('127.0.0.1', e.PORT, context=ctx, timeout=10)
    c.request(method, path, body=json.dumps(body) if body is not None else None, headers={'Content-Type': 'application/json'})
    r = c.getresponse(); d = r.read(); c.close(); return r.status, d


def pos():
    p = ctypes.wintypes.POINT(); ctypes.windll.user32.GetCursorPos(ctypes.byref(p)); return p.x, p.y


print('/remote', req('GET', '/remote')[0], '/view', req('GET', '/view')[0])
pin = e.App.pairing.new_pin()
print('bad pin', req('POST', '/pair-web', {'pin': '000000'})[0])
st, d = req('POST', '/pair-web', {'pin': pin, 'name': 'phone'}); print('good pin', st)
tok = json.loads(d)['token']
print('no token input', req('POST', '/input', {'type': 'move', 'dx': 5, 'dy': 0})[0])
ctypes.windll.user32.SetCursorPos(500, 500)
a = pos(); print('move', req('POST', '/input?t=' + tok, {'type': 'move', 'dx': 40, 'dy': 25})[0], a, '->', pos())
print('scroll', req('POST', '/input?t=' + tok, {'type': 'scroll', 'dy': 120})[0])
print('text', req('POST', '/input?t=' + tok, {'type': 'text', 'text': ''})[0])
print('tv key', req('POST', '/input?t=' + tok, {'keyCode': 22, 'down': True})[0])
e.App.pairing.forget_all()
