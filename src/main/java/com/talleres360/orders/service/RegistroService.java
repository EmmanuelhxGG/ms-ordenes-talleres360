package com.talleres360.orders.service;
import com.talleres360.orders.model.SolicitudRegistro;
import com.talleres360.orders.repository.SolicitudRegistroRepository;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RegistroService {
  private final SolicitudRegistroRepository repository;
  public record Entrada(@NotBlank @Size(min=3,max=120) @Pattern(regexp="^[\\p{L} ]+$") String nombre,
      @NotBlank @Email @Size(max=150) String correo) {}
  @Transactional public void solicitar(Entrada entrada) {
    String correo=ComprasService.correoInstitucional(entrada.correo());
    // No se revela si existe el correo ni se modifican solicitudes ajenas repetidas.
    if(repository.existsById(correo)) return;
    var registro=new SolicitudRegistro(); registro.setCorreo(correo);registro.setNombre(entrada.nombre().trim());
    repository.saveAndFlush(registro);
  }
  @Transactional(readOnly=true) public List<SolicitudRegistro> listar(){return repository.findTop200ByOrderByCreadaEnDesc();}
}
