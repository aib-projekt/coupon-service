package pl.aibprojekt.couponservice.api.dto;

import java.time.Instant;

public record CouponResponse(
    String code, int maxUses, int currentUses,
    String country, boolean perUserLimit, Instant createdAt
) {}
