package pl.aibprojekt.couponservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geo")
public record GeoProperties(
    String baseUrl,
    int connectTimeoutSeconds,
    int readTimeoutSeconds,
    int trustedProxyHops
) {}
