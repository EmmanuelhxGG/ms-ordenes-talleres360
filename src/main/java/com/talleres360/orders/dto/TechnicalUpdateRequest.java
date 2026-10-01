package com.talleres360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

public record TechnicalUpdateRequest(
		@NotBlank @Size(min = 10, max = 2000) String diagnosis,
		@NotBlank @Size(min = 10, max = 2000) String workPerformed,
		@NotNull @PositiveOrZero BigDecimal laborCost,
		@NotNull @FutureOrPresent LocalDate estimatedDeliveryDate,
		@NotNull @Valid List<Item> items
) {
	public record Item(
			@NotNull @Positive Long productId,
			@NotNull @Positive Integer quantity
	) {}
}
