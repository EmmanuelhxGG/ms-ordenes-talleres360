package com.talleres360.orders;

import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.outbox.enabled=false")
@Transactional
class ColaStockTest {
    @Autowired OutboxRepository repo;

    @Test
    void unaCancelacionPosteriorNoQuedaBloqueadaPorCienRevisionesFallidas() {
        long ordenId = 987L;
        for (long revision = 1; revision <= 101; revision++) {
            var evento = new OutboxEvent();
            evento.setEventId(UUID.randomUUID().toString()); evento.setOrderId(ordenId);
            evento.setStockRevision(revision); evento.setType(revision == 101 ? "CANCELADA" : "INFORME_ACTUALIZADO");
            evento.setActor("operador"); evento.setOccurredAt(Instant.now());
            evento.setStatus("ACEPTADA"); evento.setTotal(BigDecimal.ZERO); evento.setItemsJson("[]");
            repo.save(evento);
        }
        var pendientes = repo.pendientesStock(PageRequest.of(0, 100)).stream()
                .filter(e -> e.getOrderId().equals(ordenId)).toList();
        assertEquals(1, pendientes.size());
        assertEquals(101L, pendientes.get(0).getStockRevision());
        assertEquals(101, repo.confirmarRevisiones(ordenId, 101L));
        assertTrue(repo.pendientesStock(PageRequest.of(0, 100)).stream()
                .noneMatch(e -> e.getOrderId().equals(ordenId)));
    }
}
