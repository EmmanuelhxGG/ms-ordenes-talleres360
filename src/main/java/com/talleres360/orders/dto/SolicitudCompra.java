package com.talleres360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SolicitudCompra(
    @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String solicitudId,
    @NotBlank @Size(min = 3, max = 120) @Pattern(regexp = "^[\\p{L} ]+$") String nombre,
    @NotBlank @Size(max = 12) String rut,
    @NotBlank @Pattern(regexp = "^[0-9]{8}$") String telefono,
    @NotNull @Min(1) @Max(20) Long tallerId,
    @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> productos) {
  public record Item(@NotNull @Positive Long productoId, @Min(1) @Max(1000) int cantidad) {}
}
