package com.talleres360.orders.exception;

import lombok.Getter;

@Getter
public class DisponibilidadProductoException extends RuntimeException {
  private final String codigo;
  private final Long productoId;

  public DisponibilidadProductoException(String codigo, Long productoId, String mensaje) {
    super(mensaje);
    this.codigo = codigo;
    this.productoId = productoId;
  }
}
