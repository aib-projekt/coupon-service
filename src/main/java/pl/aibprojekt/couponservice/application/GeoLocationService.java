package pl.aibprojekt.couponservice.application;

import reactor.core.publisher.Mono;

public interface GeoLocationService {
    Mono<String> getCountryCode(String ipAddress);
}
