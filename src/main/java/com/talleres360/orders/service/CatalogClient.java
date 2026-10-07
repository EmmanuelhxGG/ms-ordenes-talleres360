package com.talleres360.orders.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;

@Component
public class CatalogClient {
    private final RestClient client;
    private final String key;

    public record Product(Long id, String sku, String name, BigDecimal price, int stock, boolean active, boolean available) {}
    public record Reserva(long revision, java.util.Map<Long, Integer> quantities) {}

    public CatalogClient(@Value("${CATALOG_URL:http://localhost:8082}") String baseUrl,
                         @Value("${INTERNAL_API_KEY:}") String key) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.key = key;
    }
    public Product product(Long id) {
        return client.get().uri("/api/products/{id}", id).header("X-Internal-Key", key)
                .retrieve().body(Product.class);
    }
    public Reserva reserva(Long ordenId) {
        return client.get().uri("/internal/stock-reservations/{id}", ordenId).header("X-Internal-Key", key)
                .retrieve().body(Reserva.class);
    }
}
