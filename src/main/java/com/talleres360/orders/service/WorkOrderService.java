package com.talleres360.orders.service;

import com.talleres360.orders.dto.OrderRequest;
import com.talleres360.orders.dto.OrderResponse;
import com.talleres360.orders.dto.TechnicalUpdateRequest;
import com.talleres360.orders.exception.InvalidStatusTransitionException;
import com.talleres360.orders.exception.OrderNotFoundException;
import com.talleres360.orders.model.OrderItem;
import com.talleres360.orders.model.OrderStatus;
import com.talleres360.orders.model.WorkOrder;
import com.talleres360.orders.repository.WorkOrderRepository;
import com.talleres360.orders.validation.RutValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

	private final WorkOrderRepository repository;
	private final CatalogClient catalogClient;
	private final OrderEventService events;

	@Transactional
	public OrderResponse create(OrderRequest request, String actor) {
		WorkOrder order = new WorkOrder();
		order.setStatus(OrderStatus.RECIBIDA);
		apply(order, request);
		OrderResponse response = OrderResponse.from(repository.save(order));
		events.record(order, "CREADA", actor);
		return response;
	}

	@Transactional(readOnly = true)
	public OrderResponse findById(Long id) {
		return OrderResponse.from(get(id));
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> search(OrderStatus status, LocalDateTime from, LocalDateTime to) {
		return repository.findAll(WorkOrderRepository.filter(status, from, to), Sort.by(Sort.Direction.DESC, "createdAt"))
				.stream().map(OrderResponse::from).toList();
	}

	@Transactional
	public OrderResponse update(Long id, OrderRequest request, String actor) {
		WorkOrder order = get(id);
		if (order.getStatus() != OrderStatus.RECIBIDA) {
			throw new InvalidStatusTransitionException(
					"Solo se puede editar una orden en estado RECIBIDA (estado actual: " + order.getStatus() + ")");
		}
		apply(order, request);
		OrderResponse response = OrderResponse.from(repository.save(order));
		events.record(order, "ACTUALIZADA", actor);
		return response;
	}

	@Transactional
	public OrderResponse changeStatus(Long id, OrderStatus newStatus, String actor, String actorRole, String reason) {
		WorkOrder order = get(id);
		OrderStatus current = order.getStatus();

		if (!current.canTransitionTo(newStatus)) {
			throw new InvalidStatusTransitionException(
					"No se puede pasar de " + current + " a " + newStatus
							+ ". Permitidos: " + current.allowedTransitions());
		}
		if ("Admin".equals(actorRole) && (reason == null || reason.trim().length() < 10)) {
			throw new IllegalArgumentException("El administrador debe justificar su intervención (mínimo 10 caracteres)");
		}
		if (newStatus == OrderStatus.LISTA_PARA_ENTREGA
				&& (order.getDiagnosis() == null || order.getDiagnosis().isBlank()
				|| order.getWorkPerformed() == null || order.getWorkPerformed().isBlank())) {
			throw new InvalidStatusTransitionException(
					"Debes registrar el diagnóstico y el trabajo realizado antes de marcar la orden como lista");
		}
		if (newStatus == OrderStatus.ENTREGADA) {
			var requested = order.getItems().stream().collect(java.util.stream.Collectors.groupingBy(
					OrderItem::getProductId, java.util.stream.Collectors.summingInt(OrderItem::getQuantity)));
			for (var entry : requested.entrySet()) {
				var product = catalogClient.product(entry.getKey());
				if (product == null || !product.active() || product.stock() < entry.getValue()) {
					throw new IllegalArgumentException("No hay stock suficiente para entregar la orden");
				}
			}
		}

		order.setStatus(newStatus);
		LocalDateTime now = LocalDateTime.now();
		if (newStatus == OrderStatus.ACEPTADA) {
			order.setAcceptedAt(now);
		}
		if (newStatus == OrderStatus.ENTREGADA) {
			order.setDeliveredAt(now);
		}
		OrderResponse response = OrderResponse.from(repository.save(order));
		events.record(order, newStatus.name(), actor, reason);
		return response;
	}

	@Transactional
	public OrderResponse updateTechnicalDetails(Long id, TechnicalUpdateRequest request, String actor) {
		WorkOrder order = get(id);
		if (order.getStatus() != OrderStatus.ACEPTADA && order.getStatus() != OrderStatus.EN_REPARACION
				&& order.getStatus() != OrderStatus.LISTA_PARA_ENTREGA) {
			throw new InvalidStatusTransitionException(
					"Primero se debe aceptar la solicitud para registrar el diagnóstico y los costos");
		}

		order.setDiagnosis(request.diagnosis().trim());
		order.setWorkPerformed(request.workPerformed().trim());
		order.setLaborCost(request.laborCost());
		order.setEstimatedDeliveryDate(request.estimatedDeliveryDate());
		order.setTechnicalUpdatedAt(LocalDateTime.now());
		List<OrderItem> newItems = request.items().stream().map(i -> {
			var product = catalogClient.product(i.productId());
			if (product == null || !product.active() || product.stock() < i.quantity()) {
				throw new IllegalArgumentException("El producto seleccionado no tiene stock suficiente");
			}
			OrderItem item = new OrderItem();
			item.setProductId(product.id());
			item.setDescription(product.name());
			item.setQuantity(i.quantity());
			item.setUnitPrice(product.price());
			return item;
		}).toList();
		validateStock(newItems);
		order.replaceItems(newItems);
		OrderResponse response = OrderResponse.from(repository.save(order));
		events.record(order, "INFORME_ACTUALIZADO", actor);
		return response;
	}

	@Transactional
	public void delete(Long id, String actor) {
		WorkOrder order = get(id);
		events.record(order, "ELIMINADA", actor);
		repository.delete(order);
	}

	private WorkOrder get(Long id) {
		return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	private void apply(WorkOrder order, OrderRequest request) {
		if (!RutValidator.isValid(request.customerRut())) {
			throw new IllegalArgumentException("El RUT ingresado no es válido");
		}
		order.setWorkshopId(request.workshopId());
		order.setCustomerName(request.customerName());
		order.setCustomerEmail(request.customerEmail());
		order.setCustomerRut(request.customerRut());
		order.setCustomerPhone(request.customerPhone());
		order.setVehiclePlate(request.vehiclePlate().toUpperCase());
		order.setVehicleModel(request.vehicleModel());
		order.setDescription(request.description());

		List<OrderRequest.Item> items = request.items() == null ? List.of() : request.items();
		List<OrderItem> newItems = items.stream().map(i -> {
			var product = catalogClient.product(i.productId());
			if (product == null || !product.active() || product.stock() < i.quantity()) {
				throw new IllegalArgumentException("El producto seleccionado no tiene stock suficiente");
			}
			OrderItem item = new OrderItem();
			item.setProductId(product.id());
			item.setDescription(product.name());
			item.setQuantity(i.quantity());
			item.setUnitPrice(product.price());
			return item;
		}).toList();
		validateStock(newItems);
		order.replaceItems(newItems);
	}

	private void validateStock(List<OrderItem> items) {
		var requested = items.stream().collect(java.util.stream.Collectors.groupingBy(
				OrderItem::getProductId, java.util.stream.Collectors.summingInt(OrderItem::getQuantity)));
		for (var entry : requested.entrySet()) {
			var product = catalogClient.product(entry.getKey());
			if (product == null || !product.active() || product.stock() < entry.getValue()) {
				throw new IllegalArgumentException("El producto seleccionado no tiene stock suficiente");
			}
		}
	}
}
