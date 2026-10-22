package com.talleres360.orders.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Evita arrancar sin publicador al conservar un valor de transporte retirado. */
@Component
public class ConfiguracionMensajeria {
  @Value("${app.messaging.transport:rabbit}")
  private String transporte;

  @PostConstruct
  void validar() {
    if (!"rabbit".equals(transporte) && !"http".equals(transporte))
      throw new IllegalStateException("MESSAGING_TRANSPORT debe ser rabbit o http");
  }
}
