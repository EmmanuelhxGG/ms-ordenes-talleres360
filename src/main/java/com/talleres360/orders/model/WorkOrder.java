package com.talleres360.orders.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solicitudes")
@Getter
@Setter
@NoArgsConstructor
public class WorkOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id") private Long id;

	@Column(name = "taller_id", nullable = false)
	private Long workshopId;

	@Column(name = "nombre_cliente", nullable = false, length = 120)
	private String customerName;

	@Column(name = "correo_cliente", nullable = false, length = 150)
	private String customerEmail;

	@Column(name = "rut_cliente", length = 12)
	private String customerRut;

	@Column(name = "telefono_cliente", length = 8)
	private String customerPhone;

	@Column(name = "patente_vehiculo", length = 10)
	private String vehiclePlate;

	@Column(name = "compra_repuestos", nullable = false, columnDefinition = "boolean default false")
	private boolean compraRepuestos;

	@Column(name = "aceptacion_pendiente", nullable = false, columnDefinition = "boolean default false")
	private boolean aceptacionPendiente;

	@Column(name = "error_aceptacion", length = 500)
	private String errorAceptacion;

	@Column(name = "usuario_aceptacion", length = 160)
	private String actorAceptacion;

	@Column(name = "motivo_aceptacion", length = 500)
	private String motivoAceptacion;

	@Column(name = "motivo_rechazo", length = 500)
	private String motivoRechazo;

	@Column(name = "identificador_solicitud", length = 36, unique = true)
	private String solicitudId;

	@Column(name = "modelo_vehiculo", length = 120)
	private String vehicleModel;

	@Column(name = "anio_vehiculo") private Integer vehicleYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_servicio", length = 30)
	private ServiceType serviceType;

	@Column(name = "region_id", length = 30)
	private String regionId;

	@Column(name = "fecha_atencion") private LocalDate appointmentDate;

	@Column(name = "descripcion", length = 1000)
	private String description;

	@Column(name = "diagnostico", length = 2000)
	private String diagnosis;

	@Column(name = "trabajo_realizado", length = 2000)
	private String workPerformed;

	@Column(name = "costo_mano_obra", precision = 12, scale = 2)
	private BigDecimal laborCost = BigDecimal.ZERO;

	@Column(name = "fecha_entrega_estimada") private java.time.LocalDate estimatedDeliveryDate;
	@Column(name = "informe_actualizado_en") private LocalDateTime technicalUpdatedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 30)
	private OrderStatus status;

	@Column(name = "total", precision = 12, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<OrderItem> items = new ArrayList<>();

	@Column(name = "creada_en", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "actualizada_en") private LocalDateTime updatedAt;
	@Column(name = "aceptada_en") private LocalDateTime acceptedAt;
	@Column(name = "entregada_en") private LocalDateTime deliveredAt;

	// Nullable para conservar órdenes anteriores al cambio de stock.
	@Column(name = "revision_existencias") private Long stockRevision;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
		if (status == null) {
			status = OrderStatus.RECIBIDA;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void replaceItems(List<OrderItem> newItems) {
		items.clear();
		newItems.forEach(item -> {
			item.setOrder(this);
			items.add(item);
		});
		total = items.stream()
				.map(OrderItem::subtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.add(laborCost == null ? BigDecimal.ZERO : laborCost);
	}
}
