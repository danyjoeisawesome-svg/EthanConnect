import hashlib, hmac, json, ssl, sys, time, http.client
sys.path.insert(0, '.')
import ethanconnect_pc as e
srv = e.start_server()
time.sleep(3)
pin = e.App.pairing.new_pin()
ctx = ssl._create_unverified_context()
def req(method, path, body=None):
    c = http.client.HTTPSConnection('127.0.0.1', e.PORT, context=ctx, timeout=10)
    c.request(method, path, body=json.dumps(body) if body else None, headers={'Content-Type': 'application/json'})
    r = c.getresponse(); d = r.read(); c.close(); return r.status, d
print('ping no token', req('GET', '/ping')[0])
bad = hmac.new(b'000000', bytes.fromhex(e.App.fingerprint), hashlib.sha256).hexdigest()
print('bad pin', req('POST', '/pair', {'proof': bad, 'name': 't'})[0])
good = hmac.new(pin.encode(), bytes.fromhex(e.App.fingerprint), hashlib.sha256).hexdigest()
st, d = req('POST', '/pair', {'proof': good, 'name': 'testtv'}); print('good pin', st)
tok = json.loads(d)['token']
print('ping', req('GET', '/ping?t=' + tok))
print('input', req('POST', '/input?t=' + tok, {'keyCode': 22, 'down': True})[0])
c = http.client.HTTPSConnection('127.0.0.1', e.PORT, context=ctx, timeout=10)
c.request('GET', '/stream?t=' + tok); r = c.getresponse(); data = r.read(300000); print('stream', r.status, len(data), data[:60])
e.App.pairing.forget_all()
