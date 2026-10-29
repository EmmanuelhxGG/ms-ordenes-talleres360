package com.talleres360.orders.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "eventos_pendientes")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
  @Id
  @Column(name = "evento_id", length = 36)
  private String eventId;

  @Column(name = "solicitud_id", nullable = false)
  private Long orderId;

  @Column(name = "tipo", nullable = false, length = 60)
  private String type;

  @Column(name = "usuario_accion", nullable = false, length = 160)
  private String actor;

  @Column(name = "motivo", length = 500)
  private String reason;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant occurredAt;

  @Column(name = "estado", nullable = false, length = 30)
  private String status;

  @Column(name = "total", nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @Column(name = "productos_json", columnDefinition = "text")
  private String itemsJson;

  // Null identifica consumos de entregas pendientes creados por la versión anterior.
  @Column(name = "revision_existencias") private Long stockRevision;

  @Column(name = "reporteria_enviada", nullable = false)
  private boolean reportSent;

  @Column(name = "existencias_confirmadas", nullable = false)
  private boolean stockSent;

  // Nullable para conservar los eventos anteriores durante la actualización.
  @Column(name = "fallo_existencias") private Boolean stockFallido = false;

  // Nullable para incorporar filas anteriores sin perder eventos históricos.
  @Column(name = "auditoria_enviada") private Boolean auditoriaEnviada = false;
  @Column(name = "notificaciones_enviadas") private Boolean notificacionesEnviadas = false;

  @Column(name = "notificaciones_json", columnDefinition = "text")
  private String notificacionesJson;
}
