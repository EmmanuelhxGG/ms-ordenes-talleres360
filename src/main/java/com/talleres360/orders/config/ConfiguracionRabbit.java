package com.talleres360.orders.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Convertidor del productor; Notify interpreta el JSON con su propio DTO. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionRabbit {
  @Bean
  public Jackson2JsonMessageConverter convertidorJsonRabbit(ObjectMapper json) {
    return new Jackson2JsonMessageConverter(json);
  }
}
