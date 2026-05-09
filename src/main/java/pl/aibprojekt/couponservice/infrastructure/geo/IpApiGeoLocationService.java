package pl.aibprojekt.couponservice.infrastructure.geo;

import org.springframework.web.reactive.function.client.WebClient;
import pl.aibprojekt.couponservice.application.GeoLocationService;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;
import pl.aibprojekt.couponservice.infrastructure.config.GeoProperties;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class IpApiGeoLocationService implements GeoLocationService {

    private final WebClient webClient;
    private final GeoProperties geoProperties;

    public IpApiGeoLocationService(WebClient webClient, GeoProperties geoProperties) {
        this.webClient = webClient;
        this.geoProperties = geoProperties;
    }

    @Override
    public Mono<String> getCountryCode(String ipAddress) {
        return webClient.get()
            .uri("/json/{ip}?fields=status,countryCode", ipAddress)
            .retrieve()
            .bodyToMono(GeoResponse.class)
            .timeout(Duration.ofSeconds(geoProperties.readTimeoutSeconds()))
            .flatMap(response -> {
                if (!"success".equals(response.status()) || response.countryCode() == null || response.countryCode().isBlank()) {
                    return Mono.error(new CouponException(CouponErrorCode.GEO_UNAVAILABLE,
                        "Geolocation service returned invalid response."));
                }
                return Mono.just(response.countryCode());
            })
            .onErrorMap(ex -> !(ex instanceof CouponException),
                ex -> new CouponException(CouponErrorCode.GEO_UNAVAILABLE,
                    "Geolocation service is unavailable."));
    }

    private record GeoResponse(String status, String countryCode) {}
}
