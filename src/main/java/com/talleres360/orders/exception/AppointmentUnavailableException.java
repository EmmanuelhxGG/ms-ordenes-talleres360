package com.talleres360.orders.exception;

public class AppointmentUnavailableException extends RuntimeException {
	public AppointmentUnavailableException() {
		super("La fecha seleccionada ya no está disponible para ese taller");
	}
}
