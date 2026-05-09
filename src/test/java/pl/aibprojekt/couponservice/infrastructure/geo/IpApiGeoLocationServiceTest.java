package pl.aibprojekt.couponservice.infrastructure.geo;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;
import pl.aibprojekt.couponservice.infrastructure.config.GeoProperties;
import reactor.test.StepVerifier;

import java.io.IOException;

class IpApiGeoLocationServiceTest {

    private MockWebServer mockWebServer;
    private IpApiGeoLocationService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        GeoProperties geoProperties = new GeoProperties(baseUrl, 2, 3, 1);
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        service = new IpApiGeoLocationService(webClient, geoProperties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getCountryCode_shouldReturnCountryCode_whenSuccessResponse() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"status\":\"success\",\"countryCode\":\"PL\"}")
            .addHeader("Content-Type", "application/json"));

        // When / Then
        StepVerifier.create(service.getCountryCode("1.2.3.4"))
            .expectNext("PL")
            .verifyComplete();
    }

    @Test
    void getCountryCode_shouldThrowGeoUnavailable_whenStatusIsFail() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"status\":\"fail\",\"countryCode\":\"\"}")
            .addHeader("Content-Type", "application/json"));

        // When / Then
        StepVerifier.create(service.getCountryCode("1.2.3.4"))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.GEO_UNAVAILABLE)
            .verify();
    }

    @Test
    void getCountryCode_shouldThrowGeoUnavailable_whenNon2xxHttpResponse() {
        // Given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // When / Then
        StepVerifier.create(service.getCountryCode("1.2.3.4"))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.GEO_UNAVAILABLE)
            .verify();
    }

    @Test
    void getCountryCode_shouldThrowGeoUnavailable_whenNetworkTimeout() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"status\":\"success\",\"countryCode\":\"PL\"}")
            .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS));

        // When / Then
        StepVerifier.create(service.getCountryCode("1.2.3.4"))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.GEO_UNAVAILABLE)
            .verify();
    }

    @Test
    void getCountryCode_shouldThrowGeoUnavailable_whenCountryCodeIsNull() {
        // Given — success status but null countryCode
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"status\":\"success\",\"countryCode\":null}")
            .addHeader("Content-Type", "application/json"));

        // When / Then
        StepVerifier.create(service.getCountryCode("1.2.3.4"))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.GEO_UNAVAILABLE)
            .verify();
    }

    @Test
    void getCountryCode_shouldThrowGeoUnavailable_whenCountryCodeIsBlank() {
        // Given — success status but blank countryCode (covers the isBlank() branch)
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"status\":\"success\",\"countryCode\":\"  \"}")
            .addHeader("Content-Type", "application/json"));

        // When / Then
        StepVerifier.create(service.getCountryCode("1.2.3.4"))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.GEO_UNAVAILABLE)
            .verify();
    }
}
