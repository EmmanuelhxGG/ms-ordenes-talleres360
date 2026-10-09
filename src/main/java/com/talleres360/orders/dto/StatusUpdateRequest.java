package com.talleres360.orders.dto;

import com.talleres360.orders.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
    @NotNull OrderStatus status, @jakarta.validation.constraints.Size(max = 500) String reason) {}
