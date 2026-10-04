package tools;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Delivers signed webhook notifications to user-supplied URLs.
 *
 * SSRF protection: every resolved address of the target host must be a public unicast
 * address (all private, shared, loopback, link-local, unique-local, documentation,
 * benchmarking, multicast, reserved and transition ranges are rejected), and the HTTP
 * connection is made to exactly the address that was validated. The hostname is only used
 * for the Host header, TLS SNI and certificate verification, so a second DNS answer
 * (DNS rebinding) can never redirect the request. Redirects are never followed.
 */
@ApplicationScoped
public class WebhookSender {
    @ConfigProperty(name = "notification.webhook.require-https", defaultValue = "true") boolean requireHttps;
    @ConfigProperty(name = "notification.webhook.timeout-seconds", defaultValue = "8") int timeoutSeconds;

    /** A validated destination: the exact address to connect to plus the original URL parts. */
    record Target(URI uri, InetAddress address, int port, boolean https) { }

    public void validateUrl(String value) {
        resolveTarget(value);
    }

    Target resolveTarget(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Ungültige Webhook-URL");
            }
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new IllegalArgumentException("Nur HTTP(S)-Webhook-URLs sind erlaubt");
            }
            if (requireHttps && !"https".equals(scheme)) {
                throw new IllegalArgumentException("Webhook-URLs müssen HTTPS verwenden");
            }
            String host = uri.getHost();
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("Webhook-Ziel konnte nicht aufgelöst werden");
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new IllegalArgumentException("Private oder lokale Webhook-Ziele sind nicht erlaubt");
                }
            }
            boolean https = "https".equals(scheme);
            int port = uri.getPort() > 0 ? uri.getPort() : (https ? 443 : 80);
            return new Target(uri, addresses[0], port, https);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook-Ziel konnte nicht validiert werden", e);
        }
    }

    /** True only for globally routable unicast addresses. */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(b);
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            // Only global unicast 2000::/3 is allowed (excludes ::/8, fc00::/7 ULA, fe80::/10, ff00::/8 …).
            if ((first & 0xe0) != 0x20) {
                return false;
            }
            int word0 = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
            int word1 = ((b[2] & 0xff) << 8) | (b[3] & 0xff);
            if (word0 == 0x2001 && word1 < 0x0200) return false;      // 2001::/23 IETF protocol assignments (Teredo, ORCHID, …)
            if (word0 == 0x2001 && word1 == 0x0db8) return false;     // 2001:db8::/32 documentation
            if (word0 == 0x2002) return false;                         // 2002::/16 6to4 (embeds arbitrary IPv4)
            if (word0 == 0x3fff && (word1 & 0xf000) == 0) return false; // 3fff::/20 documentation
            return true;
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] b) {
        int a = b[0] & 0xff;
        int c = b[1] & 0xff;
        int d = b[2] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;            // this-network, private, loopback, multicast, reserved, broadcast
        if (a == 100 && (c & 0xc0) == 64) return false;                          // 100.64.0.0/10 carrier-grade NAT
        if (a == 169 && c == 254) return false;                                  // link-local / cloud metadata
        if (a == 172 && (c & 0xf0) == 16) return false;                          // 172.16.0.0/12
        if (a == 192 && c == 168) return false;                                  // 192.168.0.0/16
        if (a == 192 && c == 0 && (d == 0 || d == 2)) return false;              // 192.0.0.0/24, 192.0.2.0/24
        if (a == 192 && c == 88 && d == 99) return false;                        // 192.88.99.0/24 6to4 relay
        if (a == 198 && (c == 18 || c == 19)) return false;                      // 198.18.0.0/15 benchmarking
        if (a == 198 && c == 51 && d == 100) return false;                       // 198.51.100.0/24 documentation
        if (a == 203 && c == 0 && d == 113) return false;                        // 203.0.113.0/24 documentation
        return true;
    }

    public int send(String url, String secret, String eventType, String body, String deliveryId) throws Exception {
        Target target = resolveTarget(url); // Validated address is the one we connect to.
        String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
        String signature = sign(secret, timestamp + "." + body);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);

        String path = target.uri().getRawPath() == null || target.uri().getRawPath().isEmpty()
                ? "/" : target.uri().getRawPath();
        if (target.uri().getRawQuery() != null) {
            path += "?" + target.uri().getRawQuery();
        }
        String hostHeader = target.uri().getHost()
                + (target.uri().getPort() > 0 ? ":" + target.uri().getPort() : "");
        String request = "POST " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Content-Type: application/json\r\n"
                + "User-Agent: WildRovers-Notifications/1.0\r\n"
                + "X-Wildrovers-Event: " + headerValue(eventType) + "\r\n"
                + "X-Wildrovers-Delivery: " + headerValue(deliveryId == null ? UUID.randomUUID().toString() : deliveryId) + "\r\n"
                + "X-Wildrovers-Timestamp: " + timestamp + "\r\n"
                + "X-Wildrovers-Signature: sha256=" + signature + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";

        int timeoutMillis = timeoutSeconds * 1000;
        try (Socket socket = open(target, timeoutMillis)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String statusLine = reader.readLine();
            return parseStatus(statusLine);
        }
    }

    private Socket open(Target target, int timeoutMillis) throws IOException {
        Socket plain = new Socket();
        try {
            plain.connect(new InetSocketAddress(target.address(), target.port()), timeoutMillis);
            plain.setSoTimeout(timeoutMillis);
            if (!target.https()) {
                return plain;
            }
            String host = target.uri().getHost();
            SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(plain, host, target.port(), true);
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS"); // verify certificate against the hostname
            if (!host.contains(":") && !Character.isDigit(host.charAt(host.length() - 1))) {
                parameters.setServerNames(List.of(new SNIHostName(host)));
            }
            tls.setSSLParameters(parameters);
            tls.setSoTimeout(timeoutMillis);
            tls.startHandshake();
            return tls;
        } catch (IOException | RuntimeException e) {
            plain.close();
            throw e;
        }
    }

    static int parseStatus(String statusLine) throws IOException {
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new IOException("Ungültige Webhook-Antwort");
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("Ungültige Webhook-Antwort");
        }
        try {
            return Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IOException("Ungültige Webhook-Antwort", e);
        }
    }

    private static String headerValue(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    private String sign(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
