package com.talleres360.orders.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Repuesto/servicio de la orden. productId referencia a ms-talleres360-catalog. */
@Entity
@Table(name = "detalle_solicitudes")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id") private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id")
	private WorkOrder order;

	@Column(name = "producto_id", nullable = false)
	private Long productId;

	@Column(name = "descripcion", length = 160)
	private String description;

	@Column(name = "cantidad", nullable = false)
	private Integer quantity;

	@Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	public BigDecimal subtotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}
}
