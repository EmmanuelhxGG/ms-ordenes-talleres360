package com.talleres360.orders.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.talleres360.orders.dto.ComandoNotificacion;
import com.talleres360.orders.config.TopologiaRabbit;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbox de al menos una entrega: solo confirma después de la confirmación del broker. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.messaging.transport", havingValue = "rabbit", matchIfMissing = true)
public class PublicadorMensajeria {
  private final OutboxRepository repository;
  private final ObjectMapper json;
  private final RabbitTemplate rabbit;
  private final TopologiaRabbit topologia;

  @Value("${app.outbox.enabled:true}")
  private boolean habilitado;

  @Scheduled(fixedDelayString = "${OUTBOX_POLL_MS:1000}")
  public void publicarComandos() {
    if (!habilitado) return;
    for (var e : repository.pendientesNotificaciones(PageRequest.of(0, 50))) {
      try {
        List<ComandoNotificacion> comandos =
            json.readValue(e.getNotificacionesJson(), new TypeReference<>() {});
        for (var comando : comandos) {
          String ruta = topologia.destino(comando.tipo()).ruta();
          ObjectNode cuerpo = json.valueToTree(comando);
          cuerpo.put("type", comando.tipo());
          cuerpo.put("eventId", e.getEventId());
          cuerpo.put("timestamp", e.getOccurredAt().toString());
          cuerpo.put("traceId", e.getEventId());
          cuerpo.put("correlationId", e.getEventId());
          var confirmacion = new CorrelationData(comando.commandId());
          rabbit.convertAndSend(
              topologia.getDirecto(),
              ruta,
              cuerpo,
              mensaje -> {
                var propiedades = mensaje.getMessageProperties();
                propiedades.setMessageId(comando.commandId());
                propiedades.setCorrelationId(e.getEventId());
                propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return mensaje;
              },
              confirmacion);
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
