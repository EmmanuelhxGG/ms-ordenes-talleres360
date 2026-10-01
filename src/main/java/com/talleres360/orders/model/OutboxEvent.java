package com.talleres360.orders.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "order_outbox")
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {
    @Id @Column(length = 36) private String eventId;
    @Column(nullable = false) private Long orderId;
    @Column(nullable = false, length = 60) private String type;
    @Column(nullable = false, length = 160) private String actor;
    @Column(length = 500) private String reason;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @Column(columnDefinition = "text") private String itemsJson;
    @Column(nullable = false) private boolean reportSent;
    @Column(nullable = false) private boolean stockSent;
}
