package com.linkx.server.common;

import com.linkx.server.exception.CustomException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 外链拉取 SSRF 防护：仅 http(s)、拒绝内网/本机/链路本地与带 userinfo 的 URL。
 * <p>
 * HTTP 拉取时必须用 {@link #parseAndValidatePinned} + {@link #openPinnedConnection}，
 * 校验通过后直连已解析的公网 IP，避免 DNS rebinding（TOCTOU）。
 */
public final class SafeExternalUrl {

    private SafeExternalUrl() {
    }

    /**
     * 校验后的外链：URI 保留原始主机名（用于 Host/SNI/签名），pinnedAddress 为已校验的公网 IP。
     */
    public record Validated(URI uri, InetAddress pinnedAddress) {
    }

    public static URI parseAndValidate(String raw) {
        return parseAndValidatePinned(raw).uri();
    }

    public static Validated parseAndValidatePinned(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(400, "外链地址不能为空");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new CustomException(400, "图片地址过长");
        }
        final URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new CustomException(400, "无效的外链地址");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new CustomException(400, "无效的外链地址");
        }
        String schemeLower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(schemeLower) && !"https".equals(schemeLower)) {
            throw new CustomException(400, "仅支持 http/https 外链");
        }
        if (uri.getUserInfo() != null) {
            throw new CustomException(400, "外链不可包含认证信息");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new CustomException(400, "无效的外链主机");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(hostLower)
                || hostLower.endsWith(".localhost")
                || "metadata.google.internal".equals(hostLower)) {
            throw new CustomException(400, "不允许访问内网地址");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            InetAddress pinned = null;
            for (InetAddress addr : addresses) {
                assertPublicAddress(addr);
                if (pinned == null) {
                    pinned = addr;
                }
            }
            if (pinned == null) {
                throw new CustomException(400, "无法解析外链主机");
            }
            return new Validated(uri, pinned);
        } catch (UnknownHostException e) {
            throw new CustomException(400, "无法解析外链主机");
        }
    }

    /**
     * 用已校验的公网 IP 建连，Host/SNI 仍使用原始主机名，避免二次 DNS 解析。
     */
    public static HttpURLConnection openPinnedConnection(Validated validated) throws IOException {
        URI uri = validated.uri();
        InetAddress ip = validated.pinnedAddress();
        String host = uri.getHost();
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        String file = buildFile(uri);
        // 对 IPv6 用方括号；URL 构造走字面量 IP，不再触发 DNS
        URL url = new URL(uri.getScheme(), ip.getHostAddress(), port, file);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String hostHeader = host;
        int defaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        if (uri.getPort() > 0 && uri.getPort() != defaultPort) {
            hostHeader = host + ":" + uri.getPort();
        }
        conn.setRequestProperty("Host", hostHeader);
        if (conn instanceof HttpsURLConnection https) {
            SSLSocketFactory defaultFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
            https.setSSLSocketFactory(new SniSslSocketFactory(defaultFactory, host));
            // 证书校验按原始主机名，而非字面量 IP
            https.setHostnameVerifier((hostname, session) ->
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session));
        }
        return conn;
    }

    private static String buildFile(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private static void assertPublicAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            throw new CustomException(400, "不允许访问内网地址");
        }
        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xff;
            int b1 = bytes[1] & 0xff;
            if (b0 == 169 && b1 == 254) {
                throw new CustomException(400, "不允许访问内网地址");
            }
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                throw new CustomException(400, "不允许访问内网地址");
            }
        }
    }

    /**
     * 在 TCP 连到字面量 IP 时仍通过 SNI 声明原始主机名，以便对端返回正确证书。
     */
    private static final class SniSslSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String serverName;

        SniSslSocketFactory(SSLSocketFactory delegate, String serverName) {
            this.delegate = delegate;
            this.serverName = serverName;
        }

        private Socket configure(Socket socket) {
            if (socket instanceof SSLSocket ssl) {
                SSLParameters params = ssl.getSSLParameters();
                params.setServerNames(java.util.List.of(new SNIHostName(serverName)));
                ssl.setSSLParameters(params);
            }
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(s, serverName, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return configure(delegate.createSocket(address, port, localAddress, localPort));
        }
    }
}
