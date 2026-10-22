package com.talleres360.orders.service;

import com.talleres360.orders.dto.AppointmentRequest;
import com.talleres360.orders.dto.AvailabilityResponse;
import com.talleres360.orders.dto.OrderResponse;
import com.talleres360.orders.model.OrderStatus;
import com.talleres360.orders.model.WorkOrder;
import com.talleres360.orders.repository.WorkOrderRepository;
import com.talleres360.orders.validation.RutValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

	private final WorkOrderRepository repository;
	private final OrderEventService events;

	@Transactional
	public OrderResponse create(String authenticatedEmail, AppointmentRequest request) {
		validate(authenticatedEmail, request);
		WorkOrder order = new WorkOrder();
		order.setWorkshopId(TallerActual.ID);
		order.setCustomerName(request.firstName().trim() + " " + request.lastName().trim());
		order.setCustomerEmail(authenticatedEmail.trim().toLowerCase());
		order.setCustomerRut(request.rut().toUpperCase());
		order.setCustomerPhone(request.phone());
		order.setVehiclePlate(request.vehiclePlate().toUpperCase());
		order.setVehicleModel(request.vehicleModel().trim());
		order.setVehicleYear(request.vehicleYear());
		order.setServiceType(request.serviceType());
		order.setAppointmentDate(request.appointmentDate());
		order.setDescription(request.reason().trim());
		order.setStatus(OrderStatus.RECIBIDA);
		OrderResponse response = OrderResponse.from(repository.save(order));
		events.record(order, "CREADA", authenticatedEmail);
		return response;
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> findMine(String authenticatedEmail) {
		requireEmail(authenticatedEmail);
		return repository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(authenticatedEmail.trim())
				.stream().map(OrderResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AvailabilityResponse availability(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("Los datos de disponibilidad no son válidos");
		}
		return new AvailabilityResponse(List.of());
	}

	private void validate(String email, AppointmentRequest request) {
		requireEmail(email);
		if (request.vehicleYear() > LocalDate.now().getYear() + 1) {
			throw new IllegalArgumentException("El año del vehículo no es válido");
		}
		if (!RutValidator.isValid(request.rut())) {
			throw new IllegalArgumentException("El RUT ingresado no es válido");
		}
		if (request.appointmentDate().isAfter(LocalDate.now().plusDays(90))) {
			throw new IllegalArgumentException("La fecha debe estar dentro de los próximos 90 días");
		}
	}

	private void requireEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("No fue posible identificar al cliente autenticado");
		}
	}

}
