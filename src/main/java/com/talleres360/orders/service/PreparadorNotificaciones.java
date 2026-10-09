package com.talleres360.orders.service;

import com.talleres360.orders.dto.ComandoNotificacion;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.model.WorkOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Construye una instantánea: los reintentos no consultan una orden que pudo cambiar. */
final class PreparadorNotificaciones {
  private static final Map<String, String> ESTADOS =
      Map.of(
          "CREADA",
          "Solicitud recibida",
          "ACEPTADA",
          "Solicitud aceptada",
          "LISTA_PARA_ENTREGA",
          "Vehículo listo para entrega",
          "ENTREGADA",
          "Vehículo entregado",
          "CANCELADA",
          "Solicitud cancelada");

  private PreparadorNotificaciones() {}

  static List<ComandoNotificacion> preparar(WorkOrder orden, OutboxEvent evento) {
    String estado = ESTADOS.get(evento.getType());
    if (estado == null) return List.of();
    String servicio =
        orden.getServiceType() == null
            ? "Servicio de taller"
            : orden.getServiceType().name().equals("DIAGNOSTICS")
                ? "Diagnóstico"
                : "Mantenciones y arreglos";
    String asunto = "Orden #" + orden.getId() + ": " + estado;
    String mensaje =
        asunto
            + "\nServicio: "
            + servicio
            + "\nTaller: "
            + orden.getWorkshopId()
            + "\nVehículo: "
            + orden.getVehiclePlate()
            + (orden.getAppointmentDate() == null
                ? ""
                : "\nAtención: " + orden.getAppointmentDate())
            + (orden.getEstimatedDeliveryDate() == null
                ? ""
                : "\nEntrega estimada: " + orden.getEstimatedDeliveryDate());
    String ticket = mensaje + "\nMotivo del cliente: " + orden.getDescription();
    return List.of(
        comando(evento, "CORREO", orden.getCustomerEmail(), asunto, mensaje),
        comando(evento, "TICKET_TALLER", null, asunto, ticket));
  }

  private static ComandoNotificacion comando(
      OutboxEvent evento, String tipo, String correo, String asunto, String mensaje) {
    String id =
        UUID.nameUUIDFromBytes((evento.getEventId() + ":" + tipo).getBytes(StandardCharsets.UTF_8))
            .toString();
    return new ComandoNotificacion(
        id, evento.getOrderId(), tipo, correo, asunto, mensaje, evento.getOccurredAt());
  }
}
