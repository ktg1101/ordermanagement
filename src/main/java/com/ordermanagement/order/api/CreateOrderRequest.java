package com.ordermanagement.order.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ordermanagement.order.application.OrderLineCommand;

public record CreateOrderRequest(
        @NotNull UUID userId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> items) {

    public record Line(
            @NotNull UUID productId,
            @NotNull @Min(1) @Max(10_000) Integer quantity) {
    }

    List<OrderLineCommand> toCommands() {
        return items.stream().map(line -> new OrderLineCommand(line.productId(), line.quantity())).toList();
    }
}
