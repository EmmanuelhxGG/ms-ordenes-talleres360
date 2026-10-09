package com.talleres360.orders.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.repository.OutboxRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.outbox.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final RestClient catalog;
  private final RestClient report;
  private final String key;

  public OutboxPublisher(
      OutboxRepository outbox,
      ObjectMapper json,
      @Value("${CATALOG_URL:http://localhost:8082}") String catalogUrl,
      @Value("${REPORT_URL:http://localhost:8083}") String reportUrl,
      @Value("${INTERNAL_API_KEY:}") String key) {
    this.outbox = outbox;
    this.json = json;
    this.key = key;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2_000);
    factory.setReadTimeout(5_000);
    this.catalog = RestClient.builder().baseUrl(catalogUrl).requestFactory(factory).build();
    this.report = RestClient.builder().baseUrl(reportUrl).requestFactory(factory).build();
  }

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void publish() {
    for (OutboxEvent e :
        outbox.pendientesStock(org.springframework.data.domain.PageRequest.of(0, 100))) {
      if (!e.isStockSent()) {
        try {
          List<OrderEventService.StockItem> items =
              json.readValue(e.getItemsJson(), new TypeReference<>() {});
          if (e.getStockRevision() == null) {
            // Compatibilidad: entregas pendientes producidas antes de esta versión.
            catalog
                .post()
                .uri("/internal/stock-consumptions")
                .header("X-Internal-Key", key)
                .body(new StockConsumption(e.getEventId(), e.getOrderId(), items))
                .retrieve()
                .toBodilessEntity();
            e.setStockSent(true);
            outbox.confirmarStock(e.getEventId());
          } else {
            catalog
                .put()
                .uri("/internal/stock-reservations")
                .header("X-Internal-Key", key)
                .body(new StockReservation(e.getOrderId(), e.getStockRevision(), items))
                .retrieve()
                .toBodilessEntity();
            outbox.confirmarRevisiones(e.getOrderId(), e.getStockRevision());
            e.setStockSent(true);
          }
        } catch (Exception ex) {
          log.warn(
              "Stock pendiente para evento {}: {}", e.getEventId(), ex.getClass().getSimpleName());
        }
      }
    }
    // Las caídas de Reportería no deben ocupar el lote de stock e impedir aceptar/cancelar órdenes.
    for (OutboxEvent e : outbox.findTop100ByReportSentFalseAndStockSentTrueOrderByOccurredAtAsc()) {
      if (!e.isReportSent() && e.isStockSent()) {
        try {
          report
              .post()
              .uri("/internal/events")
              .header("X-Internal-Key", key)
              .body(
                  new ReportEvent(
                      e.getEventId(),
                      e.getOrderId(),
                      e.getType(),
                      e.getActor(),
                      e.getReason(),
                      e.getOccurredAt(),
                      e.getStatus(),
                      e.getTotal()))
              .retrieve()
              .toBodilessEntity();
          e.setReportSent(true);
          outbox.confirmarReporte(e.getEventId());
        } catch (Exception ex) {
          log.warn(
              "Reportería pendiente para evento {}: {}",
              e.getEventId(),
              ex.getClass().getSimpleName());
        }
      }
    }
  }

  private record StockConsumption(
      String eventId, Long orderId, List<OrderEventService.StockItem> items) {}

  private record StockReservation(
      Long orderId, long revision, List<OrderEventService.StockItem> items) {}

  private record ReportEvent(
      String eventId,
      Long orderId,
      String type,
      String actor,
      String reason,
      java.time.Instant occurredAt,
      String status,
      java.math.BigDecimal total) {}
}
