package com.talleres360.orders.repository;

import com.talleres360.orders.model.OrderStatus;
import com.talleres360.orders.model.WorkOrder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

	List<WorkOrder> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String customerEmail);

	boolean existsByWorkshopIdAndAppointmentDateAndStatusNot(Long workshopId, LocalDate appointmentDate, OrderStatus status);

	List<WorkOrder> findByWorkshopIdAndAppointmentDateBetweenAndStatusNot(
			Long workshopId, LocalDate from, LocalDate to, OrderStatus status);

	/** Filtros opcionales: solo se agrega la condicion si el parametro viene (evita "? is null" que falla en PostgreSQL). */
	static Specification<WorkOrder> filter(OrderStatus status, LocalDateTime from, LocalDateTime to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
