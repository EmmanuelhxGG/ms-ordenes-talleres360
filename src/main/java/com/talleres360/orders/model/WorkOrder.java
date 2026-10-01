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
@Table(name = "WORK_ORDERS")
@Getter
@Setter
@NoArgsConstructor
public class WorkOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long workshopId;

	@Column(nullable = false, length = 120)
	private String customerName;

	@Column(nullable = false, length = 150)
	private String customerEmail;

	@Column(length = 12)
	private String customerRut;

	@Column(length = 8)
	private String customerPhone;

	@Column(nullable = false, length = 10)
	private String vehiclePlate;

	@Column(length = 120)
	private String vehicleModel;

	private Integer vehicleYear;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private ServiceType serviceType;

	@Column(length = 30)
	private String regionId;

	private LocalDate appointmentDate;

	@Column(length = 1000)
	private String description;

	@Column(length = 2000)
	private String diagnosis;

	@Column(length = 2000)
	private String workPerformed;

	@Column(precision = 12, scale = 2)
	private BigDecimal laborCost = BigDecimal.ZERO;

	private java.time.LocalDate estimatedDeliveryDate;
	private LocalDateTime technicalUpdatedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrderStatus status;

	@Column(precision = 12, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<OrderItem> items = new ArrayList<>();

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;
	private LocalDateTime acceptedAt;
	private LocalDateTime deliveredAt;

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
