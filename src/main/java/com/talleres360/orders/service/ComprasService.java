package com.talleres360.orders.service;

import com.talleres360.orders.dto.*;
import com.talleres360.orders.model.*;
import com.talleres360.orders.repository.WorkOrderRepository;
import com.talleres360.orders.validation.RutValidator;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComprasService {
  private final WorkOrderRepository repository;
  private final CatalogClient catalogo;
  private final OrderEventService eventos;

  public static String correoInstitucional(String correo) {
    if (correo == null || !correo.trim().matches("(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@duocuc\\.cl$"))
      throw new IllegalArgumentException("Debes utilizar tu correo institucional @duocuc.cl");
    return correo.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public OrderResponse crear(String correo, SolicitudCompra entrada) {
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
      if (!cantidades.equals(previas) || !orden.getWorkshopId().equals(entrada.tallerId())
          || !orden.getCustomerName().equals(entrada.nombre().trim())
          || !orden.getCustomerRut().equalsIgnoreCase(entrada.rut())
          || !orden.getCustomerPhone().equals(entrada.telefono()))
        throw new IllegalArgumentException("La clave ya corresponde a otra solicitud");
      return OrderResponse.from(orden);
    }
    if (!RutValidator.isValid(entrada.rut())) throw new IllegalArgumentException("El RUT no es válido");
    var orden = new WorkOrder();
    orden.setSolicitudId(id); orden.setCompraRepuestos(true);
    orden.setCustomerEmail(correo); orden.setCustomerName(entrada.nombre().trim());
    orden.setCustomerRut(entrada.rut().toUpperCase(Locale.ROOT));
    orden.setCustomerPhone(entrada.telefono()); orden.setWorkshopId(entrada.tallerId());
    orden.setRegionId(entrada.tallerId() <= 7 ? "biobio" : entrada.tallerId() <= 14 ? "maule" : "araucania");
    orden.setDescription("Solicitud de compra de repuestos para retiro en taller");
    orden.setLaborCost(BigDecimal.ZERO); orden.setStatus(OrderStatus.RECIBIDA);
    var cantidades = new TreeMap<Long, Integer>();
    entrada.productos().forEach(i -> cantidades.merge(i.productoId(), i.cantidad(), Math::addExact));
    var items = cantidades.entrySet().stream().map(e -> {
      var p = catalogo.product(e.getKey());
      if (p == null || !p.active() || e.getValue() > p.stock() || e.getValue() > 1000)
        throw new IllegalArgumentException("Un producto ya no está disponible en la cantidad solicitada");
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
