package com.talleres360.orders.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.model.WorkOrder;
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
        e.setStockSent(!"ENTREGADA".equals(type));
        if ("ENTREGADA".equals(type)) {
            try {
                e.setItemsJson(json.writeValueAsString(order.getItems().stream()
                        .map(i -> new StockItem(i.getProductId(), i.getQuantity())).toList()));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("No se pudieron serializar los repuestos", ex);
            }
        }
        outbox.save(e);
    }
    public record StockItem(Long productId, Integer quantity) {}
}
