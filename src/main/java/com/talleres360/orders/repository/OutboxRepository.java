package com.talleres360.orders.repository;

import com.talleres360.orders.model.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
  // Una revisión fallida no debe impedir procesar una corrección o cancelación posterior.
  @Query(
      """
      select e from OutboxEvent e where e.stockSent = false and
      (e.stockRevision is null or not exists (
          select n from OutboxEvent n where n.orderId = e.orderId and n.stockRevision > e.stockRevision
      )) order by e.occurredAt asc
      """)
  List<OutboxEvent> pendientesStock(Pageable limite);

  @Modifying
  @Transactional
  @Query(
      "update OutboxEvent e set e.stockSent = true where e.orderId = :ordenId and e.stockRevision"
          + " <= :revision")
  int confirmarRevisiones(Long ordenId, Long revision);

  List<OutboxEvent> findTop100ByReportSentFalseAndStockSentTrueOrderByOccurredAtAsc();

  @Query(
      "select e from OutboxEvent e where e.auditoriaEnviada is null or e.auditoriaEnviada = false"
          + " order by e.occurredAt")
  List<OutboxEvent> pendientesAuditoria(Pageable limite);

  @Query(
      "select e from OutboxEvent e where e.notificacionesJson is not null and"
          + " (e.notificacionesEnviadas is null or e.notificacionesEnviadas = false) order by"
          + " e.occurredAt")
  List<OutboxEvent> pendientesNotificaciones(Pageable limite);

  // Actualizaciones por columna: no sobrescriben confirmaciones de otros publicadores.
  @Modifying
  @Transactional
  @Query("update OutboxEvent e set e.auditoriaEnviada = true where e.eventId = :id")
  int confirmarAuditoria(String id);

  @Modifying
  @Transactional
  @Query("update OutboxEvent e set e.notificacionesEnviadas = true where e.eventId = :id")
  int confirmarNotificaciones(String id);

  @Modifying
  @Transactional
  @Query("update OutboxEvent e set e.reportSent = true where e.eventId = :id")
  int confirmarReporte(String id);

  @Modifying
  @Transactional
  @Query("update OutboxEvent e set e.stockSent = true where e.eventId = :id")
  int confirmarStock(String id);
}
