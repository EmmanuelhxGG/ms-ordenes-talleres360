package com.talleres360.orders.service;

import com.talleres360.orders.dto.*;
import com.talleres360.orders.model.*;
import com.talleres360.orders.repository.WorkOrderRepository;
import com.talleres360.orders.exception.DisponibilidadProductoException;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComprasService {
  private static final java.util.regex.Pattern CORREO_PERMITIDO = java.util.regex.Pattern.compile(
      "(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(duocuc[.]cl|[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.]onmicrosoft[.]com)$");
  private final WorkOrderRepository repository;
  private final CatalogClient catalogo;
  private final OrderEventService eventos;

  public static String correoInstitucional(String correo) {
    if (correo == null)
      throw new IllegalArgumentException("Debes utilizar una cuenta @duocuc.cl o de un dominio onmicrosoft.com");
    String normalizado = correo.strip().toLowerCase(Locale.ROOT);
    if (normalizado.length() > 150 || normalizado.contains("#ext#")
        || !CORREO_PERMITIDO.matcher(normalizado).matches())
      throw new IllegalArgumentException("Debes utilizar una cuenta @duocuc.cl o de un dominio onmicrosoft.com");
    return normalizado;
  }

  @Transactional
  public OrderResponse crear(String correo, String nombreAutenticado, SolicitudCompra entrada) {
    correo = correoInstitucional(correo);
    String id = UUID.fromString(entrada.solicitudId()).toString();
    var anterior = repository.findBySolicitudId(id);
    if (anterior.isPresent()) {
      var orden = anterior.get();
      if (!orden.getCustomerEmail().equalsIgnoreCase(correo))
        throw new IllegalArgumentException("No se pudo identificar la solicitud");
      // La misma clave no puede reutilizarse para otro carrito.
      var cantidades = new TreeMap<Long, Integer>();
      entrada.productos().forEach(i -> cantidades.merge(i.productoId(), i.cantidad(), Math::addExact));
      var previas = new TreeMap<Long, Integer>();
      orden.getItems().forEach(i -> previas.merge(i.getProductId(), i.getQuantity(), Math::addExact));
      if (!cantidades.equals(previas))
        throw new IllegalArgumentException("La clave ya corresponde a otra solicitud");
      return OrderResponse.from(orden);
    }
    var orden = new WorkOrder();
    orden.setSolicitudId(id); orden.setCompraRepuestos(true);
    orden.setCustomerEmail(correo);
    String nombre = nombreAutenticado == null ? correo : nombreAutenticado.strip();
    if (nombre.isBlank()) nombre = correo;
    if (nombre.length() > 120 || nombre.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("El nombre de la cuenta autenticada no es válido");
    orden.setCustomerName(nombre);
    orden.setWorkshopId(TallerActual.ID);
    orden.setDescription("Solicitud de compra de repuestos para retiro en taller");
    orden.setLaborCost(BigDecimal.ZERO); orden.setStatus(OrderStatus.RECIBIDA);
    var cantidades = new TreeMap<Long, Integer>();
    entrada.productos().forEach(i -> cantidades.merge(i.productoId(), i.cantidad(), Math::addExact));
    var items = cantidades.entrySet().stream().map(e -> {
      var p = catalogo.product(e.getKey());
      if (p == null || !p.active() || (!p.available() && p.stock() > 0))
        throw new DisponibilidadProductoException("PRODUCTO_NO_DISPONIBLE", e.getKey(),
            "Este producto está deshabilitado o ya no está disponible. Retíralo del carrito.");
      if (e.getValue() > p.stock())
        throw new DisponibilidadProductoException("STOCK_INSUFICIENTE", e.getKey(),
            "No hay stock suficiente para la cantidad solicitada. Reduce la cantidad e inténtalo nuevamente.");
      if (e.getValue() > 1000)
        throw new IllegalArgumentException("La cantidad máxima por producto es 1000");
      var item = new OrderItem(); item.setProductId(p.id()); item.setDescription(p.name());
      item.setQuantity(e.getValue()); item.setUnitPrice(p.price()); return item;
    }).toList();
    orden.replaceItems(items); repository.saveAndFlush(orden);
    eventos.record(orden, "CREADA", correo);
    return OrderResponse.from(orden);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> propias(String correo) {
    return repository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(correoInstitucional(correo))
        .stream().filter(WorkOrder::isCompraRepuestos).map(OrderResponse::from).toList();
  }
}
