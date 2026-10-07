package com.talleres360.orders.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.model.WorkOrder;
import com.talleres360.orders.model.OrderStatus;
import com.talleres360.orders.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class OrderEventService {
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public void record(WorkOrder order, String type, String actor) {
        record(order, type, actor, null);
    }
    public void record(WorkOrder order, String type, String actor, String reason) {
        OutboxEvent e = new OutboxEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setOrderId(order.getId());
        e.setType(type);
        e.setActor(actor == null || actor.isBlank() ? "sistema" : actor);
        e.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        e.setOccurredAt(Instant.now());
        e.setStatus(order.getStatus().name());
        e.setTotal(order.getTotal());
        boolean liberar = "CANCELADA".equals(type) || ("ELIMINADA".equals(type)
                && order.getStatus() != OrderStatus.ENTREGADA);
        boolean regularizarOrdenAnterior = order.getStockRevision() == null
                && ("EN_REPARACION".equals(type) || "LISTA_PARA_ENTREGA".equals(type));
        boolean sincronizar = "ACEPTADA".equals(type) || "INFORME_ACTUALIZADO".equals(type)
                || (liberar && order.getStockRevision() != null) || regularizarOrdenAnterior;
        e.setStockSent(!sincronizar);
        if (sincronizar) {
            long revision = Math.addExact(order.getStockRevision() == null ? 0 : order.getStockRevision(), 1);
            order.setStockRevision(revision);
            e.setStockRevision(revision);
            try {
                e.setItemsJson(json.writeValueAsString(liberar ? java.util.List.of() : order.getItems().stream()
                        .map(i -> new StockItem(i.getProductId(), i.getQuantity())).toList()));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("No se pudieron serializar los repuestos", ex);
            }
        }
        outbox.save(e);
    }
    public record StockItem(Long productId, Integer quantity) {}
}
