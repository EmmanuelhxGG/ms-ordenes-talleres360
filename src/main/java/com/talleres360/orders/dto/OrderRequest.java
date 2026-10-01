package com.talleres360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record OrderRequest(
		@NotNull @Min(1) @Max(20) Long workshopId,
		@NotBlank @Size(min = 2, max = 120) @Pattern(regexp = "^[\\p{L}][\\p{L} '-]*$") String customerName,
		@NotBlank @Email @Size(max = 150) String customerEmail,
		@NotBlank @Size(max = 12) String customerRut,
		@NotBlank @Pattern(regexp = "^\\d{8}$") String customerPhone,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{2}-[A-Za-z0-9]{2}-[A-Za-z0-9]{2}$") String vehiclePlate,
		@Size(max = 120) @Pattern(regexp = "^[\\p{L}\\p{N} ]*$") String vehicleModel,
		@Size(min = 10, max = 1000) String description,
		@Valid List<Item> items
) {
	public record Item(
			@NotNull Long productId,
			@NotNull @Positive Integer quantity
	) {
	}
}
