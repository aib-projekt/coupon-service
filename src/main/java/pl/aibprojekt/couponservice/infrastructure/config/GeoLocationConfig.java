package pl.aibprojekt.couponservice.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import pl.aibprojekt.couponservice.application.GeoLocationService;
import pl.aibprojekt.couponservice.infrastructure.geo.IpApiGeoLocationService;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GeoProperties.class)
public class GeoLocationConfig {

    @Bean
    public GeoLocationService geoLocationService(WebClient.Builder webClientBuilder, GeoProperties geoProperties) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) Duration.ofSeconds(geoProperties.connectTimeoutSeconds()).toMillis());

        WebClient webClient = webClientBuilder
            .baseUrl(geoProperties.baseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();

        return new IpApiGeoLocationService(webClient, geoProperties);
    }
}
