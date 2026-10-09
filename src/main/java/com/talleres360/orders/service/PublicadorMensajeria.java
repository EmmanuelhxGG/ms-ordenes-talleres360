package com.talleres360.orders.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.talleres360.orders.dto.ComandoNotificacion;
import com.talleres360.orders.repository.OutboxRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbox de al menos una entrega: solo confirma después de la confirmación del broker. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.messaging.transport", havingValue = "broker")
public class PublicadorMensajeria {
  private final OutboxRepository repository;
  private final ObjectMapper json;
  private final RabbitTemplate rabbit;
  private final KafkaTemplate<String, String> kafka;

  @Value("${app.kafka.topic:orders.events}")
  private String topico;

  @Value("${app.outbox.enabled:true}")
  private boolean habilitado;

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void publicarEventos() {
    if (!habilitado) return;
    for (var e : repository.pendientesKafka(PageRequest.of(0, 50))) {
      try {
        ObjectNode mensaje = json.createObjectNode();
        mensaje.put("eventId", e.getEventId());
        mensaje.put("orderId", e.getOrderId());
        mensaje.put("type", e.getType());
        mensaje.put("actor", e.getActor());
        mensaje.put("reason", e.getReason());
        mensaje.put("occurredAt", e.getOccurredAt().toString());
        mensaje.put("timestamp", e.getOccurredAt().toString());
        mensaje.put("status", e.getStatus());
        mensaje.put("total", e.getTotal());
        mensaje.put("source", "orders");
        mensaje.put("traceId", e.getEventId());
        mensaje.put("correlationId", e.getEventId());
        kafka
            .send(topico, e.getOrderId().toString(), json.writeValueAsString(mensaje))
            .get(15, TimeUnit.SECONDS);
        repository.confirmarKafka(e.getEventId());
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception error) {
        log.warn("Evento Kafka pendiente {}: {}", e.getEventId(), error.getClass().getSimpleName());
        return;
      }
    }
  }

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void publicarComandos() {
    if (!habilitado) return;
    for (var e : repository.pendientesNotificaciones(PageRequest.of(0, 50))) {
      try {
        List<ComandoNotificacion> comandos =
            json.readValue(e.getNotificacionesJson(), new TypeReference<>() {});
        for (var comando : comandos) {
          String ruta =
              switch (comando.tipo()) {
                case "CORREO" -> "email.send";
                case "TICKET_TALLER" -> "workshop.ticket";
                case "COMPROBANTE" -> "invoice.gen";
                default -> throw new IllegalArgumentException("Tipo de tarea desconocido");
              };
          ObjectNode cuerpo = json.valueToTree(comando);
          cuerpo.put("type", comando.tipo());
          cuerpo.put("eventId", e.getEventId());
          cuerpo.put("timestamp", e.getOccurredAt().toString());
          cuerpo.put("traceId", e.getEventId());
          cuerpo.put("correlationId", e.getEventId());
          Message mensaje =
              MessageBuilder.withBody(json.writeValueAsBytes(cuerpo))
                  .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                  .setMessageId(comando.commandId())
                  .setCorrelationId(e.getEventId())
                  .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                  .build();
          var confirmacion = new CorrelationData(comando.commandId());
          rabbit.send("cmd.direct", ruta, mensaje, confirmacion);
          if (!confirmacion.getFuture().get(10, TimeUnit.SECONDS).isAck()
              || confirmacion.getReturned() != null)
            throw new IllegalStateException("El broker no confirmó el enrutamiento");
        }
        repository.confirmarNotificaciones(e.getEventId());
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception error) {
        log.warn(
            "Tareas Rabbit pendientes {}: {}", e.getEventId(), error.getClass().getSimpleName());
        return;
      }
    }
  }
}
