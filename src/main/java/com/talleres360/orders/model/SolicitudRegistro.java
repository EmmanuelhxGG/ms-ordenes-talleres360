package com.talleres360.orders.model;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name="solicitudes_registro") @Getter @Setter @NoArgsConstructor
public class SolicitudRegistro {
  @Id @Column(length=150) private String correo;
  @Column(nullable=false,length=120) private String nombre;
  @Column(nullable=false) private Instant creadaEn = Instant.now();
}
