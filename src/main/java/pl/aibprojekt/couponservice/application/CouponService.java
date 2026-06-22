package pl.aibprojekt.couponservice.application;

import pl.aibprojekt.couponservice.api.dto.CouponResponse;
import pl.aibprojekt.couponservice.api.dto.CreateCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedeemCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedemptionResponse;
import reactor.core.publisher.Mono;

public interface CouponService {
    Mono<CouponResponse> createCoupon(CreateCouponRequest request);
    Mono<CouponResponse> getCoupon(String code);
    Mono<RedemptionResponse> redeemCoupon(String code, String clientIp, RedeemCouponRequest request);
}
