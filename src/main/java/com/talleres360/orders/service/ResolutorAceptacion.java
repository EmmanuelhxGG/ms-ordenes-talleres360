package com.talleres360.orders.service;

import com.talleres360.orders.model.OrderStatus;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.repository.OutboxRepository;
import com.talleres360.orders.repository.WorkOrderRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Un conflicto definitivo no se reintenta como una caída de red ni confirma la aceptación. */
@Service
@RequiredArgsConstructor
public class ResolutorAceptacion {
  private final WorkOrderRepository ordenes;
  private final OutboxRepository outbox;
  private final OrderEventService eventos;

  @Transactional
  public void sinDisponibilidad(OutboxEvent intento, String nombreProducto) {
    var orden = ordenes.findLockedById(intento.getOrderId()).orElse(null);
    var evento = outbox.findById(intento.getEventId()).orElse(null);
    if (evento == null || evento.isStockSent() || Boolean.TRUE.equals(evento.getStockFallido())) return;
    if (orden == null || !orden.isCompraRepuestos() || !"ACTUALIZADA".equals(evento.getType())
        || !"RECIBIDA".equals(evento.getStatus()))
      throw new IllegalStateException("El conflicto no corresponde a un intento de aceptación de compra");
    evento.setStockFallido(true);
    if (!orden.isAceptacionPendiente()
        || orden.getStatus() != OrderStatus.RECIBIDA
        || !Objects.equals(orden.getStockRevision(), evento.getStockRevision())) return;
    String producto = nombreProducto == null || nombreProducto.isBlank() ? "Un producto" : nombreProducto;
    String aviso = producto + ": no hay disponibilidad suficiente para aceptar esta solicitud. "
        + "Permanece recibida. Puedes rechazarla indicando el motivo o volver a intentar cuando haya stock.";
    orden.setAceptacionPendiente(false);
    orden.setErrorAceptacion(aviso);
    // ACTUALIZADA se audita, pero no genera un correo de aceptación ni de rechazo.
    eventos.record(orden, "ACTUALIZADA", orden.getActorAceptacion(), aviso);
  }
}
