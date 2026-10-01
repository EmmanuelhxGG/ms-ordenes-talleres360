package com.talleres360.orders.exception;

/** Regla de negocio violada: transicion de estado no permitida o edicion de una orden ya procesada. */
public class InvalidStatusTransitionException extends RuntimeException {
	public InvalidStatusTransitionException(String message) {
		super(message);
	}
}
