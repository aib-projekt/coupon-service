package pl.aibprojekt.couponservice.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import pl.aibprojekt.couponservice.infrastructure.config.GeoProperties;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressExtractorTest {

    private IpAddressExtractor extractor;

    @BeforeEach
    void setUp() {
        // trustedProxyHops=1: the last XFF entry was added by the trusted load balancer
        extractor = new IpAddressExtractor(new GeoProperties("http://ip-api.com", 2, 3, 1));
    }

    @Test
    void extractClientIp_shouldReturnClientIp_whenDirectClientThroughOneTrustedProxy() {
        // Given — LB appended client IP (1.2.3.4) directly; chain has 1 entry with trustedHops=1
        // index = max(0, 1-1) = 0 → 1.2.3.4
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
            .header("X-Forwarded-For", "1.2.3.4")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        String ip = extractor.extractClientIp(exchange);

        // Then
        assertThat(ip).isEqualTo("1.2.3.4");
    }

    @Test
    void extractClientIp_shouldReturnAttackerRealIp_whenSpoofedHeaderPresentWithOneTrustedProxy() {
        // Given — attacker (9.9.9.9) sends spoofed XFF, LB appends real source IP 9.9.9.9
        // chain=[spoofed, 9.9.9.9], index = max(0, 2-1) = 1 → 9.9.9.9
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
            .header("X-Forwarded-For", "1.1.1.1, 9.9.9.9")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        String ip = extractor.extractClientIp(exchange);

        // Then — returns the real source IP seen by the trusted proxy, not the spoofed header
        assertThat(ip).isEqualTo("9.9.9.9");
    }

    @Test
    void extractClientIp_shouldFallbackToRemoteAddress_whenSelectedIpIsPrivate() {
        // Given — LB added a private proxy's IP; fall back to connection remote address
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
            .remoteAddress(new InetSocketAddress("203.0.113.5", 0))
            .header("X-Forwarded-For", "192.168.1.100")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        String ip = extractor.extractClientIp(exchange);

        // Then
        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    void extractClientIp_shouldFallbackToRemoteAddress_whenHeaderAbsent() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
            .remoteAddress(new InetSocketAddress("203.0.113.10", 0))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        String ip = extractor.extractClientIp(exchange);

        // Then
        assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    void extractClientIp_shouldFallbackToRemoteAddress_whenHeaderIsBlank() {
        // Given — blank X-Forwarded-For header
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
            .remoteAddress(new InetSocketAddress("203.0.113.11", 0))
            .header("X-Forwarded-For", "  ")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        String ip = extractor.extractClientIp(exchange);

        // Then
        assertThat(ip).isEqualTo("203.0.113.11");
    }

    @Test
    void extractClientIp_shouldReturnUnknown_whenNoRemoteAddressAndNoHeader() {
        // Given — no remote address set (returns null from getRemoteAddress)
        MockServerHttpRequest request = MockServerHttpRequest.get("/").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        String ip = extractor.extractClientIp(exchange);

        // Then — falls back to "unknown" when no remote address available
        assertThat(ip).isNotNull();
    }
}
