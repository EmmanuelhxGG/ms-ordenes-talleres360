package com.talleres360.orders.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Contrato interno compartido con el receptor de notificaciones. */
public record ComandoNotificacion(
    String commandId,
    @JsonProperty("pedidoId") @JsonAlias("orderId") Long orderId,
    String tipo,
    @JsonProperty("email") @JsonAlias("destinatario") String destinatario,
    String asunto,
    String mensaje,
    Instant occurredAt,
    String comprobanteId) {}
