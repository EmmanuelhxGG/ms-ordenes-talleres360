package com.talleres360.orders.controller;
import com.talleres360.orders.service.RegistroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController @RequestMapping("/api/registros") @RequiredArgsConstructor
public class RegistroController {
  private final RegistroService service;
  @Value("${INTERNAL_API_KEY:}") private String clave;
  private void comprobar(String candidata){if(clave.isBlank()||candidata==null||!MessageDigest.isEqual(
      clave.getBytes(StandardCharsets.UTF_8),candidata.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
  @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
  public void solicitar(@RequestHeader(value="X-Internal-Key",required=false) String key,@Valid @RequestBody RegistroService.Entrada e){comprobar(key);service.solicitar(e);}
  @GetMapping public Object listar(@RequestHeader(value="X-Internal-Key",required=false) String key){comprobar(key);return service.listar();}
}
