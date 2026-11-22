package com.ordermanagement.inventory.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code delta} dương = nhập kho, âm = kiểm kê giảm. */
public record AdjustStockRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta) {
}
