package com.talleres360.orders.exception;

import lombok.Getter;

@Getter
public class DisponibilidadProductoException extends RuntimeException {
  private final String codigo;
  private final Long productoId;
  private final String nombreProducto;

  public DisponibilidadProductoException(String codigo, Long productoId, String mensaje) {
    this(codigo, productoId, null, mensaje);
  }

  public DisponibilidadProductoException(String codigo, Long productoId, String nombreProducto, String mensaje) {
    super(mensaje);
    this.codigo = codigo;
    this.productoId = productoId;
    this.nombreProducto = nombreProducto;
  }
}
