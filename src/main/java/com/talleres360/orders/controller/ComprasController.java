package com.talleres360.orders.controller;

import com.talleres360.orders.dto.*;
import com.talleres360.orders.service.ComprasService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class ComprasController {
  private final ComprasService service;
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse crear(@RequestHeader("X-Customer-Email") String correo,
      @Valid @RequestBody SolicitudCompra entrada) { return service.crear(correo, entrada); }
  @GetMapping
  public List<OrderResponse> propias(@RequestHeader("X-Customer-Email") String correo) {
    return service.propias(correo);
  }
}
