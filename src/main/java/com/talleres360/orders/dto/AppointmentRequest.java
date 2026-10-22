package com.talleres360.orders.dto;

import com.talleres360.orders.model.ServiceType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record AppointmentRequest(
		@NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[\\p{L}][\\p{L} '-]*$") String firstName,
		@NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[\\p{L}][\\p{L} '-]*$") String lastName,
		@NotBlank @Size(max = 12) String rut,
		@NotBlank @Pattern(regexp = "^\\d{8}$") String phone,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{2}-[A-Za-z0-9]{2}-[A-Za-z0-9]{2}$") String vehiclePlate,
		@NotBlank @Size(min = 2, max = 60) @Pattern(regexp = "^[\\p{L}\\p{N} ]+$") String vehicleModel,
		@NotNull @Min(1900) Integer vehicleYear,
		@NotNull ServiceType serviceType,
		@NotBlank @Size(min = 10, max = 500) String reason,
		@NotNull @Future LocalDate appointmentDate
) {}
