"""Focused integration check: revoking a paired token closes its live MJPEG stream."""
import http.client
import os
import ssl
import tempfile
import threading
import time
import unittest
from pathlib import Path

# Keep test certificates and pairing records out of the user's real EthanConnect profile.
_test_profile = tempfile.TemporaryDirectory(
    prefix=".test-ethanconnect-disconnect-", dir=Path(__file__).resolve().parent
)
os.environ["APPDATA"] = _test_profile.name

import ethanconnect_pc as app


class DisconnectStreamTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cert_path, key_path, fingerprint = app.load_or_create_cert()
        app.App.fingerprint = fingerprint
        app.App.pairing = app.Pairing(fingerprint)
        cls.token = "test-disconnect-token"
        app.App.pairing.tokens[cls.token] = {"name": "Test TV", "created": int(time.time())}
        app.current_frame = b"frame-payload"

        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(str(cert_path), str(key_path))
        cls.server = app.TLSServer(("127.0.0.1", 0), app.Handler, context)
        cls.server_thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.server_thread.start()
        cls.port = cls.server.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.server_thread.join(timeout=2)
        app.current_frame = None
        _test_profile.cleanup()

    def test_revoking_pair_closes_stream(self):
        client = http.client.HTTPSConnection(
            "127.0.0.1", self.port, context=ssl._create_unverified_context(), timeout=4
        )
        try:
            client.request("GET", f"/stream?t={self.token}")
            response = client.getresponse()
            self.assertEqual(response.status, 200)
            self.assertTrue(response.readline().startswith(b"--frame"))
            headers = {}
            while True:
                line = response.readline()
                if line in (b"\r\n", b"\n"):
                    break
                name, value = line.split(b":", 1)
                headers[name.lower()] = value.strip()
            length = int(headers[b"content-length"])
            self.assertEqual(len(response.read(length)), length)
            response.readline()  # trailing CRLF

            self.assertTrue(app.App.pairing.revoke(self.token))
            self.assertEqual(response.read(1), b"")
        finally:
            client.close()
