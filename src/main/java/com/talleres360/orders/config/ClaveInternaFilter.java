package com.talleres360.orders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Solo el BFF y los servicios de confianza pueden proporcionar identidad interna. */
@Component
public class ClaveInternaFilter extends OncePerRequestFilter {
  private final byte[] clave;

  public ClaveInternaFilter(@Value("${INTERNAL_API_KEY:}") String clave) {
    this.clave = clave.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String ruta = request.getServletPath();
    return !ruta.startsWith("/api/") && !ruta.startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String recibida = request.getHeader("X-Internal-Key");
    if (clave.length == 0 || recibida == null
        || !MessageDigest.isEqual(clave, recibida.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"detail\":\"No se pudo autorizar la solicitud.\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
