package com.talleres360.orders.model;

import java.util.Set;

public enum OrderStatus {
	RECIBIDA,
	ACEPTADA,
	EN_REPARACION,
	LISTA_PARA_ENTREGA,
	ENTREGADA,
	CANCELADA;

	/** Flujo: RECIBIDA -> ACEPTADA -> EN_REPARACION -> LISTA_PARA_ENTREGA -> ENTREGADA (se puede cancelar antes de entregar). */
	public Set<OrderStatus> allowedTransitions() {
		return switch (this) {
			case RECIBIDA -> Set.of(ACEPTADA, CANCELADA);
			case ACEPTADA -> Set.of(EN_REPARACION, CANCELADA);
			case EN_REPARACION -> Set.of(LISTA_PARA_ENTREGA, CANCELADA);
			case LISTA_PARA_ENTREGA -> Set.of(ENTREGADA, CANCELADA);
			case ENTREGADA, CANCELADA -> Set.of();
		};
	}

	public boolean canTransitionTo(OrderStatus target) {
		return allowedTransitions().contains(target);
	}
}
