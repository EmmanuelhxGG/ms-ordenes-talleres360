package com.talleres360.orders.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.orders.dto.ComandoNotificacion;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.repository.OutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PublicadorIntegraciones {
  private static final Logger log = LoggerFactory.getLogger(PublicadorIntegraciones.class);
  private final OutboxRepository repository;
  private final ObjectMapper json;
  private final RestClient auditoria;
  private final RestClient notificaciones;
  private final String clave;

  public PublicadorIntegraciones(
      OutboxRepository repository,
      ObjectMapper json,
      @Value("${AUDIT_URL:http://localhost:8085}") String auditUrl,
      @Value("${NOTIFICATIONS_URL:http://localhost:8084}") String notificationsUrl,
      @Value("${INTERNAL_API_KEY:}") String clave) {
    this.repository = repository;
    this.json = json;
    this.clave = clave;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(5000);
    auditoria = RestClient.builder().baseUrl(auditUrl).requestFactory(factory).build();
    notificaciones = RestClient.builder().baseUrl(notificationsUrl).requestFactory(factory).build();
  }

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void publicarAuditoria() {
    for (var evento : repository.pendientesAuditoria(PageRequest.of(0, 50))) {
      try {
        auditoria
            .post()
            .uri("/internal/events")
            .header("X-Internal-Key", clave)
            .body(Evento.desde(evento))
            .retrieve()
            .toBodilessEntity();
        repository.confirmarAuditoria(evento.getEventId());
      } catch (Exception ex) {
        log.warn(
            "Auditoría pendiente para {}: {}", evento.getEventId(), ex.getClass().getSimpleName());
      }
    }
  }

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void publicarNotificaciones() {
    for (var evento : repository.pendientesNotificaciones(PageRequest.of(0, 50))) {
      try {
        List<ComandoNotificacion> comandos =
            json.readValue(evento.getNotificacionesJson(), new TypeReference<>() {});
        for (var comando : comandos) {
          notificaciones
              .post()
              .uri("/internal/commands")
              .header("X-Internal-Key", clave)
              .body(comando)
              .retrieve()
              .toBodilessEntity();
        }
        repository.confirmarNotificaciones(evento.getEventId());
      } catch (Exception ex) {
        log.warn(
            "Notificación pendiente para {}: {}",
            evento.getEventId(),
            ex.getClass().getSimpleName());
      }
    }
  }

  private record Evento(
      String eventId,
      Long orderId,
      String type,
      String actor,
      String reason,
      Instant occurredAt,
      String status,
      BigDecimal total,
      String source,
      String correlationId) {
    static Evento desde(OutboxEvent e) {
      return new Evento(
          e.getEventId(),
          e.getOrderId(),
          e.getType(),
          e.getActor(),
          e.getReason(),
          e.getOccurredAt(),
          e.getStatus(),
          e.getTotal(),
          "orders",
          e.getEventId());
    }
  }
}
