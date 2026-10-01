package com.talleres360.orders.controller;

import com.talleres360.orders.dto.AppointmentRequest;
import com.talleres360.orders.dto.AvailabilityResponse;
import com.talleres360.orders.dto.OrderResponse;
import com.talleres360.orders.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

	private static final String CUSTOMER_EMAIL_HEADER = "X-Customer-Email";
	private final AppointmentService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse create(
			@RequestHeader(CUSTOMER_EMAIL_HEADER) String customerEmail,
			@Valid @RequestBody AppointmentRequest request) {
		return service.create(customerEmail, request);
	}

	@GetMapping
	public List<OrderResponse> findMine(@RequestHeader(CUSTOMER_EMAIL_HEADER) String customerEmail) {
		return service.findMine(customerEmail);
	}

	@GetMapping("/availability")
	public AvailabilityResponse availability(
			@RequestParam Long workshopId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.availability(workshopId, from, to);
	}
}
