package pl.aibprojekt.couponservice.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import pl.aibprojekt.couponservice.api.validation.ValidCountryCode;

public record CreateCouponRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,64}") String code,
    @Min(1) int maxUses,
    @NotBlank @ValidCountryCode String country,
    boolean perUserLimit
) {}
