# Microservicio de Órdenes de Talleres360

Servicio independiente de solicitudes y órdenes de trabajo. Java 21, Spring Boot 3.5.6, JPA, validación y Lombok. Puerto **8081**; PostgreSQL **orders_db** en Docker, H2 en memoria para desarrollo. Rama **`backend-emmanuel`**. [Repositorio](https://github.com/EmmanuelhxGG/ms-ordenes-talleres360).

Documentación del código al 6 de octubre de 2026. Este repositorio sustituye la parte de órdenes del backend anteriormente agrupado.

## Responsabilidad y conexiones

Con `MESSAGING_TRANSPORT=broker`, los eventos de negocio del outbox se publican en
Kafka `orders.events`, mientras las tareas de correo, ticket de taller y
comprobante PDF se publican en RabbitMQ. Se confirman después del ACK del broker
y se reintentan si falla la conexión. Catálogo conserva la coordinación HTTP de
stock; el BFF sigue ofreciendo las consultas HTTP de los cinco microservicios.
`MESSAGING_TRANSPORT=http` conserva el transporte directo como alternativa
explícita; no se activan los dos transportes simultáneamente.

Gestiona solicitudes, datos del vehículo/propietario, taller, estados, informe técnico y total. Consulta productos/precios en Catálogo y persiste eventos de negocio en su outbox para enviarlos a Catálogo/Reportería.

```text
BFF → Órdenes :8081 → PostgreSQL orders_db
                ├── Catálogo :8082, por HTTP y clave interna
                └── Reportería :8083, desde outbox
```

BFF valida el JWT y deriva correo/rol. Órdenes recibe esas cabeceras desde BFF y debe quedar accesible solo desde la red autorizada; no valida el JWT por sí mismo. El navegador utiliza BFF/Gateway, nunca este puerto directamente.

## Estructura

Las rutas Java parten de `src/main/java/com/talleres360/orders/`.

| Carpeta/archivo | Contenido |
| --- | --- |
| `controller/` | `WorkOrderController` y `AppointmentController`: contratos HTTP. |
| `dto/` | Entradas/salidas, validación y `EstadoStockResponse`. |
| `service/WorkOrderService.java` | CRUD operativo, estados, informe y coordinación de repuestos. |
| `service/AppointmentService.java` | Crear/listar solicitudes de cliente y validar región/taller. |
| `service/CatalogClient.java` | Consulta de productos y asignaciones confirmadas. |
| `service/OrderEventService.java` | Eventos y revisiones guardados junto con la orden. |
| `service/OutboxPublisher.java` | Envíos HTTP, reintentos y confirmación de revisiones. |
| `model/` | `WorkOrder`, `OrderItem`, `OrderStatus`, `ServiceType`, `OutboxEvent`. |
| `repository/` | Persistencia, filtros, bloqueo de orden y selección de eventos. |
| `validation/`, `exception/`, `config/` | RUT, errores de negocio y CORS. |
| `src/main/resources/application.yml` | Puerto, perfiles H2/PostgreSQL y variables. |
| `compose.yml`, `Dockerfile`, `.env.example` | Despliegue independiente. |

## Modelo y flujo

`WorkOrder` guarda cliente/correo/RUT/teléfono, patente/modelo, taller, descripción, informe, mano de obra, ítems, total, estado y fechas. Las solicitudes de cliente también guardan año, región, tipo de servicio y día de atención.

| Dato | Significado |
| --- | --- |
| `id` | ID automático de la base, al crear. Todos los actores usan la misma tabla. |
| `workshopId` | ID del taller, repetible entre órdenes. No es el número de pedido. |
| `appointmentDate` | Día solicitado para atención. |
| `estimatedDeliveryDate` | Día estimado de entrega del informe. |
| `createdAt`, `acceptedAt`, `deliveredAt` | Marcas de creación, aceptación y entrega. |
| `stockRevision` | Revisión de la asignación requerida por la orden. |

Aceptar no genera otra orden ni cambia su ID. La secuencia puede tener saltos; no reiniciarla para rellenarlos. Los 20 talleres se validan por región: Biobío 1–7, Maule 8–14 y Araucanía 15–20.

Estados: `RECIBIDA → ACEPTADA → EN_REPARACION → LISTA_PARA_ENTREGA → ENTREGADA`. Se permite `CANCELADA` antes de entregar. Entregada y cancelada son terminales. Editar datos generales exige recibida. El informe admite aceptada/en reparación/lista. Marcar lista exige diagnóstico y trabajo realizado. Admin justifica cambios de estado con al menos diez caracteres.

Total = suma de cantidad × precio de catálogo + mano de obra. No se confía en un precio aportado por el navegador.

## API de órdenes

Los roles de esta tabla se aplican al acceder **a través del BFF**:

| Método/ruta | Función | Rol |
| --- | --- | --- |
| `POST /api/orders` | Crear recibida. | Operador/Admin |
| `GET /api/orders?status=&from=&to=` | Listar; filtros opcionales por estado y creación. | Operador/Admin |
| `GET /api/orders/{id}` | Detalle. | Operador/Admin |
| `PUT /api/orders/{id}` | Editar recibida. | Operador/Admin |
| `PUT /api/orders/{id}/status` | Cambiar estado. | Operador/Admin |
| `PUT /api/orders/{id}/technical` | Diagnóstico, trabajo, costos y repuestos. | Operador/Admin |
| `GET /api/orders/{id}/stock` | Revisión requerida/confirmada y cantidades asignadas. | Operador/Admin |
| `DELETE /api/orders/{id}` | Eliminar y registrar evento. | Admin |

Los filtros `from/to` de órdenes usan fecha-hora ISO local, por ejemplo `2026-10-06T00:00:00`; el límite superior del filtro de órdenes es inclusivo.

Ejemplo de creación:

```json
{
  "workshopId": 1,
  "customerName": "Juan Perez",
  "customerEmail": "cliente@ejemplo.cl",
  "customerRut": "12.345.678-5",
  "customerPhone": "12345678",
  "vehiclePlate": "HD-JK-17",
  "vehicleModel": "Toyota Corolla",
  "description": "Revisar ruido al encender el motor",
  "items": []
}
```

Cambio de estado: `{"status":"ACEPTADA"}`. Para Admin incluir `reason` con la justificación. El BFF deriva `X-Actor-Email` y `X-Actor-Role` del token.

El informe recibe `diagnosis`, `workPerformed`, `laborCost`, `estimatedDeliveryDate` y `items:[{"productId":1,"quantity":2}]`. La fecha estimada es hoy o posterior; diagnóstico/trabajo tienen 10–2000 caracteres. La respuesta stock contiene `revision`, `confirmedRevision`, `pending` y `quantities`: `pending=true` indica que la revisión aún está en proceso de confirmación.

## API de solicitudes del cliente

| Método/ruta | Función |
| --- | --- |
| `POST /api/appointments` | Crear solicitud recibida. |
| `GET /api/appointments` | Solicitudes del correo autenticado. |
| `GET /api/appointments/availability?workshopId=1&from=YYYY-MM-DD&to=YYYY-MM-DD` | Valida taller/rango y responde `{"occupiedDates":[]}` en esta versión. |

El BFF establece `X-Customer-Email`. La creación recibe `workshopId`, `regionId`, `firstName`, `lastName`, `rut`, `phone`, `vehiclePlate`, `vehicleModel`, `vehicleYear`, `serviceType`, `reason` y `appointmentDate`. Valores de servicio: `MAINTENANCE` y `DIAGNOSTICS`. La fecha es futura y dentro de 90 días. Se admiten múltiples solicitudes del mismo cliente.

La respuesta de disponibilidad descrita no confirma un cupo reservado. Las solicitudes guardan un día, no una hora de atención.

## Validaciones

- Patente: seis caracteres alfanuméricos separados en pares, por ejemplo `HD-JK-17`.
- Modelo: letras, números y espacios.
- RUT: cuerpo numérico y dígito verificador, incluida K final, comprobado con Módulo 11. Valida el dígito, no consulta existencia o identidad en un registro oficial.
- Teléfono: ocho dígitos; la interfaz muestra el prefijo chileno +56 9.
- Nombre: letras, espacios, apóstrofes y guiones. Correo: formato válido.
- Año de solicitud: desde 1900 hasta el año actual + 1; motivo de 10–500 caracteres.
- Región/taller: asociación válida de la lista de 20.
- Ítems: hasta 200 líneas, IDs positivos y cantidades entre 1 y 1.000.000; líneas del mismo producto se agrupan para comprobar existencias.

Los DTO son el contrato exacto. Errores de negocio se devuelven como ProblemDetail: 400 validación, 404 orden inexistente, 409 transición/confirmación incompatible y 503 fallo de consulta de Catálogo con mensaje personalizado.

## Stock y outbox

1. Crear/editar recibida comprueba stock sin descontar.
2. Aceptar registra una asignación completa versionada en la misma transacción de la orden.
3. El publicador pide a Catálogo aplicar esa asignación; Catálogo cambia solo la diferencia.
4. Editar el informe genera otra revisión. Se puede conservar stock ya asignado aunque el libre sea cero; aumentar exige existencias y producto activo.
5. Cancelar/eliminar una orden activa solicita una lista vacía que libera lo confirmado.
6. Entregar comprueba la última revisión confirmada y no descuenta de nuevo. El evento de entrega alimenta ventas.
7. Eliminar una entrega histórica no devuelve repuestos ni elimina auditoría.

Se bloquean modificaciones concurrentes de una misma orden. El outbox envía aproximadamente cada segundo (`OUTBOX_POLL_MS`, por defecto 1000), con lotes de hasta 100 para stock y reportes por separado. Procesa la revisión más reciente de cada orden; una cancelación no queda detrás de revisiones antiguas fallidas. Los reintentos son idempotentes por revisión en Catálogo y por evento en Reportería.

La integración es asíncrona, no una transacción distribuida inmediata. Si la asignación no se confirma, la orden puede estar aceptada pero no se entrega. El informe permite reducir/quitar repuestos, o puede cancelarse la orden. `app.outbox.enabled=false` desactiva el publicador para pruebas específicas.

### Auditoría y notificaciones

Cada modificación registra su evento en el outbox dentro de la misma transacción de la orden. Un publicador independiente envía todos los eventos a `AUDIT_URL/internal/events`. Otro envía comandos a `NOTIFICATIONS_URL/internal/commands` cuando se crea, acepta, prepara para entrega, entrega o cancela una orden.

Cada evento relevante genera un correo para el cliente y un ticket para el taller. Los mensajes se guardan como instantáneas: editar o eliminar una orden no altera el contenido de un aviso ya preparado. Los identificadores se conservan en cada reintento y los receptores deduplican. La confirmación indica recepción persistida, no entrega de un correo.

Stock, reportes, auditoría y notificaciones tienen confirmaciones separadas, actualizadas por columna para no sobrescribirse entre publicadores. Auditoría y notificaciones no esperan la confirmación de stock; las ventas conservan la regla existente. Las tareas tienen cuatro hilos de planificación y lotes acotados. Los errores de comunicación mantienen el evento pendiente para volver a intentar. Las filas históricas pueden reenviarse a Auditoría; no se inventan avisos antiguos si no existe una instantánea de notificación.

La conexión actual es HTTP interno con outbox persistente. Los consumidores RabbitMQ/Kafka de los servicios nuevos son opcionales; este publicador no configura brokers ni reemplaza la mensajería exigida por el caso. La alternativa es publicar los mismos contratos mediante esos brokers; evita acoplamiento HTTP, pero requiere su infraestructura y operación.

## Configuración y ejecución

Crea `.env` junto a `compose.yml`:

```dotenv
DB_USERNAME=talleres360
DB_PASSWORD=<CONTRASENA_DE_ESTA_BASE>
INTERNAL_API_KEY=<CLAVE_COMPARTIDA_CON_BFF_Y_MICROS>
CATALOG_URL=http://<IP_PRIVADA_EC2_CATALOGO>:8082
REPORT_URL=http://<IP_PRIVADA_EC2_REPORTES>:8083
NOTIFICATIONS_URL=http://<IP_PRIVADA_EC2_NOTIFICACIONES>:8084
AUDIT_URL=http://<IP_PRIVADA_EC2_AUDITORIA>:8085
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Las URL no incluyen `/api` ni `/dev`. Este micro no necesita IDs de Azure. Compose establece perfil postgres, puerto y conexión `jdbc:postgresql://postgres:5432/orders_db`; cada base tiene su volumen y credenciales.

Con Git, Docker Engine, Buildx y Compose instalados:

```bash
docker buildx version
docker compose version
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 ordenes
```

Para desarrollo con JDK 17, exporta las variables de Catálogo/Reportería/clave y ejecuta `./mvnw spring-boot:run`; PowerShell: `./mvnw.cmd spring-boot:run`. El perfil local usa H2 y no conserva datos al detener el proceso. Java directo no carga `.env` automáticamente.

## EC2, actualización y conservación

[Guía de esta EC2](DESPLIEGUE_EC2.md). Permitir 8081 solo desde el grupo BFF; no publicar PostgreSQL. En BFF usar `ORDERS_URL=http://<IP_PRIVADA_ORDENES>:8081`. Si cambian Catálogo/Reportería, actualizar sus URL aquí y en BFF y recrear los contenedores afectados.

Con la rama verificada y código publicado:

```bash
git pull --ff-only origin backend-emmanuel
docker compose up -d --build
```

Para esta versión actualizar Catálogo → Órdenes → frontend. JPA añade las columnas de revisión con `ddl-auto=update`. Las entregas antiguas sin revisión conservan el consumo idempotente anterior. Una orden activa anterior se regulariza al guardar informe o avanzar a reparación/lista; si ya estaba lista, guardar de nuevo su informe antes de entregar. No recalcular entregas históricas, reiniciar secuencias ni reutilizar IDs con reservas existentes.

El volumen conserva datos al recrear. `docker compose down -v` lo elimina. `restart: unless-stopped` recupera el servicio al reiniciar Docker/EC2, salvo detención manual; no actualiza Git. Respaldar Órdenes y Catálogo de forma coherente.

## Comprobar empaquetado

```bash
./mvnw -DskipTests package
```

PowerShell: `./mvnw.cmd '-DskipTests' package`. La revisión local del 8 de octubre verificó estados, stock, outbox y solicitudes con H2; no certifica una ejecución EC2. Los archivos de pruebas no forman parte de esta versión. No publicar `.env`, PEM, tokens ni clave interna.
