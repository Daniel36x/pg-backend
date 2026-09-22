# PG-Backend — Product Admin

API REST para la administración de productos de un supermercado (marcas, categorías, promociones) con autenticación JWT y notificaciones en tiempo real vía MQTT hacia dispositivos IoT (ESP32/sensores por categoría).

## Stack técnico

| Componente | Tecnología |
|---|---|
| Lenguaje / runtime | Java 17 |
| Framework | Spring Boot 4.0.3 (Web MVC) |
| Seguridad | Spring Security 6 + JWT (`jjwt` 0.12.6) |
| Persistencia | Spring Data JPA / Hibernate |
| Base de datos | H2 en memoria (`jdbc:h2:mem:supermercado_db`) |
| Mensajería IoT | MQTT (Eclipse Paho client 1.2.5) |
| Build | Gradle (`gradlew`) |
| Utilidades | Lombok, Jakarta Validation |

Puerto por defecto: **8083** (`server.port` en `application.properties`).

---

## Arquitectura

Arquitectura en capas típica de Spring Boot, monolito simple:

```
Cliente HTTP
     │
     ▼
┌─────────────────────────┐
│  JwtAuthenticationFilter │  ← intercepta cada request, valida el Bearer token
└─────────────────────────┘
     │
     ▼
┌─────────────────────────┐
│      Controller          │  AuthController / ProductController
│  (@PreAuthorize por rol) │
└─────────────────────────┘
     │
     ▼
┌─────────────────────────┐
│        Service           │  AuthService / ProductService (lógica, @Transactional)
└─────────────────────────┘
     │
     ▼
┌─────────────────────────┐
│      Repository          │  Spring Data JPA (UserRepository, ProductRepository, ...)
└─────────────────────────┘
     │
     ▼
┌─────────────────────────┐
│      H2 (en memoria)     │
└─────────────────────────┘

ProductService también publica eventos hacia:
┌─────────────────────────┐
│   MqttPub (publisher)    │ → Broker MQTT (mqtt.broker.url, por defecto tcp://192.168.110.229)
│   tópico = categoría      │   (p.ej. "domestico", "alimentos")
└─────────────────────────┘
```

### Paquetes (`src/main/java/com/uco/productAdmin`)

- **`controller/`** — `AuthController`, `ProductController` y `GlobalExceptionHandler` (traduce excepciones a respuestas JSON con código HTTP).
- **`services/`** — `AuthService` (registro/login/emisión de JWT), `ProductService` (CRUD, descuentos programados, notificación MQTT, reversión automática de promociones).
- **`security/`** — filtro JWT, `SecurityConfig`, `JwtService` (firma/verificación), `CustomUserDetailsService`, entry points de error (401/403).
- **`models/`** — entidades JPA: `User`, `Product`, `Brand`, `Category`, enum `Role`.
- **`dto/`** — objetos de entrada/salida validados (`RegisterRequestDTO`, `LoginRequestDTO`, `ProductRequestDTO`, `AuthResponseDTO`).
- **`repository/`** — interfaces `JpaRepository`.
- **`exceptions/`** — `InvalidCredentialsException`, `UserAlreadyExistsException`.
- **`mqtt/`** — `MqttPub` (publicador usado por `ProductService`), `MqttSubAlimentos` / `MqttSubDomestico` (clientes `main()` de ejemplo para suscribirse a tópicos, no forman parte del arranque de Spring) y `MqttBrokerConfig` (única fuente de la URL del broker: lee `MQTT_BROKER_URL` o, si no está definida, `mqtt.broker.url` de `application.properties`; la usan tanto los subscriptores de ejemplo como, indirectamente, `MqttPub` vía `@Value`).
- **`config/AdminUserSeeder`** — `CommandLineRunner` que crea un usuario `ADMIN` al arrancar si no existe (el registro público solo permite crear rol `EMPLEADO`).

### Modelo de datos

- **User**: `id`, `username` (único), `password` (hash BCrypt), `role` (`ADMIN` | `EMPLEADO`). Implementa `UserDetails`.
- **Product**: `id`, `sku`, `barCode`, `productName`, `price`, `weight`, `brand` (→ Brand), `category` (→ Category), `lastModifiedDate` (auto en `@PrePersist`/`@PreUpdate`), campos de promoción activa: `promo`, `originalPrice`, `promoEndsAt`, y campos de descuento programado (pendiente de activarse): `promoStartsAt`, `scheduledDiscountPercentage`, `scheduledDurationMinutes`.
- **Brand** / **Category**: catálogos simples (`id`, `name` único), se crean automáticamente si no existen al registrar un producto.

### Flujo de negocio relevante

- Al crear o modificar un producto (creación, descuento por marca/categoría/SKU, activación/reversión de promo), `ProductService` publica un JSON por MQTT en un tópico igual al nombre de la categoría (normalizado en minúsculas y `_`).
- Los endpoints de descuento (`/discount`, `/discount/category`, `/discount/sku`) aceptan un `startDate` opcional para **programar** el descuento en vez de aplicarlo de inmediato (ver [Descuentos programados](#descuentos-programados)).
- Un job `@Scheduled(fixedRate = 60000)` (`activarDescuentosProgramados`) revisa cada minuto los productos con un descuento programado cuya fecha de inicio (`promoStartsAt`) ya se cumplió, y lo activa (aplicando el mismo flujo que un descuento inmediato).
- Otro job `@Scheduled(fixedRate = 60000)` (`revertirPromocionesVencidas`) revisa cada minuto los productos con promoción **activa** vencida y restaura el precio original.

---

## Roles y permisos

Hay dos roles (`Role` enum): `ADMIN` y `EMPLEADO`.

- **`ADMIN`**: acceso total — puede crear productos, modificarlos por completo, eliminarlos por SKU, aplicar/programar promociones (descuentos por marca, categoría o SKU) y consultarlos.
- **`EMPLEADO`**: rol operativo, limitado a **cambiar precios** (vía `PUT /products/{id}`) y **crear/programar promociones por marca, categoría o SKU**; también puede consultar productos (`GET`) para saber qué precios/promos aplicar. **No puede crear productos nuevos** ni **eliminarlos** (`POST /products` y `DELETE /products/sku/{sku}` siguen siendo exclusivos de `ADMIN`).
- El registro público (`POST /api/v1/auth/register`) **siempre** crea usuarios con rol `EMPLEADO`; el único `ADMIN` que existe por defecto es el creado por `AdminUserSeeder` al arrancar (ver [Usuario administrador por defecto](#usuario-administrador-por-defecto)).

| Endpoint | ADMIN | EMPLEADO |
|---|---|---|
| `POST /api/v1/auth/register` / `login` | ✅ (público) | ✅ (público) |
| `POST /api/v1/products` (crear producto) | ✅ | ❌ |
| `PUT /api/v1/products/{id}` (modificar producto / precio) | ✅ | ✅ |
| `PATCH /api/v1/products/discount` (promoción por marca) | ✅ | ✅ |
| `PATCH /api/v1/products/discount/category` (promoción por categoría) | ✅ | ✅ |
| `PATCH /api/v1/products/discount/sku` (promoción por SKU) | ✅ | ✅ |
| `GET /api/v1/products` / `GET /api/v1/products/{id}` | ✅ | ✅ |
| `DELETE /api/v1/products/sku/{sku}` (eliminar producto) | ✅ | ❌ |

## Endpoints

Prefijo base: `/api/v1`.

### `POST /api/v1/auth/register` — público

Crea un usuario nuevo. **Siempre** con rol `EMPLEADO` (no es posible crear `ADMIN` por esta vía).

- Body:
  ```json
  { "username": "string (obligatorio)", "password": "string, mín. 6 caracteres" }
  ```
- Respuestas:
  - `201 Created` → `AuthResponseDTO { token, tokenType: "Bearer", username, role }`
  - `400` si falla la validación (`@Valid`).
  - `409 Conflict` si el username ya existe (`UserAlreadyExistsException`).
- **Seguridad**: sin autenticación (`permitAll`). Contraseña almacenada con **BCrypt**. El token JWT se devuelve ya en el registro (autologin).

### `POST /api/v1/auth/login` — público

- Body: `{ "username": "string", "password": "string" }`
- Respuestas:
  - `200 OK` → `AuthResponseDTO` con token JWT.
  - `401 Unauthorized` si las credenciales son inválidas (`InvalidCredentialsException`), manejado de forma genérica para no filtrar si el error es usuario o contraseña.
- **Seguridad**: sin autenticación previa; usa `AuthenticationManager` + `DaoAuthenticationProvider` + BCrypt para verificar.

### `POST /api/v1/products` — requiere rol `ADMIN`

Crea un producto (crea marca/categoría si no existen) y notifica por MQTT.

- Header requerido: `Authorization: Bearer <token>`
- Body (`ProductRequestDTO`): `sku`, `price`, `barCode`, `productName`, `brand`, `weight`, `category` (todos obligatorios, validados con `@NotNull`/`@NotBlank`).
- Respuestas: `201 Created` con el `Product` creado; `403` si no es ADMIN; `401` si no hay token/token inválido; `400` si falla validación.

### `PATCH /api/v1/products/discount` — requiere rol `ADMIN` o `EMPLEADO`

Aplica (o programa) un descuento porcentual temporal a todos los productos de una marca (crea una promoción por marca).

- Query params: `brand` (String), `percentage` (BigDecimal), `durationMinutes` (Long), `startDate` (opcional, ISO-8601 `yyyy-MM-ddTHH:mm:ss`).
- El precio original se guarda para poder revertir automáticamente cuando expira `durationMinutes`.
- Si se envía `startDate` con una fecha futura, el descuento **no se aplica de inmediato**: queda programado y un job lo activa automáticamente al llegar esa fecha (ver [Descuentos programados](#descuentos-programados)).
- Respuestas: `200 OK` (texto plano de confirmación, indica si se aplicó ya o quedó programado); `404`/`400` si la marca no tiene productos (excepción genérica mapeada por `GlobalExceptionHandler`); `403`/`401` según el caso.

### `PATCH /api/v1/products/discount/category` — requiere rol `ADMIN` o `EMPLEADO`

Igual que el anterior pero filtrando por `category` en vez de `brand` (crea una promoción por categoría). También admite `startDate` para programar el descuento.

### `PATCH /api/v1/products/discount/sku` — requiere rol `ADMIN` o `EMPLEADO`

Igual que los anteriores pero filtrando por `sku` (Long) en vez de `brand`/`category`: aplica (o programa) el descuento a un único producto (crea una promoción individual por SKU).

- Query params: `sku` (Long), `percentage` (BigDecimal), `durationMinutes` (Long), `startDate` (opcional, ISO-8601 `yyyy-MM-ddTHH:mm:ss`).
- Respuestas: `200 OK` (texto plano de confirmación, indica si se aplicó ya o quedó programado); `404`/`400` si no existe un producto con ese SKU (excepción genérica mapeada por `GlobalExceptionHandler`); `403`/`401` según el caso.

#### Descuentos programados

Los tres endpoints de descuento anteriores comparten el mismo mecanismo de programación:

- El parámetro `startDate` es **opcional**. Si se omite, o si se envía una fecha igual o anterior al momento actual, el descuento se aplica **de inmediato** (comportamiento previo, sin cambios).
- Si `startDate` es una fecha **futura**, el/los producto(s) quedan marcados como "con descuento pendiente" (`promoStartsAt`, `scheduledDiscountPercentage`, `scheduledDurationMinutes`) y **no cambian de precio todavía**; no se notifica por MQTT en este punto.
- Un job `@Scheduled(fixedRate = 60000)` (`activarDescuentosProgramados`) revisa cada minuto los productos cuyo `promoStartsAt` ya se cumplió: en ese momento aplica el descuento (baja el precio, marca `promo = true`, calcula `promoEndsAt = ahora + durationMinutes`) y limpia los campos de programación. A partir de ahí, la reversión al finalizar `durationMinutes` funciona igual que un descuento aplicado de forma inmediata (job `revertirPromocionesVencidas`).
- Ejemplo — programar un 20% de descuento en la marca `Diana` a partir del 1 de octubre de 2026 a las 08:00, activo por 120 minutos:
  ```
  PATCH /api/v1/products/discount?brand=Diana&percentage=20&durationMinutes=120&startDate=2026-10-01T08:00:00
  ```
- Solo puede existir un descuento programado por producto a la vez: si se programa uno nuevo sobre un producto que ya tenía otro pendiente, el más reciente sobrescribe al anterior.

### `PUT /api/v1/products/{id}` — requiere rol `ADMIN` o `EMPLEADO`

Modifica un producto existente **de forma permanente** (reemplaza todos sus datos), a diferencia de los endpoints de descuento que son temporales y se revierten solos.

- Header requerido: `Authorization: Bearer <token>` (rol `ADMIN` o `EMPLEADO`) — es la vía habilitada para que `EMPLEADO` modifique precios.
- Body (`ProductRequestDTO`): mismos campos que la creación, todos obligatorios — es un reemplazo completo, no parcial: hay que enviar todos los campos aunque no cambien.
  ```json
  {
    "sku": 123456,
    "price": 15000,
    "barCode": 7701234567890,
    "productName": "Arroz Diana 500g",
    "brand": "Diana",
    "weight": 0.5,
    "category": "Alimentos"
  }
  ```
  - `sku` (Long, `@NotNull`) — código SKU del producto.
  - `price` (BigDecimal, `@NotNull`) — nuevo precio permanente.
  - `barCode` (Long, `@NotNull`) — código de barras.
  - `productName` (String, `@NotBlank`) — nombre del producto.
  - `brand` (String, `@NotBlank`) — nombre de la marca; si no existe se crea automáticamente.
  - `weight` (Double, `@NotNull`) — peso.
  - `category` (String, `@NotBlank`) — nombre de la categoría; si no existe se crea automáticamente.
- Busca o crea la marca/categoría igual que en la creación.
- Si el producto tenía una promoción activa, se cancela (`promo=false`, `originalPrice=null`, `promoEndsAt=null`) para que el precio nuevo no sea revertido luego por el job programado de reversión de promociones.
- Notifica el cambio por MQTT igual que el resto de mutaciones.
- Respuestas: `200 OK` con el `Product` actualizado; `404` si el ID no existe; `403`/`401` según el caso; `400` si falla la validación.

### `GET /api/v1/products` — requiere rol `ADMIN` o `EMPLEADO`

Lista todos los productos. Cualquier usuario autenticado (sin importar el rol) puede consultarlos.

### `GET /api/v1/products/{id}` — requiere rol `ADMIN` o `EMPLEADO`

Obtiene un producto por ID. `404` si no existe (vía `RuntimeException` → `GlobalExceptionHandler`).

### `DELETE /api/v1/products/sku/{sku}` — requiere rol `ADMIN`

Elimina de forma permanente el producto cuyo `sku` coincide con el valor recibido en la ruta.

- Header requerido: `Authorization: Bearer <token>` (rol `ADMIN`).
- Respuestas: `204 No Content` si se elimina correctamente; `404` si no existe un producto con ese SKU (`RuntimeException` → `GlobalExceptionHandler`); `403`/`401` según el caso.
- No notifica por MQTT (el producto deja de existir).

### `GET /h2-console/**` — público (solo desarrollo)

Consola web de la base de datos H2 en memoria. Expuesta sin autenticación y con protección de frames deshabilitada (`frameOptions().disable()`) para poder renderizarse en iframe.

> ⚠️ Debe deshabilitarse o protegerse antes de exponer el backend en un entorno accesible públicamente (ver sección de seguridad).

---

## Seguridad

### Autenticación: JWT stateless

- `SessionCreationPolicy.STATELESS`: no hay sesiones de servidor; cada request debe traer su token.
- El token se firma con **HMAC-SHA** (`Keys.hmacShaKeyFor`) usando `jwt.secret`, y expira según `jwt.expiration-ms` (24h por defecto).
- El payload solo contiene `sub` (username), `iat` y `exp` — no incluye el rol ni datos sensibles; el rol se resuelve en cada request contra la base de datos (`CustomUserDetailsService`), por lo que **revocar/cambiar el rol de un usuario aplica de inmediato**, no hay que esperar a que expire el token.
- `JwtAuthenticationFilter` (`OncePerRequestFilter`) lee el header `Authorization: Bearer <token>`, valida firma/expiración/usuario y puebla el `SecurityContext`. Si el token es inválido/expirado, limpia el contexto y deja que la request continúe sin autenticar (el endpoint protegido responderá 401 más abajo en la cadena).

### Autorización: roles y `@PreAuthorize`

- Dos roles: `ADMIN` y `EMPLEADO` (`Role` enum), mapeados a `ROLE_ADMIN` / `ROLE_EMPLEADO` como `GrantedAuthority`.
- `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` (solo `POST /products`) / `hasAnyRole('ADMIN','EMPLEADO')` (resto de endpoints de productos) en cada método de `ProductController` (autorización a nivel de método, no solo por ruta). Ver la matriz completa en [Roles y permisos](#roles-y-permisos).
- Reglas a nivel de `HttpSecurity`: `/api/v1/auth/**` y `/h2-console/**` públicas; **todo lo demás requiere estar autenticado** (`anyRequest().authenticated()`), y luego `@PreAuthorize` añade el control fino por rol.

### Manejo de errores de seguridad

- `JwtAuthenticationEntryPoint` → `401` con JSON uniforme cuando falta token o es inválido.
- `JwtAccessDeniedHandler` (+ `GlobalExceptionHandler` para `AccessDeniedException`) → `403` con JSON uniforme cuando el usuario está autenticado pero no tiene el rol requerido.
- `GlobalExceptionHandler` también normaliza `InvalidCredentialsException` (401), `UserAlreadyExistsException` (409) y cualquier otra `RuntimeException` de negocio (404), evitando exponer *stack traces*.

### CORS

- Configurado explícitamente en `SecurityConfig` (bean `CorsConfigurationSource`), aplicado a `/**`.
- Orígenes permitidos vienen de `app.cors.allowed-origins` (lista separada por coma, por defecto `http://localhost:*,http://127.0.0.1:*` — solo desarrollo). **En producción hay que sobreescribir `CORS_ALLOWED_ORIGINS` con el/los dominio(s) reales del frontend**, igual que se hace con `JWT_SECRET`.
- Métodos permitidos: `GET, POST, PUT, PATCH, DELETE, OPTIONS`; headers permitidos: todos (`*`).
- `allowCredentials(false)`: no se envían/aceptan cookies en las peticiones cross-origin, consistente con que la API es stateless y se autentica solo con el header `Authorization: Bearer <token>` (no hace falta exponer credenciales de navegador).

### Contraseñas

- Hasheadas con **BCrypt** (`BCryptPasswordEncoder`), nunca se almacenan ni se devuelven en texto plano.
- Validación mínima: `@Size(min = 6)` en el registro. No hay política adicional de complejidad (mayúsculas, símbolos, etc.) ni límite de intentos / rate limiting sobre `/login`.

### Usuario administrador por defecto

- `AdminUserSeeder` crea, al arrancar, un usuario `ADMIN` con credenciales tomadas de `app.security.default-admin.username` / `...password` (por defecto `admin` / `Admin123!`) si no existe aún.
- **Riesgo**: si no se sobreescriben las variables de entorno `DEFAULT_ADMIN_USER` / `DEFAULT_ADMIN_PASSWORD` en producción, el backend queda con credenciales de administrador conocidas públicamente (están en este repo).

### Puntos a reforzar antes de producción

1. **Secreto JWT y credenciales de admin en el repo**: `application.properties` trae valores por defecto para `jwt.secret`, `DEFAULT_ADMIN_USER` y `DEFAULT_ADMIN_PASSWORD`. Deben sobreescribirse siempre vía variables de entorno y rotarse; no deberían tener un valor por defecto funcional en el código versionado.
2. **CSRF deshabilitado**: aceptable para una API stateless consumida por clientes no-browser (SPA/servicios) que usan Bearer token en vez de cookies; si en algún momento se introduce autenticación por cookie, debe reevaluarse.
3. **Consola H2 expuesta** (`/h2-console/**` público, `frameOptions` deshabilitado): solo debe habilitarse en desarrollo. En producción, `spring.h2.console.enabled` debería ser `false` y la ruta debe quedar protegida o eliminada.
4. **CORS restringido a localhost por defecto**: `app.cors.allowed-origins` trae de fábrica solo `http://localhost:*` y `http://127.0.0.1:*` (ver sección [CORS](#cors)); antes de desplegar hay que sobreescribir `CORS_ALLOWED_ORIGINS` con el dominio real del frontend, o las peticiones cross-origin serán bloqueadas por el navegador.
5. **Sin rate limiting / bloqueo de cuenta** en `/auth/login` ni `/auth/register`: expuesto a fuerza bruta y enumeración de usuarios (aunque el mensaje de error de login es genérico, `register` sí revela con 409 si un username ya existe).
6. **MQTT sin TLS ni autenticación**: `MqttPub` se conecta al broker (Mosquitto) configurado en `mqtt.broker.url` (por defecto `tcp://192.168.110.229`, sobreescribible con `MQTT_BROKER_URL`) sin usuario/contraseña ni TLS. Los mensajes publicados (precio, SKU, nombre de producto) viajan en claro. Recomendado: usar `ssl://` y credenciales de broker.
7. **JWT no incluye claim de rol**: por diseño consulta el rol en cada request (más seguro ante cambios de rol), pero implica una consulta a BD por cada request autenticada; si se optimiza incluyendo el rol en el token, hay que invalidar tokens en cambios de rol.
8. **`EMPLEADO` puede modificar más que solo el precio**: `PUT /api/v1/products/{id}` es un reemplazo completo del producto (`ProductRequestDTO`), así que un `EMPLEADO` autorizado a "modificar precios" también puede cambiar `productName`, `brand`, `category`, `barCode` y `weight` en la misma llamada. Si se necesita restringir estrictamente a solo el campo `price`, habría que introducir un endpoint dedicado (p. ej. `PATCH /products/{id}/price`) y quitarle a `EMPLEADO` el acceso al `PUT` completo, dejándolo solo para `ADMIN`.

---

## Configuración (`src/main/resources/application.properties`)

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `server.port` | `8083` | Puerto HTTP |
| `spring.datasource.url` | `jdbc:h2:mem:supermercado_db` | BD en memoria (se reinicia en cada arranque) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-generación/actualización de esquema |
| `spring.h2.console.enabled` | `true` | Consola H2 en `/h2-console` |
| `jwt.secret` | `${JWT_SECRET:...}` | Clave HMAC para firmar JWT — **sobreescribir en prod** |
| `jwt.expiration-ms` | `86400000` (24h) | Expiración del token |
| `app.security.default-admin.username` | `${DEFAULT_ADMIN_USER:admin}` | Usuario admin creado al arrancar |
| `app.security.default-admin.password` | `${DEFAULT_ADMIN_PASSWORD:Admin123!}` | Password admin creado al arrancar — **sobreescribir en prod** |
| `app.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:*,http://127.0.0.1:*}` | Orígenes permitidos por CORS, separados por coma — **sobreescribir en prod** con el dominio del frontend |
| `mqtt.broker.url` | `tcp://192.168.110.229` | URL del broker MQTT — **único lugar a editar** al cambiar de red (o exportar `MQTT_BROKER_URL`, que tiene prioridad); la leen `MqttPub`, `MqttSubAlimentos` y `MqttSubDomestico` a través de `MqttBrokerConfig` |

## Ejecución local

```bash
./gradlew bootRun
```

API disponible en `http://localhost:8083`, consola H2 en `http://localhost:8083/h2-console`.

Para la configuración del broker MQTT (Mosquitto) usado por `MqttPub`, ver [`commands.md`](commands.md).
