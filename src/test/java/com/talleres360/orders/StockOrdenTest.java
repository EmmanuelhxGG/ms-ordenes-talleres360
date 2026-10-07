package com.talleres360.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.orders.dto.TechnicalUpdateRequest;
import com.talleres360.orders.model.*;
import com.talleres360.orders.repository.*;
import com.talleres360.orders.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockOrdenTest {
    @Test
    void aceptacionEInformeGeneranAsignacionesPeroEntregaNoDescuentaOtraVez() throws Exception {
        var repo = mock(OutboxRepository.class);
        var json = new ObjectMapper();
        var servicio = new OrderEventService(repo, json);
        var orden = orden();
        orden.setStatus(OrderStatus.ACEPTADA);
        servicio.record(orden, "ACEPTADA", "operador");
        servicio.record(orden, "INFORME_ACTUALIZADO", "operador");
        orden.setStatus(OrderStatus.ENTREGADA);
        servicio.record(orden, "ENTREGADA", "operador");
        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo, times(3)).save(captor.capture());
        var eventos = captor.getAllValues();
        assertEquals(1L, eventos.get(0).getStockRevision());
        assertFalse(eventos.get(0).isStockSent());
        assertEquals(2L, eventos.get(1).getStockRevision());
        assertTrue(eventos.get(2).isStockSent());
        assertNull(eventos.get(2).getItemsJson());
        servicio.record(orden, "ELIMINADA", "admin");
        assertEquals(2L, orden.getStockRevision(), "Eliminar una entrega no devuelve repuestos consumidos");
    }

    @Test
    void cancelarUnaOrdenActivaSolicitaLiberarLosRepuestos() {
        var repo = mock(OutboxRepository.class);
        var servicio = new OrderEventService(repo, new ObjectMapper());
        var orden = orden();
        orden.setStockRevision(1L);
        orden.setStatus(OrderStatus.CANCELADA);
        servicio.record(orden, "CANCELADA", "operador");
        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo).save(captor.capture());
        assertEquals("[]", captor.getValue().getItemsJson());
        assertEquals(2L, captor.getValue().getStockRevision());
        assertFalse(captor.getValue().isStockSent());
    }

    @Test
    void editarPermiteConservarStockYaAsignadoSinVolverADescontarlo() {
        var repo = mock(WorkOrderRepository.class);
        var catalogo = mock(CatalogClient.class);
        var eventos = mock(OrderEventService.class);
        var servicio = new WorkOrderService(repo, catalogo, eventos);
        var orden = orden();
        orden.setStatus(OrderStatus.ACEPTADA);
        when(repo.findLockedById(7L)).thenReturn(Optional.of(orden));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(catalogo.product(1L)).thenReturn(new CatalogClient.Product(1L, "A", "Filtro", BigDecimal.TEN, 0, true, false));
        when(catalogo.reserva(7L)).thenReturn(new CatalogClient.Reserva(1, Map.of(1L, 2)));
        var solicitud = new TechnicalUpdateRequest("Diagnóstico registrado", "Trabajo realizado", BigDecimal.TEN,
                LocalDate.now(), List.of(new TechnicalUpdateRequest.Item(1L, 2)));
        assertEquals(2, servicio.updateTechnicalDetails(7L, solicitud, "operador").items().get(0).quantity());
        var exceso = new TechnicalUpdateRequest(solicitud.diagnosis(), solicitud.workPerformed(), BigDecimal.TEN,
                LocalDate.now(), List.of(new TechnicalUpdateRequest.Item(1L, 3)));
        assertThrows(IllegalArgumentException.class, () -> servicio.updateTechnicalDetails(7L, exceso, "operador"));
    }

    private WorkOrder orden() {
        var orden = new WorkOrder();
        orden.setId(7L);
        var item = new OrderItem();
        item.setProductId(1L); item.setQuantity(2); item.setUnitPrice(BigDecimal.TEN);
        orden.replaceItems(List.of(item));
        return orden;
    }
}
