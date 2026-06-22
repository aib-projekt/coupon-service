package pl.aibprojekt.couponservice.api.dto;

import java.time.Instant;

public record RedemptionResponse(String code, int remainingUses, Instant redeemedAt) {}
