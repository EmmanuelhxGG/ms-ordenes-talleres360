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
    String correo = correoRegistro(entrada.correo());
    String nombre = java.text.Normalizer.normalize(entrada.nombre(), java.text.Normalizer.Form.NFC).strip();
    if (!nombre.matches("^[\\p{L} ]{3,120}$")) throw new IllegalArgumentException("Nombre inválido");
    // No se revela si existe el correo ni se modifican solicitudes ajenas repetidas.
    if(repository.existsById(correo)) return;
    var registro=new SolicitudRegistro(); registro.setCorreo(correo);registro.setNombre(nombre);
    repository.saveAndFlush(registro);
  }
  public static String correoRegistro(String valor) {
    if (valor == null) throw new IllegalArgumentException("Se requiere un correo @duocuc.cl");
    String correo = valor.strip().toLowerCase(java.util.Locale.ROOT);
    if (correo.length() > 150 || !correo.matches("(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@duocuc[.]cl$"))
      throw new IllegalArgumentException("Se requiere un correo @duocuc.cl válido");
    String local = correo.substring(0, correo.indexOf('@'));
    if (local.length() > 64 || local.startsWith(".") || local.endsWith(".") || local.contains(".."))
      throw new IllegalArgumentException("Se requiere un correo @duocuc.cl válido");
    return correo;
  }
  @Transactional(readOnly=true) public List<SolicitudRegistro> listar(){return repository.findTop200ByOrderByCreadaEnDesc();}
}
