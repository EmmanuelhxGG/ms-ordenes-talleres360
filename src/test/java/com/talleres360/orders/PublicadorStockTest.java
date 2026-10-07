package com.talleres360.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.talleres360.orders.model.OutboxEvent;
import com.talleres360.orders.repository.OutboxRepository;
import com.talleres360.orders.service.OutboxPublisher;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicadorStockTest {
    @Test
    void reintentaLaMismaRevisionYNoPublicaAuditoriaHastaConfirmarStock() throws Exception {
        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var peticiones = new ArrayList<String>();
        var cuerpos = new ArrayList<String>();
        var codigos = new ArrayDeque<>(List.of(503, 204, 204));
        servidor.createContext("/", llamada -> {
            peticiones.add(llamada.getRequestMethod() + " " + llamada.getRequestURI().getPath());
            cuerpos.add(new String(llamada.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("test-key", llamada.getRequestHeaders().getFirst("X-Internal-Key"));
            llamada.sendResponseHeaders(codigos.remove(), -1);
            llamada.close();
        });
        servidor.start();
        try {
            var repositorio = mock(OutboxRepository.class);
            var evento = evento(); evento.setStockRevision(3L);
            when(repositorio.pendientesStock(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(evento));
            when(repositorio.findTop100ByReportSentFalseAndStockSentTrueOrderByOccurredAtAsc()).thenReturn(List.of(evento));
            var url = "http://127.0.0.1:" + servidor.getAddress().getPort();
            var publicador = new OutboxPublisher(repositorio, new ObjectMapper().findAndRegisterModules(), url, url, "test-key");
            publicador.publish();
            assertFalse(evento.isStockSent());
            assertFalse(evento.isReportSent());
            publicador.publish();
            assertTrue(evento.isStockSent()); assertTrue(evento.isReportSent());
            assertEquals(List.of("PUT /internal/stock-reservations", "PUT /internal/stock-reservations", "POST /internal/events"), peticiones);
            assertEquals(cuerpos.get(0), cuerpos.get(1));
            assertEquals(3, new ObjectMapper().readTree(cuerpos.get(1)).get("revision").asInt());
            verify(repositorio).confirmarRevisiones(7L, 3L);
        } finally { servidor.stop(0); }
    }

    @Test
    void mantieneCompatibilidadConEntregasPendientesDeLaVersionAnterior() throws Exception {
        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var rutas = new ArrayList<String>();
        servidor.createContext("/", llamada -> {
            rutas.add(llamada.getRequestMethod() + " " + llamada.getRequestURI().getPath());
            llamada.getRequestBody().readAllBytes(); llamada.sendResponseHeaders(204, -1); llamada.close();
        });
        servidor.start();
        try {
            var repositorio = mock(OutboxRepository.class);
            var evento = evento(); evento.setType("ENTREGADA");
            when(repositorio.pendientesStock(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(evento));
            when(repositorio.findTop100ByReportSentFalseAndStockSentTrueOrderByOccurredAtAsc()).thenReturn(List.of(evento));
            var url = "http://127.0.0.1:" + servidor.getAddress().getPort();
            new OutboxPublisher(repositorio, new ObjectMapper().findAndRegisterModules(), url, url, "test-key").publish();
            assertEquals(List.of("POST /internal/stock-consumptions", "POST /internal/events"), rutas);
        } finally { servidor.stop(0); }
    }

    private OutboxEvent evento() {
        var evento = new OutboxEvent();
        evento.setEventId(UUID.randomUUID().toString()); evento.setOrderId(7L);
        evento.setType("ACEPTADA"); evento.setActor("operador"); evento.setOccurredAt(Instant.now());
        evento.setStatus("ACEPTADA"); evento.setTotal(BigDecimal.TEN);
        evento.setItemsJson("[{\"productId\":1,\"quantity\":2}]");
        return evento;
    }
}
