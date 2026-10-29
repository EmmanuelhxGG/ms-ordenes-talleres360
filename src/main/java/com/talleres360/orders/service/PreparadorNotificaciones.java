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
          "ACEPTADA",
          "Solicitud aceptada",
          "LISTA_PARA_ENTREGA",
          "Solicitud lista para retirar o entregar",
          "CANCELADA",
          "Solicitud rechazada");

  private PreparadorNotificaciones() {}

  static List<ComandoNotificacion> preparar(WorkOrder orden, OutboxEvent evento) {
    if ("ENTREGADA".equals(evento.getType())) {
      String detalle = "TALLERES360 - COMPROBANTE DE ENTREGA\n"
          + "Solicitud #" + orden.getId() + "\nFecha: " + evento.getOccurredAt()
          + "\nCliente: " + orden.getCustomerName() + "\nCorreo: " + orden.getCustomerEmail()
          + "\nEntregado por: " + evento.getActor() + "\n\nProductos:\n"
          + orden.getItems().stream().map(i -> i.getDescription() + " x " + i.getQuantity()
              + " | Precio unitario: $" + i.getUnitPrice() + " | Subtotal: $" + i.subtotal() + " CLP")
              .collect(java.util.stream.Collectors.joining("\n"))
          + "\nMano de obra: $" + (orden.getLaborCost() == null ? 0 : orden.getLaborCost())
          + "\nTOTAL: $" + orden.getTotal() + " CLP\n\n"
          + "Comprobante interno de entrega. No acredita un pago ni corresponde a una boleta tributaria.";
      return List.of(comando(evento, "COMPROBANTE", orden.getCustomerEmail(),
          "Comprobante de entrega - Solicitud #" + orden.getId(), detalle));
    }
    String estado = ESTADOS.get(evento.getType());
    if (estado == null) return List.of();
    String servicio = orden.isCompraRepuestos() ? "Compra de repuestos" :
        orden.getServiceType() == null
            ? "Servicio de taller"
            : orden.getServiceType().name().equals("DIAGNOSTICS")
                ? "Diagnóstico"
                : "Mantenciones y arreglos";
    String asunto = "Orden #" + orden.getId() + ": " + estado;
    String explicacion = switch (evento.getType()) {
      case "ACEPTADA" -> "Tu solicitud de repuestos fue aceptada. Estamos revisando los productos para coordinar su preparación. Te enviaremos otro aviso cuando puedas retirarlos.";
      case "LISTA_PARA_ENTREGA" -> "Tu pedido ya está listo para ser retirado por ti en Talleres360. Presenta el número de solicitud para identificar tu pedido. Si necesitas coordinar la entrega, comunícate con el taller.";
      case "CANCELADA" -> "Tu solicitud fue rechazada o cancelada por el operador. A continuación puedes revisar el motivo informado. Si tienes dudas, comunícate con el taller indicando el número de solicitud.";
      default -> throw new IllegalArgumentException("Tipo de aviso no permitido");
    };
    String mensaje =
        "Hola " + orden.getCustomerName() + ",\n\n" + explicacion + "\n\n" + asunto
            + "\nGestionado por: " + evento.getActor()
            + "\nServicio: "
            + servicio
            + "\nTaller: "
            + (TallerActual.ID.equals(orden.getWorkshopId()) ? TallerActual.NOMBRE : "Taller histórico #" + orden.getWorkshopId())
            + (orden.isCompraRepuestos() ? "" : "\nVehículo: " + orden.getVehiclePlate())
            + (orden.getAppointmentDate() == null
                ? ""
                : "\nAtención: " + orden.getAppointmentDate())
            + (orden.getEstimatedDeliveryDate() == null
                ? ""
                : "\nEntrega estimada: " + orden.getEstimatedDeliveryDate())
            + "\nProductos:\n"
            + orden.getItems().stream().map(i -> i.getDescription() + " x " + i.getQuantity()
                + " - $" + i.subtotal() + " CLP").collect(java.util.stream.Collectors.joining("\n"))
            + "\nTotal: $" + orden.getTotal() + " CLP"
            + ("CANCELADA".equals(evento.getType()) ? "\nMotivo del rechazo: " + evento.getReason() : "")
            + "\n\nGracias por confiar en Talleres360. Este aviso se genera automáticamente a partir de la gestión del operador.";
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
        id, evento.getOrderId(), tipo, correo, asunto, mensaje, evento.getOccurredAt(), null);
  }
}
