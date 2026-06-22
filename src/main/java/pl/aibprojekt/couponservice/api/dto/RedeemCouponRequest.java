package pl.aibprojekt.couponservice.api.dto;

import jakarta.validation.constraints.Size;

public record RedeemCouponRequest(@Size(max = 255) String userId) {}
