package com.talleres360.orders.service;

import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final RestClient catalog;
    private final RestClient report;
    private final String key;

    public OutboxPublisher(OutboxRepository outbox, ObjectMapper json,
                           @Value("${CATALOG_URL:http://localhost:8082}") String catalogUrl,
                           @Value("${REPORT_URL:http://localhost:8083}") String reportUrl,
                           @Value("${INTERNAL_API_KEY:}") String key) {
        this.outbox = outbox; this.json = json; this.key = key;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000); factory.setReadTimeout(5_000);
        this.catalog = RestClient.builder().baseUrl(catalogUrl).requestFactory(factory).build();
        this.report = RestClient.builder().baseUrl(reportUrl).requestFactory(factory).build();
    }
    @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
    public void publish() {
        for (OutboxEvent e : outbox.findTop100ByReportSentFalseOrStockSentFalseOrderByOccurredAtAsc()) {
            if (!e.isStockSent()) {
                try {
                    List<OrderEventService.StockItem> items = json.readValue(e.getItemsJson(), new TypeReference<>() {});
                    catalog.post().uri("/internal/stock-consumptions").header("X-Internal-Key", key)
                            .body(new StockConsumption(e.getEventId(), e.getOrderId(), items)).retrieve().toBodilessEntity();
                    e.setStockSent(true); outbox.save(e);
                } catch (Exception ex) {
                    log.warn("Stock pendiente para evento {}: {}", e.getEventId(), ex.getMessage());
                }
            }
            // Una entrega no se contabiliza como venta mientras falle el descuento de stock.
            if (!e.isReportSent() && e.isStockSent()) {
                try {
                    report.post().uri("/internal/events").header("X-Internal-Key", key)
                            .body(new ReportEvent(e.getEventId(), e.getOrderId(), e.getType(), e.getActor(),
                                    e.getReason(), e.getOccurredAt(), e.getStatus(), e.getTotal()))
                            .retrieve().toBodilessEntity();
                    e.setReportSent(true); outbox.save(e);
                } catch (Exception ex) {
                    log.warn("Auditoría pendiente para evento {}: {}", e.getEventId(), ex.getMessage());
                }
            }
        }
    }
    private record StockConsumption(String eventId, Long orderId, List<OrderEventService.StockItem> items) {}
    private record ReportEvent(String eventId, Long orderId, String type, String actor, String reason,
                               java.time.Instant occurredAt, String status, java.math.BigDecimal total) {}
}
