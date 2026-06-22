package pl.aibprojekt.couponservice.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pl.aibprojekt.couponservice.infrastructure.config.GeoProperties;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class IpAddressExtractor {

    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
        "^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
        "172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}|" +
        "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
        "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
        "::1)$"
    );

    private final int trustedProxyHops;

    public IpAddressExtractor(GeoProperties geoProperties) {
        this.trustedProxyHops = geoProperties.trustedProxyHops();
    }

    public String extractClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            List<String> chain = Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .toList();

            if (!chain.isEmpty()) {
                // With N trusted proxy hops, the Nth entry from the right is the first IP
                // added by a trusted proxy — i.e., the real client IP as seen by the first
                // trusted hop. This prevents spoofing via a caller-controlled XFF header.
                int targetIndex = Math.max(0, chain.size() - trustedProxyHops);
                String ip = chain.get(Math.min(targetIndex, chain.size() - 1));
                if (!isPrivate(ip)) {
                    return ip;
                }
            }
        }
        return getRemoteAddress(exchange);
    }

    private boolean isPrivate(String ip) {
        return PRIVATE_IP_PATTERN.matcher(ip).matches();
    }

    private String getRemoteAddress(ServerWebExchange exchange) {
        var address = exchange.getRequest().getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }
}
