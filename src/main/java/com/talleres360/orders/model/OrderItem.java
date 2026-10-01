package com.talleres360.orders.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Repuesto/servicio de la orden. productId referencia a ms-talleres360-catalog. */
@Entity
@Table(name = "WORK_ORDER_ITEMS")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ORDER_ID")
	private WorkOrder order;

	@Column(nullable = false)
	private Long productId;

	@Column(length = 160)
	private String description;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	public BigDecimal subtotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}
}
