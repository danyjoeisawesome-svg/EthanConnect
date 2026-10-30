package com.ethanconnect.tv;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * HTTPS client for the EthanConnect PC app. The PC uses a self-signed certificate, so instead of CA
 * validation the connection is pinned to the certificate fingerprint learned while pairing.
 */
final class PcClient {
    static final class Unauthorized extends IOException {
        Unauthorized() { super("unauthorized"); }
    }

    private final String host;
    private final int port;
    private final String pinnedFingerprint; // hex SHA-256, or null while pairing
    private final SSLContext ssl;

    PcClient(String host, int port, String pinnedFingerprint) {
        this.host = host;
        this.port = port;
        this.pinnedFingerprint = pinnedFingerprint;
        try {
            TrustManager tm = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    if (PcClient.this.pinnedFingerprint == null) return; // pairing: learn it, see pair()
                    if (!PcClient.this.pinnedFingerprint.equalsIgnoreCase(fingerprint(chain[0]))) {
                        throw new CertificateException("certificate does not match the paired PC");
                    }
                }

                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{tm}, new SecureRandom());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String fingerprint(Certificate cert) throws CertificateException {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    /** Stable lowercase hex independent of signed-byte formatting differences between runtimes. */
    private static String toHex(byte[] bytes) {
        final char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(out);
    }

    private HttpsURLConnection open(String method, String path, int readTimeoutMs) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL("https", host, port, path).openConnection();
        c.setSSLSocketFactory(ssl.getSocketFactory());
        c.setHostnameVerifier((h, s) -> true); // identity is the pinned fingerprint, not the hostname
        c.setConnectTimeout(5000);
        c.setReadTimeout(readTimeoutMs);
        c.setRequestMethod(method);
        return c;
    }

    /** Pairs with the PIN; returns {token, certificateFingerprint}. Must be called on an unpinned client. */
    String[] pair(String pin, String tvName) throws IOException {
        // 1) learn the certificate the PC presents (the handshake completes even though the request is refused)
        String fp;
        HttpsURLConnection probe = open("GET", "/ping", 5000);
        try {
            probe.connect();
            fp = fingerprint(probe.getServerCertificates()[0]);
            android.util.Log.i("EthanConnect", "PC certificate fingerprint=" + fp.substring(0, 16)
                    + " length=" + fp.length());
        } catch (CertificateException e) {
            throw new IOException(e);
        } finally {
            probe.disconnect();
        }
        // 2) send the proof over a connection pinned to that certificate. The proof binds the PIN to the
        // certificate we actually saw, so a man in the middle cannot reuse it against the real PC.
        HttpsURLConnection c = new PcClient(host, port, fp).open("POST", "/pair", 8000);
        try {
            String proof = hmacHex(pin, fp);
            android.util.Log.i("EthanConnect", "pair request host=" + host + ":" + port + " fingerprint="
                    + fp.substring(0, 16) + " proofLength=" + proof.length());
            byte[] body = ("{\"proof\":\"" + proof + "\",\"name\":\"" + tvName.replace("\"", "") + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) { os.write(body); }
            int responseCode = c.getResponseCode();
            if (responseCode != 200) throw new IOException("PC rejected pairing: HTTP " + responseCode);
            String json = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
            int i = json.indexOf("\"token\"");
            int a = json.indexOf('"', json.indexOf(':', i) + 1) + 1;
            return new String[]{json.substring(a, json.indexOf('"', a)), fp};
        } finally {
            c.disconnect();
        }
    }

    private static String hmacHex(String pin, String fingerprintHex) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pin.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] msg = new byte[fingerprintHex.length() / 2];
            for (int i = 0; i < msg.length; i++) {
                msg[i] = (byte) Integer.parseInt(fingerprintHex.substring(2 * i, 2 * i + 2), 16);
            }
            return toHex(mac.doFinal(msg));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /** Opens the MJPEG stream; throws Unauthorized if the PC no longer knows this TV. */
    HttpsURLConnection openStream(String token) throws IOException {
        HttpsURLConnection c = open("GET", "/stream?t=" + token, 15000);
        int code = c.getResponseCode();
        if (code == 401) throw new Unauthorized();
        if (code != 200) throw new IOException("HTTP " + code);
        return c;
    }

    void sendKey(String token, int keyCode, boolean down) throws IOException {
        HttpsURLConnection c = open("POST", "/input?t=" + token, 3000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] body = ("{\"keyCode\":" + keyCode + ",\"down\":" + down + "}").getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(body); }
        int code = c.getResponseCode();
        readAll(c.getInputStream()); // drain so the connection can be reused (keep-alive)
        if (code == 401) throw new Unauthorized();
    }

    /** Reads the next JPEG from a multipart/x-mixed-replace stream. */
    static byte[] readFrame(InputStream in) throws IOException {
        int length = -1;
        while (true) {
            String line = readLine(in);
            if (line.isEmpty()) {
                if (length >= 0) break;
                continue;
            }
            if (line.regionMatches(true, 0, "content-length:", 0, 15)) length = Integer.parseInt(line.substring(15).trim());
        }
        byte[] frame = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(frame, off, length - off);
            if (n < 0) throw new EOFException();
            off += n;
        }
        return frame;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != '\n') {
            if (b < 0) throw new EOFException();
            if (b != '\r') sb.append((char) b);
        }
        return sb.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
