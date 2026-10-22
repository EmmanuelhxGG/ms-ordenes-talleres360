package com.talleres360.orders.repository;
import com.talleres360.orders.model.SolicitudRegistro;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SolicitudRegistroRepository extends JpaRepository<SolicitudRegistro,String> {
  List<SolicitudRegistro> findTop200ByOrderByCreadaEnDesc();
}
