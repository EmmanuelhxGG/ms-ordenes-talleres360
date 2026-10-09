package com.talleres360.orders.dto;

import java.time.Instant;

/** Contrato interno compartido con el receptor de notificaciones. */
public record ComandoNotificacion(
    String commandId,
    Long orderId,
    String tipo,
    String destinatario,
    String asunto,
    String mensaje,
    Instant occurredAt) {}
