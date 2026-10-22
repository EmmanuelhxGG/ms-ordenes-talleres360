package com.talleres360.orders.service;

import com.talleres360.orders.model.OrderStatus;
import com.talleres360.orders.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** La aceptación y su aviso solo se confirman cuando Catálogo persistió la reserva. */
@Component @RequiredArgsConstructor @Slf4j
public class ConfirmadorCompras {
  private final WorkOrderRepository repository;
  private final CatalogClient catalogo;
  private final OrderEventService eventos;
  private final PlatformTransactionManager transacciones;

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void confirmar() {
    for (var candidata : repository.findTop100ByAceptacionPendienteTrueOrderByCreatedAtAsc()) {
      try {
        new TransactionTemplate(transacciones).executeWithoutResult(t -> {
          var orden = repository.findLockedById(candidata.getId()).orElse(null);
          if (orden == null || !orden.isAceptacionPendiente() || orden.getStatus() != OrderStatus.RECIBIDA) return;
          var reserva = catalogo.reserva(orden.getId());
          if (reserva == null || orden.getStockRevision() == null || reserva.revision() < orden.getStockRevision()) return;
          orden.setAceptacionPendiente(false); orden.setStatus(OrderStatus.ACEPTADA);
          orden.setAcceptedAt(LocalDateTime.now());
          eventos.confirmarCompra(orden, orden.getActorAceptacion(), orden.getMotivoAceptacion());
        });
      } catch (RuntimeException error) {
        log.warn("Aceptación pendiente para solicitud {} ({})", candidata.getId(), error.getClass().getSimpleName());
      }
    }
  }
}
