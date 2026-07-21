# Plataforma de E-Commerce B2B — Lab 2 (PostGIS)

Grupo 4 — Taller de Base de Datos Diurno 1-2026, USACH.

Tienda mayorista B2B (inventario de productos, clientes de empresas, carritos de compra y facturación) extendida en el Lab 2 con una capa geoespacial completa en PostGIS: ubicación real de almacenes y direcciones de entrega, asignación automática del almacén más cercano en el checkout, cobertura geográfica, zonas de exclusión y reportes de ventas por comuna/distrito sobre un mapa.

---

## 1. Arquitectura y tecnologías

```
┌─────────────────────────────┐        ┌──────────────────────────────┐        ┌───────────────────────────────┐
│   Frontend (puerto 8080)    │  HTTP  │   Backend (puerto 8090)       │  JDBC  │   Base de datos (puerto 5433)  │
│  Vue 3 + Pinia + Axios +    │ ─────► │  Spring Boot 4 / Java 21      │ ─────► │  PostgreSQL 15 + PostGIS 3.3   │
│  Leaflet (mapa)             │  JWT   │  Controllers → Services →     │        │  Tablas + Triggers + Stored    │
│  Servido por Nginx          │◄────── │  Repository (JdbcTemplate,    │◄────── │  Procedures + Vistas           │
│                              │  JSON  │  SQL nativo, SIN ORM)         │        │  Materializadas + Índices GIST │
└─────────────────────────────┘        └──────────────────────────────┘        └───────────────────────────────┘
```

- **Backend:** Java 21 + Spring Boot (Spring MVC clásico + `spring-boot-starter-data-jdbc`, usando **`JdbcTemplate` con SQL nativo escrito a mano** — no se usa ningún ORM, según lo exigido por el enunciado). Seguridad con Spring Security + JWT (`Config/JwtAuthenticationFilter.java`, `Config/JwtMiddlewareService.java`), autorización por rol (RBAC: `CLIENTE` / `ADMIN`) definida en `Config/SecurityConfig.java`.
- **Frontend:** Vue 3 (Composition API) + Pinia (estado de sesión) + Axios (`src/http-common.ts`) + Leaflet (mapa de logística con capas GeoJSON).
- **Base de datos:** PostgreSQL 15 con extensión **PostGIS** habilitada. Toda la lógica de negocio crítica (checkout, cobertura, zonas de exclusión, última milla) vive en la base de datos como **stored procedures** y **triggers**, no en Java, para garantizar atomicidad e integridad sin importar qué cliente escriba en la BD.
- **Infraestructura:** 3 contenedores Docker independientes (`db`, `backend`, `frontend`), orquestados con un único `docker-compose.yml` en la raíz de este directorio.

### Estructura de carpetas

```
Lab 2/
├── docker-compose.yml        # Orquesta los 3 servicios (db, backend, frontend)
├── backendB2B/
│   ├── init.sql               # Script único: tablas, índices, triggers, SPs, vistas materializadas, seeders
│   ├── src/main/java/com/ecommerceb2b/backend/
│   │   ├── Controllers/       # Endpoints REST (@RestController)
│   │   ├── Services/          # Lógica de negocio y validación manual
│   │   ├── Repository/        # Acceso a datos vía JdbcTemplate (SQL nativo)
│   │   ├── Entities/          # POJOs usados como DTO de request/response y RowMapper
│   │   ├── Config/            # Seguridad, JWT, CORS
│   │   ├── Util/              # Utilitarios (ej. validación de RUT, normalización de coordenadas GeoJSON)
│   │   └── Loader/            # Carga inicial de comunas / unidades vecinales (geometrías reales)
│   └── Dockerfile
└── frontendB2B/
    ├── src/
    │   ├── views/              # Páginas (Admin/*, Customers/*)
    │   ├── services/           # Llamadas Axios a la API
    │   ├── stores/             # Pinia (auth, etc.)
    │   └── router/             # Rutas protegidas por rol
    └── Dockerfile
```

---

## 2. Manual de instalación y despliegue

### Prerrequisitos

- [Docker](https://docs.docker.com/get-docker/) y [Docker Compose](https://docs.docker.com/compose/) (Docker Desktop en Windows/Mac ya los incluye).
- Puertos libres en el host: **8080** (frontend), **8090** (backend), **5433** (Postgres).
- No se necesita instalar Java, Node ni Postgres localmente — todo corre dentro de los contenedores.

### Variables de entorno

Todas las variables ya están fijadas en `docker-compose.yml` con valores de desarrollo listos para usar (no requiere crear ningún archivo `.env` para levantar el proyecto):

| Variable | Servicio | Valor por defecto | Descripción |
|---|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `db` | `b2b` / `postgres` / `postgres` | Credenciales de la base de datos |
| `SPRING_DATASOURCE_URL` | `backend` | `jdbc:postgresql://db:5432/b2b` | Cadena de conexión JDBC hacia el contenedor `db` |
| `JWT_SECRET` | `backend` | (definido en `docker-compose.yml`) | Clave de firma HMAC de los tokens JWT |
| `JWT_EXPIRATION` | `backend` | `86400000` (24h) | Expiración del token en milisegundos |
| `VITE_API_BASE_URL` | `frontend` (build-time) | `http://localhost:8090` | URL base que usa el frontend para llamar a la API |

Si necesitas cambiar algún valor (por ejemplo, un puerto ya ocupado en tu máquina), edítalo directamente en `docker-compose.yml` antes de levantar los contenedores.

### Pasos para levantar el proyecto desde cero

Desde esta carpeta (`Lab 2/`):

```bash
docker compose up --build
```

Esto:
1. Descarga la imagen `postgis/postgis:15-3.3` y levanta la base de datos, ejecutando automáticamente `backendB2B/init.sql` la **primera vez** que se crea el volumen (crea tablas, índices GIST, triggers, stored procedures, vistas materializadas y datos de prueba).
2. Compila el backend con Maven dentro de un contenedor multi-stage (`backendB2B/Dockerfile`) y lo levanta en el puerto **8090**.
3. Compila el frontend con Vite dentro de un contenedor multi-stage (`frontendB2B/Dockerfile`) y lo sirve con Nginx en el puerto **8080**.

Al arrancar, el backend además carga en segundo plano (sin bloquear el arranque) las geometrías reales de las 52 comunas de la Región Metropolitana y ~2300 unidades vecinales desde un archivo GeoJSON local (no depende de red externa en el camino normal). Esto toma unos segundos — revisa los logs del contenedor `backend` y espera el mensaje `Verificación de geometrías espaciales finalizada` antes de probar el mapa de logística.

Para correrlo en segundo plano en vez de bloquear la terminal:

```bash
docker compose up --build -d
```

### Verificación de que todo quedó arriba

```bash
docker compose ps                       # los 3 servicios deben decir "Up"
curl http://localhost:8090/api/productos   # 200 (listado público de productos)
curl http://localhost:8090/api/almacenes   # 403 (requiere JWT de un usuario ADMIN) — confirma que el backend responde
```

Abrir `http://localhost:8080` en el navegador — debería cargar la pantalla de login. Usuario administrador de prueba (ver seeders en `init.sql`): `admin@ecommerceb2b.cl` (la contraseña está hasheada con BCrypt en el seed; usa el flujo de `POST /usuario/register` para crear tu propio usuario de prueba si no tienes la contraseña original).

### Apagar y limpiar

```bash
docker compose down          # detiene y elimina los contenedores, conserva los datos (volumen)
docker compose down -v       # además borra el volumen de la base de datos (vuelve a correr init.sql desde cero la próxima vez)
```

### Problemas comunes

- **Puerto ya en uso:** cambia el mapeo de puertos (`"8080:80"`, `"8090:8090"`, `"5433:5432"`) en `docker-compose.yml`.
- **El mapa de logística sale vacío justo después de levantar todo:** espera unos segundos — la carga de comunas/unidades vecinales corre en un hilo en segundo plano al arrancar el backend.
- **Cambios en `init.sql` no se reflejan:** el script solo corre en la creación inicial del volumen de Postgres. Si ya existía el volumen, hay que `docker compose down -v` para forzar que se re-ejecute.

---

## 3. Documentación de la API

Base URL (local): `http://localhost:8090`.

Todas las rutas bajo `/api/**` (salvo `/api/productos` y `/api/categorias` en `GET`) requieren el header `Authorization: Bearer <token>`. Los roles disponibles son `CLIENTE` y `ADMIN` (ver `Config/SecurityConfig.java` para el detalle completo de qué rol puede usar cada ruta).

### 3.1 Autenticación

**`POST /usuario/login`**
```json
{ "correo": "admin@ecommerceb2b.cl", "contrasena": "..." }
```
Respuesta `200`:
```json
{ "token": "eyJhbGciOiJIUzI1NiIs...", "mensaje": "Login exitoso" }
```

**`POST /usuario/register`** — crea un usuario `CLIENTE` (nombre, correo, contraseña, RUT de empresa).

### 3.2 Productos y Categorías (CRUD)

- `GET /api/productos` — listado (público).
- `GET /api/productos/buscar?termino=laptop` — búsqueda por nombre o descripción (usa `idx_producto_sku`/índices de texto).
- `GET /api/productos/sku/{sku}` — búsqueda exacta por SKU.
- `GET /api/productos/categoria/{categoriaId}` — productos de una categoría.
- `POST /api/productos` / `PUT /{id}` / `DELETE /{id}` — **rol `ADMIN`**.
- `PATCH /api/productos/{id}/stock` — ajustar stock — **rol `ADMIN`**.
- `POST /api/productos/descuento` — aplica el stored procedure `aplicar_descuento_categoria`:
  ```json
  { "categoriaId": 3, "porcentaje": 15 }
  ```
- `GET/POST/PUT/DELETE /api/categorias` — CRUD estándar. `GET /api/categorias/buscar?termino=...`.

### 3.3 Carritos y Checkout

- `GET /api/carritos/cliente/{idCliente}/activo` — carrito activo del cliente.
- `POST /api/carrito-productos` — agregar un producto al carrito.
- `POST /api/ordenes/solicitar/{carritoId}` — **checkout atómico** (rol `CLIENTE`). Ejecuta `procesar_checkout(...)` en la base de datos: valida cobertura geográfica, valida zona de exclusión, descuenta stock, asigna el almacén más cercano con `ST_Distance` y crea orden + factura en una sola transacción.
  ```json
  { "infoEntregaId": 12, "datosPagoId": 4 }
  ```
  Respuesta `201`: la `FacturaEntidad` generada (con `precioTotal`, `costoEnvio`, etc.). Si la dirección está fuera de cobertura, o el carrito contiene una categoría restringida y la dirección cae en una zona residencial protegida, responde `400` con el mensaje del trigger correspondiente.
- `PATCH /api/ordenes/{id}/aprobar` — **rol `ADMIN`**, dispara el trigger `trg_actualizar_ultima_compra`.

### 3.4 Facturas

- `GET /api/facturas/orden/{ordenId}` — detalle histórico de una factura (JSON). Un `CLIENTE` solo puede ver las propias.
- `GET /api/facturas/orden/{ordenId}/descargar` y `GET /api/facturas/{id}/descargar` — descarga el detalle de la factura como PDF.

### 3.5 Almacenes — geometrías PostGIS, acepta lat/lng plano **o** GeoJSON Point

`POST /api/almacenes` y `PUT /api/almacenes/{id}` (rol `ADMIN`) aceptan **dos formatos de coordenadas** de forma intercambiable (`Util/CoordenadasNormalizador.java` los normaliza automáticamente):

**Formato plano:**
```json
{
  "nombre": "Bodega Central",
  "direccion": "Av. Libertador 1234",
  "latitud": -33.45,
  "longitud": -70.65
}
```

**Formato GeoJSON Point** (equivalente, nota el orden `[lon, lat]` del estándar GeoJSON):
```json
{
  "nombre": "Bodega Central",
  "direccion": "Av. Libertador 1234",
  "type": "Point",
  "coordinates": [-70.65, -33.45]
}
```

Si el body no calza con ninguno de los dos formatos, responde `400`:
```json
{ "error": "Formato de coordenadas inválido, use {latitud,longitud} o GeoJSON Point" }
```

- `GET /api/almacenes/geojson` — todos los almacenes como `FeatureCollection` GeoJSON (para pintar en el mapa).
- `GET/PUT /api/almacenes/{id}/stock` — stock por almacén.

### 3.6 Direcciones de entrega — mismo soporte GeoJSON de entrada

`POST /api/entregas` y `PUT /api/entregas/{id}` (autenticado) aceptan igual que almacenes, `{latitud, longitud}` o `{type: "Point", coordinates: [lon, lat]}`:

```json
{
  "usuarioId": 3,
  "direccion": "Los Aromos",
  "numero": "456",
  "rut_Recibe_Entrega": "12.345.678-9",
  "comuna": "Providencia",
  "type": "Point",
  "coordinates": [-70.61, -33.43]
}
```

El backend valida automáticamente (trigger `validar_cobertura_direccion`) que la coordenada esté dentro del polígono de cobertura de la empresa antes de guardar.

- `GET /api/entregas/geojson` — todas las direcciones activas como GeoJSON.
- `GET /api/entregas/comunas` — listado de comunas disponibles.

### 3.7 Mapa de logística y reportes espaciales (`ADMIN`, todo devuelve GeoJSON)

- `GET /api/logistica/mapa/comunas` — choropleth de ventas por comuna (vista materializada `ventas_por_comuna`, con `nivel_semaforo`: `ALTO`/`MEDIO`/`BAJO`/`SIN_VENTAS`).
- `GET /api/logistica/mapa/distritos` — mismo reporte agregado por distrito postal (`ventas_por_distrito`, generado con `ST_Union`).
- `GET /api/logistica/mapa/cobertura` — polígono de cobertura de la empresa.
- `POST /api/logistica/mapa/refrescar` — fuerza el refresco manual de ambas vistas materializadas (además se refrescan automáticamente cada 6h y tras cada venta nueva).
- `POST /api/admin/direccion/verificar` — "dry-run": dado un punto (plano o GeoJSON, mismo normalizador), indica si cae dentro de cobertura, en qué comuna y si es zona residencial protegida:
  ```json
  { "type": "Point", "coordinates": [-70.61, -33.43] }
  ```
- `GET /api/admin/comunas/geojson`, `GET /api/admin/unidades-vecinales`, `GET /api/admin/unidades-vecinales/protegidas` — capas administrativas del mapa (GeoJSON).
- `GET /api/admin/geocodificar?q=...` — proxy de geocodificación (Nominatim/OpenStreetMap) para el buscador de direcciones del mapa.

### 3.8 Reportes (vista materializada Lab 1)

- `GET /api/reportes/ventas` / `/ventas/mes` / `/ventas/categoria` / `/ventas/anio` — consultas sobre `vw_ventas_mensuales_por_categoria`.
- `POST /api/reportes/refrescar` — refresco manual de esa vista materializada.

---

## 4. Seguridad y roles (RBAC)

- **`CLIENTE`**: crea/gestiona su propio carrito, realiza checkout, ve sus propias órdenes/facturas/direcciones de entrega.
- **`ADMIN`**: gestiona productos/categorías/stock/almacenes, aprueba órdenes, accede a reportes y al mapa de logística.
- Middleware: `Config/JwtAuthenticationFilter.java` valida el JWT en cada request y puebla `SecurityContextHolder` con la autoridad `ROLE_<rol del token>`; `Config/SecurityConfig.java` define qué rutas requieren qué rol.

---

## 5. Integrantes

Grupo 4 — Taller de Base de Datos Diurno 1-2026: María Fuentes, Constanza Viera, Ambar Uzcátegui, Ignacio Ávila, Camilo Cuero.
