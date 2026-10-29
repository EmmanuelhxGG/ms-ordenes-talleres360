# Microservicio de Órdenes de Talleres360

## Comprobante de entrega

Confirmar `ENTREGADA` guarda en el mismo outbox un comando `COMPROBANTE` con
identificador estable y el detalle de productos y total. Se publica por RabbitMQ
en `invoice.gen` y Notificaciones lo conserva como texto descargable `.txt`.
No añade un cuarto correo ni representa un pago o una boleta tributaria.

## Conflictos al aceptar compras

Antes de solicitar la reserva se valida la disponibilidad. Si falta stock,
responde HTTP 409 (`ACEPTACION_SIN_STOCK`, `productoId`, `nombreProducto`) y
conserva la solicitud recibida. Si otra compra consume el stock entre esa
validación y la reserva, el outbox registra el intento como `stockFallido`,
desactiva `aceptacionPendiente` y guarda `errorAceptacion` para el operador.
Este conflicto no confirma la aceptación ni genera su correo. El operador
puede rechazar con motivo o reintentar explícitamente cuando haya disponibilidad.
Los errores temporales de conexión o del servicio conservan el reintento.
La migración V4 agrega campos opcionales sin eliminar órdenes ni eventos.

## Actualización: errores de disponibilidad (28-10-2026)

Al crear una compra se agrupan las líneas por producto y se comprueba su
disponibilidad real en Catálogo. Si falta stock devuelve HTTP 409 con
`codigo=STOCK_INSUFICIENTE` y `productoId`; si está deshabilitado/no disponible,
`codigo=PRODUCTO_NO_DISPONIBLE`. El mensaje no expone la cantidad exacta de stock.
No se guarda orden ni outbox para esa solicitud rechazada. El descuento sigue
ocurriendo al aceptar con asignación confirmada, no al enviar el carrito.


Servicio independiente de solicitudes y órdenes de trabajo. Java 21, Spring Boot 3.5.6, JPA, validación y Lombok. Puerto **8081**; PostgreSQL **orders_db** en Docker, H2 en memoria para desarrollo. Rama **`backend-emmanuel`**. [Repositorio](https://github.com/EmmanuelhxGG/ms-ordenes-talleres360).

Documentación actualizada el **22 de octubre de 2026**. Este repositorio se despliega de forma independiente. Los ejemplos usan marcadores y datos ficticios, sin información privada de Entra o AWS.

## Solicitudes de compra y acceso

La aplicación representa un único taller: Talleres360, ID interno 1. No hay catálogo
de veinte sucursales ni selección de regiones. Los datos históricos se conservan
sin reescribir sus IDs o regiones originales.

`POST /api/compras` crea una solicitud idempotente con UUID y hasta 50 productos
distintos. No solicita RUT, teléfono, nombre manual ni taller al cliente. El BFF
deriva nombre/correo del JWT y el servidor asigna el único taller. Precios y
disponibilidad se consultan en Catálogo; no se confía en precios del navegador.
`GET /api/compras` devuelve únicamente las compras del correo autenticado.
Las compras admiten `@duocuc.cl` y cuentas `@tu-tenant.onmicrosoft.com` ya habilitadas.
Las solicitudes nuevas de registro admiten únicamente `@duocuc.cl`, sin crear cuentas
ni conceder roles automáticamente. El BFF valida el JWT del tenant configurado y
exige los permisos correspondientes. Los UPN invitados `#EXT#` no se usan como correo;
se necesita el correo real en los claims autenticados.
La compra usa la misma secuencia automática de órdenes y no inventa una patente.

Crear no descuenta stock. Al aceptar se guarda una intención durable, aún RECIBIDA,
con `aceptacionPendiente=true`. Solo después de confirmar la revisión de stock pasa
a ACEPTADA (en espera). Si Catálogo cae, el outbox reintenta sin aceptar falsamente
ni descontar dos veces. Sigue EN_PREPARACION, LISTA_PARA_ENTREGA y ENTREGADA, sin informe mecánico.
Solo Operador cambia estados y puede cancelar con motivo antes de la entrega.
Admin consulta sin modificar y no existe creación manual ni eliminación pública
de órdenes. Cliente, Operador y Admin pueden solicitar compras desde su propio
carrito. Los servicios históricos mantienen su flujo técnico.

Solo se generan correos nuevos al aceptar, marcar lista y rechazar. El comprobante
es texto del correo con productos, cantidades y monto, sin PDF ni adjuntos.
Las ventas se reconocen al entregar.

El JSON de correo usa `pedidoId`, `email`, `asunto` y `mensaje`, junto a metadatos
`commandId`, `tipo` y `occurredAt` para conservar trazabilidad e idempotencia.
Los mensajes pendientes con nombres anteriores siguen siendo compatibles.
El operador origina el aviso; la entrega técnica depende del proveedor de correo
configurado en Notificaciones y no del inicio de sesión de Entra ID.

`POST /api/registros` guarda una petición institucional autenticada sin contraseña y
`GET /api/registros` permite su consulta administrativa vía BFF. No crea usuarios
ni asigna roles en Entra: esa aprobación es manual. Las migraciones PostgreSQL
conservan las órdenes, existencias e identificadores anteriores.

## Responsabilidad y conexiones

Las rutas `/api/**` e `/internal/**` exigen `X-Internal-Key` mediante un filtro
global. Sin esa credencial no se aceptan los encabezados de identidad del cliente
ni de actor. El BFF obtiene la identidad del JWT y agrega la clave interna en
compras, registros, órdenes e historial. La clave nunca se distribuye al frontend.

Con `MESSAGING_TRANSPORT=rabbit`, las tareas de correo y ticket de taller
se publican en RabbitMQ con confirmación y detección de mensajes
sin ruta. Reportería y Auditoría reciben eventos por HTTP interno desde el outbox,
con confirmaciones independientes y reintentos. Catálogo conserva HTTP para stock.
Kafka y su dependencia fueron retirados del servicio.
`MESSAGING_TRANSPORT=http` conserva el transporte directo como alternativa
explícita; no se activan los dos transportes simultáneamente.

Gestiona compras, agendamientos y órdenes administrativas del único taller. Consulta productos/precios en Catálogo y persiste eventos en el outbox para coordinar stock, ventas, auditoría y notificaciones. Los datos de vehículo/propietario corresponden al flujo mecánico, no al carrito.

```text
BFF → Órdenes :8081 → PostgreSQL orders_db
                ├── Catálogo :8082, por HTTP y clave interna
                ├── Reportería :8083, desde outbox HTTP
                ├── Auditoría :8085, desde outbox HTTP
                └── RabbitMQ → Notificaciones :8084
```

BFF valida el JWT y deriva correo/rol. Órdenes recibe esas cabeceras desde BFF y debe quedar accesible solo desde la red autorizada; no valida el JWT por sí mismo. El navegador utiliza BFF/Gateway, nunca este puerto directamente.

## Estructura

Las rutas Java parten de `src/main/java/com/talleres360/orders/`.

| Carpeta/archivo | Contenido |
| --- | --- |
| `controller/` | Contratos de órdenes, agendamientos, compras y solicitudes de registro. |
| `dto/` | Entradas/salidas, validación y `EstadoStockResponse`. |
| `service/WorkOrderService.java` | CRUD operativo, estados, informe y coordinación de repuestos. |
| `service/AppointmentService.java` | Crear/listar agendamientos y validar datos del vehículo, propietario y fecha. |
| `service/ComprasService.java`, `service/ConfirmadorCompras.java` | Carrito idempotente y aceptación después de confirmar stock. |
| `service/TallerActual.java` | Identidad del único taller asignada por el servidor. |
| `service/CatalogClient.java` | Consulta de productos y asignaciones confirmadas. |
| `service/OrderEventService.java` | Eventos y revisiones guardados junto con la orden. |
| `service/OutboxPublisher.java` | Envíos HTTP, reintentos y confirmación de revisiones. |
| `service/PublicadorIntegraciones.java`, `service/PublicadorMensajeria.java` | Auditoría HTTP y comandos RabbitMQ; HTTP como transporte alternativo explícito de notificaciones. |
| `model/` | `WorkOrder`, `OrderItem`, `OrderStatus`, `ServiceType`, `OutboxEvent`. |
| `repository/` | Persistencia, filtros, bloqueo de orden y selección de eventos. |
| `validation/`, `exception/`, `config/` | RUT, errores de negocio y CORS. |
| `src/main/resources/application.yml` | Puerto, perfiles H2/PostgreSQL y variables. |
| `compose.yml`, `Dockerfile`, `.env.example` | Despliegue independiente. |

## Modelo y flujo

`WorkOrder` guarda identidad del cliente, taller, descripción, ítems, total, estado y fechas. El agendamiento mecánico añade RUT, teléfono, patente, modelo, año, tipo de servicio y día de atención; su informe registra diagnóstico, trabajo y mano de obra. La compra de repuestos no pide datos del propietario ni del vehículo. La región solo se conserva en registros históricos.

| Dato | Significado |
| --- | --- |
| `id` | ID automático de la base, al crear. Todos los actores usan la misma tabla. |
| `workshopId` | ID interno 1 en nuevas solicitudes. Los IDs antiguos se mantienen únicamente en el historial; no son el número de pedido. |
| `appointmentDate` | Día solicitado para atención. |
| `estimatedDeliveryDate` | Día estimado de entrega del informe. |
| `createdAt`, `acceptedAt`, `deliveredAt` | Marcas de creación, aceptación y entrega. |
| `stockRevision` | Revisión de la asignación requerida por la orden. |

Aceptar no genera otra orden ni cambia su ID. La secuencia puede tener saltos; no reiniciarla para rellenarlos. El servidor fija el taller 1 para toda nueva compra, orden administrativa o agendamiento; no acepta una sede elegida por el navegador.

Estados de atención mecánica: `RECIBIDA → ACEPTADA → EN_REPARACION → LISTA_PARA_ENTREGA → ENTREGADA`. Se permite `CANCELADA` antes de entregar. Entregada y cancelada son terminales. Editar datos generales exige recibida. El informe admite aceptada/en reparación/lista. En atención mecánica, marcar lista exige diagnóstico y trabajo realizado. Las compras usan `EN_PREPARACION` en lugar de reparación. Solo Operador modifica estados; Admin consulta.

Total = suma de cantidad × precio de catálogo + mano de obra. No se confía en un precio aportado por el navegador.

## API de órdenes

Los roles de esta tabla se aplican al acceder **a través del BFF**:

| Método/ruta | Función | Rol |
| --- | --- | --- |
| `POST /api/orders` | Creación manual deshabilitada; usar carrito. | Denegado |
| `GET /api/orders?status=&from=&to=` | Listar; filtros opcionales por estado y creación. | Operador/Admin |
| `GET /api/orders/{id}` | Detalle. | Operador/Admin |
| `PUT /api/orders/{id}` | Editar atención recibida. | Operador |
| `PUT /api/orders/{id}/status` | Cambiar estado. | Operador |
| `PUT /api/orders/{id}/technical` | Diagnóstico, trabajo, costos y repuestos. | Operador |
| `GET /api/orders/{id}/stock` | Revisión requerida/confirmada y cantidades asignadas. | Operador/Admin |
| `DELETE /api/orders/{id}` | Eliminación deshabilitada; conservar historial. | Denegado |

Los filtros `from/to` de órdenes usan fecha-hora ISO local, por ejemplo `2026-10-06T00:00:00`; el límite superior del filtro de órdenes es inclusivo.

Ejemplo de creación:

```json
{
  "customerName": "Juan Perez",
  "customerEmail": "cliente@example.invalid",
  "customerRut": "12.345.678-5",
  "customerPhone": "12345678",
  "vehiclePlate": "HD-JK-17",
  "vehicleModel": "Toyota Corolla",
  "description": "Revisar ruido al encender el motor",
  "items": []
}
```

Cambio de estado por Operador: `{"status":"ACEPTADA"}`. Para cancelar incluir `reason` con al menos diez caracteres. El BFF deriva `X-Actor-Email` y `X-Actor-Role` del token.

El informe recibe `diagnosis`, `workPerformed`, `laborCost`, `estimatedDeliveryDate` y `items:[{"productId":1,"quantity":2}]`. La fecha estimada es hoy o posterior; diagnóstico/trabajo tienen 10–2000 caracteres. La respuesta stock contiene `revision`, `confirmedRevision`, `pending` y `quantities`: `pending=true` indica que la revisión aún está en proceso de confirmación.

## API de solicitudes del cliente

| Método/ruta | Función |
| --- | --- |
| `POST /api/appointments` | Crear solicitud recibida. |
| `GET /api/appointments` | Solicitudes del correo autenticado. |
| `GET /api/appointments/availability?from=YYYY-MM-DD&to=YYYY-MM-DD` | Valida el rango del único taller y responde `{"occupiedDates":[]}` en esta versión. |

El BFF establece `X-Customer-Email`. El agendamiento mecánico recibe `firstName`, `lastName`, `rut`, `phone`, `vehiclePlate`, `vehicleModel`, `vehicleYear`, `serviceType`, `reason` y `appointmentDate`, sin región ni taller. Valores de servicio: `MAINTENANCE` y `DIAGNOSTICS`. La fecha es futura y dentro de 90 días. Se admiten múltiples solicitudes del mismo cliente.

La respuesta de disponibilidad descrita no confirma un cupo reservado. Las solicitudes guardan un día, no una hora de atención.

### Compras de repuestos

| Método/ruta | Función | Acceso mediante BFF |
| --- | --- | --- |
| `POST /api/compras` | Crear o recuperar la misma solicitud por UUID. | Cliente institucional |
| `GET /api/compras` | Listar únicamente compras del correo autenticado. | Cliente institucional |
| `POST /api/registros` | Persistir una solicitud local de acceso; devuelve 202. | JWT institucional y scope, sin exigir aún rol Cliente |
| `GET /api/registros` | Consultar solicitudes locales de acceso. | Admin |

El cuerpo de compra contiene únicamente `solicitudId` (UUID válido) y
`productos:[{"productoId":1,"cantidad":2}]`. Admite hasta 50 líneas y de 1 a
1000 unidades por producto, sujeto a disponibilidad. El BFF deriva el correo
institucional y el nombre del JWT; este último se transmite mediante
`X-Customer-Name-Encoded` en UTF-8/Base64. No se toman nombre, precio, RUT,
teléfono, región ni taller del cuerpo. Repetir UUID y carrito devuelve la misma
orden; reutilizarlo para otro carrito se rechaza. No hay pasarela ni cobro en línea.

Registrar el acceso no crea cuentas o aprobaciones en Entra ni asigna roles. Esa
gestión la realiza manualmente la persona responsable del directorio.

## Validaciones

- Patente: seis caracteres alfanuméricos separados en pares, por ejemplo `HD-JK-17`.
- Modelo: letras, números y espacios.
- RUT: cuerpo numérico y dígito verificador, incluida K final, comprobado con Módulo 11. Valida el dígito, no consulta existencia o identidad en un registro oficial.
- Teléfono: ocho dígitos; la interfaz muestra el prefijo chileno +56 9.
- Nombre: letras, espacios, apóstrofes y guiones. Correo: formato válido.
- Año de solicitud: desde 1900 hasta el año actual + 1; motivo de 10–500 caracteres.
- Taller: ID interno 1 asignado por el servidor; sin selección de región o sucursal. Los valores antiguos se conservan en el historial.
- Ítems de órdenes mecánicas: hasta 200 líneas, IDs positivos y cantidades entre 1 y 1.000.000; las líneas del mismo producto se agrupan. El carrito tiene el límite más acotado descrito arriba.

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

La integración es asíncrona, no una transacción distribuida inmediata. En compras,
la aceptación permanece `RECIBIDA` con `aceptacionPendiente=true` hasta que Catálogo
confirma la revisión. En órdenes mecánicas, aceptar puede dejar una asignación aún
pendiente; en ambos casos se impide entregar sin la confirmación necesaria.
El informe mecánico permite ajustar repuestos; el carrito confirmado no se edita
por el operador. Cancelar solicita liberar lo asignado. `app.outbox.enabled=false`
desactiva los publicadores para comprobaciones aisladas.

### Auditoría y notificaciones

Cada modificación registra su evento en el outbox dentro de la misma transacción.
Un publicador independiente envía los eventos a `AUDIT_URL/internal/events` y otro
sincroniza Reportería. Para `ACEPTADA`, `LISTA_PARA_ENTREGA` y `CANCELADA` se preparan
un correo al cliente y un ticket de taller. Crear, actualizar o marcar entregada
no genera otro correo nuevo. En compras, el aviso de aceptación se prepara solo
después de confirmar el stock.

Los mensajes se guardan como instantáneas: editar o eliminar una orden no altera
un aviso ya preparado. El correo contiene productos, cantidades, total y, al
rechazar, su motivo; no genera PDF ni adjuntos. Los identificadores se conservan
en los reintentos y los receptores deduplican. Una confirmación del broker indica
recepción del mensaje, no que el proveedor SMTP haya enviado el correo.

Stock, reportes, auditoría y notificaciones tienen confirmaciones separadas,
actualizadas por columna para no sobrescribirse entre publicadores. Los envíos de
auditoría/notificaciones son independientes del publicador de stock; en compras,
el evento de aceptación y su aviso se generan después de confirmar la asignación.
Las tareas tienen cuatro hilos de planificación y lotes acotados. Los errores de
comunicación mantienen el evento pendiente para volver a intentar. Las filas
históricas pueden reenviarse a Auditoría; no se inventan avisos antiguos si no
existe una instantánea de notificación.

Los eventos de Reportería y Auditoría se envían por HTTP interno desde el outbox
persistente. RabbitMQ recibe mensajes persistentes por `cmd.direct`, con rutas
`email.send` y `workshop.ticket`, publisher confirms y detección de mensajes sin
ruta. El transporte alternativo `http` utiliza `NOTIFICATIONS_URL/internal/commands`;
no se activan ambos a la vez. Kafka no forma parte de esta versión.

## Configuración y ejecución

Crea `.env` junto a `compose.yml`:

```dotenv
DB_USERNAME=<USUARIO_BASE_ORDENES>
DB_PASSWORD=<CONTRASENA_DE_ESTA_BASE>
INTERNAL_API_KEY=<CLAVE_COMPARTIDA_CON_BFF_Y_MICROS>
CATALOG_URL=http://<IP_PRIVADA_EC2_CATALOGO>:8082
REPORT_URL=http://<IP_PRIVADA_EC2_REPORTES>:8083
NOTIFICATIONS_URL=http://<IP_PRIVADA_EC2_NOTIFICACIONES>:8084
AUDIT_URL=http://<IP_PRIVADA_EC2_AUDITORIA>:8085
CORS_ALLOWED_ORIGINS=http://localhost:5173
MESSAGING_TRANSPORT=rabbit
RABBIT_ADDRESSES=<HOST_PRIVADO_RABBIT>:5672,<HOST_PRIVADO_RABBIT>:5673
RABBIT_USERNAME=<USUARIO_RABBIT_AUTORIZADO>
RABBIT_PASSWORD=<CONTRASENA_PRIVADA_RABBIT>
RABBIT_VHOST=/
```

Sustituye todos los marcadores por valores privados de tu instalación. Las URL no
incluyen `/api` ni `/dev`; `RABBIT_ADDRESSES` usa host:puerto, sin `http://`.
La credencial Rabbit debe poder publicar en el vhost configurado. Este micro no
necesita IDs de Azure ni credenciales SMTP. Compose establece perfil postgres,
puerto y conexión `jdbc:postgresql://postgres:5432/orders_db`; la base tiene su propio
volumen y credenciales. Copiar `.env.example` sirve para una instalación nueva,
no para sobrescribir la configuración de una EC2 existente.

Con Git, Docker Engine, Buildx y Compose instalados:

```bash
docker buildx version
docker compose version
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 ordenes
```

Para desarrollo con JDK 21, exporta las variables de las integraciones y la clave,
y ejecuta `./mvnw spring-boot:run`; PowerShell: `./mvnw.cmd spring-boot:run`.
Si verificas únicamente HTTP sin broker, configura explícitamente
`MESSAGING_TRANSPORT=http`. El perfil local usa H2 y no conserva datos al detener
el proceso. Java directo no carga `.env` automáticamente.

## EC2, actualización y conservación

[Guía de esta EC2](DESPLIEGUE_EC2.md). Permitir 8081 solo desde el grupo BFF; no publicar PostgreSQL. En BFF usar `ORDERS_URL=http://<IP_PRIVADA_ORDENES>:8081`. Si cambian Catálogo/Reportería, actualizar sus URL aquí y en BFF y recrear los contenedores afectados.

Con la rama verificada y código publicado:

```bash
git pull --ff-only origin backend-emmanuel
docker compose up -d --build
```

Conserva los archivos Compose adicionales de un despliegue existente al ejecutar
la actualización. PostgreSQL utiliza las migraciones Flyway incluidas y la
configuración JPA del perfil; no borres tablas para actualizar. Las entregas
antiguas sin revisión conservan su consumo idempotente anterior. Una orden
mecánica anterior se regulariza al guardar informe o avanzar a reparación/lista.
No recalcules entregas históricas, reinicies secuencias ni reutilices IDs con
reservas existentes. Respalda las bases antes de modificar el esquema.

El volumen conserva datos al recrear. `docker compose down -v` lo elimina. `restart: unless-stopped` recupera el servicio al reiniciar Docker/EC2, salvo detención manual; no actualiza Git. Respaldar Órdenes y Catálogo de forma coherente.

## Comprobar empaquetado

```bash
./mvnw -DskipTests package
```

PowerShell: `./mvnw.cmd '-DskipTests' package`. Comprueba compilación y empaquetado,
no una compra autenticada real. Los archivos de pruebas no forman parte de esta
versión. No publiques `.env`, PEM, tokens, credenciales de base, datos personales
ni la clave interna. Los IDs y hosts de los ejemplos son marcadores; no sustituyas
estos ejemplos por valores reales al publicar documentación.

## Configuración RabbitMQ para EP3

Los nombres, rutas y patrones están centralizados en
`src/main/java/com/talleres360/orders/config/TopologiaRabbit.java`, con prefijo
`app.rabbit.topologia`. `DeclaracionTopologiaRabbit` declara seis beans Queue,
tres Exchange y nueve Binding con los argumentos de las colas quorum existentes.
`PublicadorMensajeria` consulta esa configuración y conserva su outbox,
identificadores persistentes, publisher confirms y comprobación de retornos.
No se confirma la tarea del outbox hasta que RabbitMQ confirma su enrutamiento.
