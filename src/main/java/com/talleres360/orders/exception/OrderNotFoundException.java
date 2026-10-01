package com.talleres360.orders.exception;

public class OrderNotFoundException extends RuntimeException {
	public OrderNotFoundException(Long id) {
		super("Orden " + id + " no encontrada");
	}
}
