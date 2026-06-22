package pl.aibprojekt.couponservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import pl.aibprojekt.couponservice.api.dto.CouponResponse;
import pl.aibprojekt.couponservice.api.dto.CreateCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedeemCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedemptionResponse;
import pl.aibprojekt.couponservice.application.CouponService;
import pl.aibprojekt.couponservice.infrastructure.IpAddressExtractor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/coupons")
@Validated
public class CouponController {

    private final CouponService couponService;
    private final IpAddressExtractor ipAddressExtractor;

    public CouponController(CouponService couponService, IpAddressExtractor ipAddressExtractor) {
        this.couponService = couponService;
        this.ipAddressExtractor = ipAddressExtractor;
    }

    @PostMapping
    public Mono<ResponseEntity<CouponResponse>> createCoupon(
            @Valid @RequestBody CreateCouponRequest request) {
        return couponService.createCoupon(request)
            .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping("/{code}")
    public Mono<ResponseEntity<CouponResponse>> getCoupon(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9]{1,64}", message = "Invalid coupon code format") String code) {
        return couponService.getCoupon(code.toUpperCase())
            .map(ResponseEntity::ok);
    }

    @PostMapping("/{code}/redeem")
    public Mono<ResponseEntity<RedemptionResponse>> redeemCoupon(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9]{1,64}", message = "Invalid coupon code format") String code,
            @Valid @RequestBody(required = false) RedeemCouponRequest request,
            ServerWebExchange exchange) {
        String clientIp = ipAddressExtractor.extractClientIp(exchange);
        RedeemCouponRequest safeRequest = request != null ? request : new RedeemCouponRequest(null);
        return couponService.redeemCoupon(code.toUpperCase(), clientIp, safeRequest)
            .map(ResponseEntity::ok);
    }
}
