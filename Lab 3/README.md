# Plataforma de E-Commerce B2B — Lab 3 (MongoDB + PostGIS)

Grupo 4 — Taller de Base de Datos Diurno 1-2026, USACH.

Tienda mayorista B2B (inventario de productos, clientes de empresas, carritos de compra y facturación). El sistema es **híbrido**: conserva toda la capa geoespacial en **PostGIS** desarrollada en el Lab 2 (ubicación real de almacenes y direcciones de entrega, asignación automática del almacén más cercano en el checkout, cobertura geográfica, zonas de exclusión y reportes de ventas por comuna/distrito sobre un mapa) y suma en el Lab 3 una capa documental en **MongoDB desplegado como Replica Set**, donde viven los requerimientos NoSQL: modelado embedding/referencing, Schema Validation, transacciones multi-documento, aggregation pipelines, estrategia de índices y change streams.

> La justificación de diseño documental (embedding vs referencing, snapshot de precio) está en **[`docs/01-modelado-documental.md`](docs/01-modelado-documental.md)**.

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
- **Base de datos relacional/geoespacial:** PostgreSQL 15 con extensión **PostGIS** habilitada. Toda la lógica geoespacial crítica (cobertura, zonas de exclusión, última milla) vive en la base de datos como **stored procedures** y **triggers**, no en Java, para garantizar atomicidad e integridad sin importar qué cliente escriba en la BD.
- **Base de datos documental (Lab 3):** **MongoDB 7.0 desplegado como Replica Set `rs0`** con dos nodos (`mongo1` PRIMARY + `mongo2` SECONDARY) y autenticación con keyfile. El replica set no es opcional: MongoDB solo habilita **transacciones ACID multi-documento** y **change streams** cuando corre replicado. El backend accede con el **driver nativo `mongodb-driver-sync`** (sin Spring Data, igual que la capa PostGIS usa `JdbcTemplate` sin ORM): la conexión se configura en `Config/MongoConfig.java` y las sesiones/transacciones se centralizan en `Services/MongoSesionServicio.java`.
- **Infraestructura:** contenedores Docker orquestados con un único `docker-compose.yml` en la raíz de este directorio: `db` (PostGIS), `mongo1`, `mongo2` (+ los servicios efímeros `mongo-keyfile` y `mongo-init` que preparan el replica set), `backend` y `frontend`.

```
                      ┌──────────────────────────────┐
                      │        b2b-backend            │
                      │  Spring Boot 4 / Java 17      │
                      └───────┬──────────────┬────────┘
                        JDBC  │              │  mongodb-driver-sync
                              ▼              ▼
              ┌───────────────────┐   ┌──────────────────────────────┐
              │  PostgreSQL 15    │   │      Replica Set  rs0         │
              │  + PostGIS 3.3    │   │  mongo1 :27017  (PRIMARY)     │
              │  (geoespacial)    │   │      ▲  oplog  ▼              │
              │                   │   │  mongo2 :27018  (SECONDARY)   │
              └───────────────────┘   └──────────────────────────────┘
```

### Estructura de carpetas

```
Lab 3/
├── docker-compose.yml        # Orquesta db (PostGIS), replica set Mongo, backend y frontend
├── .env.example              # Variables opcionales de compose (credenciales Mongo)
├── mongo/
│   ├── keyfile-init.sh        # Genera el keyfile compartido del replica set (permisos 400)
│   ├── rs-init.js             # rs.initiate() idempotente + usuario de aplicación
│   ├── schema-validation.js   # Validadores $jsonSchema por colección
│   ├── indexes.js             # Índices únicos, compuestos y TTL
│   ├── aggregation-pipeline.js     # Pipeline de volumen de ventas proyectado
│   ├── change-streams-merge.js     # Vista materializada $merge "productos más vendidos" + backfill
│   └── seeders/               # Datos de prueba (productos, configuración B2B)
├── docs/
│   ├── 01-modelado-documental.md   # Justificación embedding vs referencing (entregable)
│   ├── 03-checkout-transaccion.md  # Transacción multi-documento de checkout
│   ├── 05-indices.md               # Estrategia de índices
│   └── 06-change-streams-merge.md  # Change Streams + $merge y por qué el worker va aparte
├── backendB2B/
│   ├── init.sql               # Script único: tablas, índices, triggers, SPs, vistas materializadas, seeders
│   ├── src/main/java/com/ecommerceb2b/backend/
│   │   ├── Controllers/       # Endpoints REST (@RestController)
│   │   ├── Services/          # Lógica de negocio y validación manual
│   │   ├── Repository/        # Acceso a datos vía JdbcTemplate (SQL nativo)
│   │   ├── Entities/          # POJOs usados como DTO de request/response y RowMapper
│   │   ├── Config/            # Seguridad, JWT, CORS, conexión a MongoDB
│   │   ├── Util/              # Utilitarios (ej. validación de RUT, normalización de coordenadas GeoJSON)
│   │   ├── Loader/            # Carga inicial de comunas / unidades vecinales (geometrías reales)
│   │   └── Workers/           # Procesos de fondo sin HTTP (change stream de productos más vendidos)
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
- Puertos libres en el host: **8080** (frontend), **8090** (backend), **5433** (Postgres), **27017** (MongoDB primario), **27018** (MongoDB secundario).
- No se necesita instalar Java, Node, Postgres ni MongoDB localmente — todo corre dentro de los contenedores.

### Variables de entorno

Todas las variables ya están fijadas en `docker-compose.yml` con valores de desarrollo listos para usar (no requiere crear ningún archivo `.env` para levantar el proyecto):

| Variable | Servicio | Valor por defecto | Descripción |
|---|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `db` | `b2b` / `postgres` / `postgres` | Credenciales de la base de datos |
| `SPRING_DATASOURCE_URL` | `backend` | `jdbc:postgresql://db:5432/b2b` | Cadena de conexión JDBC hacia el contenedor `db` |
| `JWT_SECRET` | `backend` | (definido en `docker-compose.yml`) | Clave de firma HMAC de los tokens JWT |
| `JWT_EXPIRATION` | `backend` | `86400000` (24h) | Expiración del token en milisegundos |
| `VITE_API_BASE_URL` | `frontend` (build-time) | `http://localhost:8090` | URL base que usa el frontend para llamar a la API |
| `MONGO_REPLICA_SET` | `mongo1`, `mongo2`, `mongo-init` | `rs0` | Nombre del replica set |
| `MONGO_DB` | `mongo-init`, `backend` | `b2b` | Base de datos documental del proyecto |
| `MONGO_ROOT_USER` / `MONGO_ROOT_PASSWORD` | `mongo1`, `mongo-init` | `root` / `rootpass` | Administrador del cluster; solo lo usa el script de inicialización |
| `MONGO_APP_USER` / `MONGO_APP_PASSWORD` | `mongo-init`, `backend` | `b2b_app` / `b2b_app_pass` | Usuario que consume el backend (`readWrite` + `dbAdmin` sobre `b2b`, `clusterMonitor` sobre `admin`) |
| `MONGO_URI` | `backend` | `mongodb://b2b_app:…@mongo1:27017,mongo2:27017/b2b?replicaSet=rs0&authSource=admin` | Cadena de conexión con **los dos nodos**, para que el driver descubra la topología y sobreviva a un failover |

Si necesitas cambiar algún valor (por ejemplo, un puerto ya ocupado en tu máquina), edítalo directamente en `docker-compose.yml` antes de levantar los contenedores. Para sobrescribir solo las credenciales de Mongo, copia `.env.example` a `.env` en esta misma carpeta; no es obligatorio, sin `.env` el stack levanta con los valores por defecto.

### Pasos para levantar el proyecto desde cero

Desde esta carpeta (`Lab 3/`):

```bash
docker compose up --build
```

Un solo comando deja todo operativo, **incluido el replica set** (no hay que ejecutar `rs.initiate()` a mano). El orden lo garantizan las condiciones `depends_on` declaradas en `docker-compose.yml`:

1. Descarga la imagen `postgis/postgis:15-3.3` y levanta la base de datos, ejecutando automáticamente `backendB2B/init.sql` la **primera vez** que se crea el volumen (crea tablas, índices GIST, triggers, stored procedures, vistas materializadas y datos de prueba).
2. **`mongo-keyfile`** genera el keyfile compartido del replica set dentro de un volumen de Docker y lo deja con permisos `400` y dueño `999:999`, como exige `mongod`. El contenedor termina y los nodos esperan a que haya terminado (`service_completed_successfully`).
3. **`mongo1`** y **`mongo2`** arrancan con `--replSet rs0 --keyFile ... --auth`. Sobre `mongo1` el entrypoint oficial crea primero el usuario `root`.
4. **`mongo-init`** espera a que ambos nodos pasen su healthcheck y ejecuta con `mongosh`, en orden: `mongo/rs-init.js` (`rs.initiate()`, espera a que `mongo1` sea PRIMARY y `mongo2` SECONDARY, crea el usuario de aplicación), `mongo/schema-validation.js` (validadores `$jsonSchema`), `mongo/indexes.js` (índices únicos/compuestos/TTL) y `mongo/change-streams-merge.js` (colección materializada `productos_mas_vendidos` + su backfill inicial). El contenedor termina.
5. Compila el backend con Maven dentro de un contenedor multi-stage (`backendB2B/Dockerfile`) y lo levanta en el puerto **8090**. El backend **solo arranca después** de que `mongo-init` terminó bien, así que nunca se encuentra con un replica set a medio configurar.
6. **`worker`** reusa la misma imagen del backend (`b2b-backend:lab3`) con `SPRING_PROFILES_ACTIVE=worker`: arranca **sin servidor HTTP** y se queda escuchando el change stream de `ordenes` para refrescar la vista materializada de productos más vendidos (punto 6 — ver [`docs/06-change-streams-merge.md`](docs/06-change-streams-merge.md)).
7. Compila el frontend con Vite dentro de un contenedor multi-stage (`frontendB2B/Dockerfile`) y lo sirve con Nginx en el puerto **8080**.

La primera vez, los pasos 2–4 toman entre 20 y 60 segundos (la elección de PRIMARY y el *initial sync* del secundario no son instantáneos).

Al arrancar, el backend además carga en segundo plano (sin bloquear el arranque) las geometrías reales de las 52 comunas de la Región Metropolitana y ~2300 unidades vecinales desde un archivo GeoJSON local (no depende de red externa en el camino normal). Esto toma unos segundos — revisa los logs del contenedor `backend` y espera el mensaje `Verificación de geometrías espaciales finalizada` antes de probar el mapa de logística.

Para correrlo en segundo plano en vez de bloquear la terminal:

```bash
docker compose up --build -d
```

### Verificación de que todo quedó arriba

```bash
docker compose ps                          # db, mongo1, mongo2, backend, worker y frontend deben decir "Up"
                                           # mongo-keyfile y mongo-init aparecen como "Exited (0)": es lo esperado
docker compose logs worker | tail -5       # debe decir "Worker de change streams iniciado"
curl http://localhost:8090/api/productos   # 200 (listado público de productos)
curl http://localhost:8090/api/almacenes   # 403 (requiere JWT de un usuario ADMIN) — confirma que el backend responde
curl http://localhost:8090/api/mongo/health   # 200 con el estado del replica set
```

`GET /api/mongo/health` es un endpoint público pensado justamente para validar el despliegue documental sin abrir `mongosh`:

```jsonc
{
  "conectado": true,
  "baseDeDatos": "b2b",
  "latenciaMs": 4,
  "replicaSet": "rs0",
  "miembros": [
    { "host": "mongo1:27017", "estado": "PRIMARY",   "salud": 1.0 },
    { "host": "mongo2:27017", "estado": "SECONDARY", "salud": 1.0 }
  ],
  "transaccionesDisponibles": true,
  "changeStreamsDisponibles": true
}
```

Si `transaccionesDisponibles` o `changeStreamsDisponibles` vienen en `false`, el replica set no quedó bien formado y **nada de lo que depende de él (checkout transaccional, change streams) va a funcionar**.

### Verificación directa del Replica Set con mongosh

```bash
# Estado del set (dentro del contenedor primario)
docker compose exec mongo1 mongosh -u root -p rootpass --authenticationDatabase admin \
  --eval "rs.status().members.map(m => ({ host: m.name, estado: m.stateStr }))"

# Comprobar que el nodo secundario replica de verdad
docker compose exec mongo2 mongosh -u root -p rootpass --authenticationDatabase admin \
  --eval "db.hello().secondary"

# Probar una transacción multi-documento real (falla si no hay replica set)
docker compose exec mongo1 mongosh -u b2b_app -p b2b_app_pass --authenticationDatabase admin \
  --eval 'const s = db.getMongo().startSession();
          s.startTransaction();
          s.getDatabase("b2b").prueba_tx.insertOne({ ok: 1 });
          s.commitTransaction();
          print("transacción OK");
          db.getSiblingDB("b2b").prueba_tx.drop();'
```

Desde el host también se puede conectar directo al primario publicado en el puerto 27017:

```bash
mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&directConnection=true"
```

> **Importante — `directConnection=true`:** los miembros del replica set se anuncian con los nombres internos de Docker (`mongo1:27017`, `mongo2:27017`), que no resuelven desde el host. Por eso, para conectarse desde fuera de Docker (mongosh, MongoDB Compass o el backend corriendo con `mvn spring-boot:run`) hay que usar `directConnection=true`, que evita el descubrimiento de topología. Es el valor por defecto de `mongo.uri` en `application.properties`. Dentro de la red de Docker no aplica: ahí el backend usa la URI con los dos nodos y `?replicaSet=rs0`.

Abrir `http://localhost:8080` en el navegador — debería cargar la pantalla de login. Usuario administrador de prueba (ver seeders en `init.sql`): `admin@ecommerceb2b.cl` (la contraseña está hasheada con BCrypt en el seed; usa el flujo de `POST /usuario/register` para crear tu propio usuario de prueba si no tienes la contraseña original).

### Apagar y limpiar

```bash
docker compose down          # detiene y elimina los contenedores, conserva los datos (volúmenes)
docker compose down -v       # además borra los volúmenes de Postgres y de Mongo (init.sql y rs-init.js
                             # vuelven a correr desde cero, y se regenera el keyfile)
```

### Problemas comunes

- **Puerto ya en uso:** cambia el mapeo de puertos (`"8080:80"`, `"8090:8090"`, `"5433:5432"`, `"27017:27017"`, `"27018:27017"`) en `docker-compose.yml`.
- **El mapa de logística sale vacío justo después de levantar todo:** espera unos segundos — la carga de comunas/unidades vecinales corre en un hilo en segundo plano al arrancar el backend.
- **Cambios en `init.sql` no se reflejan:** el script solo corre en la creación inicial del volumen de Postgres. Si ya existía el volumen, hay que `docker compose down -v` para forzar que se re-ejecute.
- **`mongo-init` termina con error o el backend no arranca:** revisa `docker compose logs mongo-init`. El servicio tiene `restart: on-failure` y `rs-init.js` es idempotente, así que reintenta solo; si persiste, `docker compose down -v && docker compose up --build` reconstruye el replica set desde cero.
- **`MongoServerError: not primary` o `Transaction numbers are only allowed on a replica set member`:** el replica set no se inicializó. Confirma con `/api/mongo/health` y con `docker compose logs mongo-init`.
- **`Permission denied` / `permissions on /etc/mongo/keyfile/mongo-keyfile are too open`:** el keyfile perdió sus permisos `400`. Se resuelve borrando su volumen: `docker compose down -v`. El keyfile **nunca** se monta desde el host (Windows/WSL no preserva `chmod 400`), siempre se genera dentro del volumen `mongo_keyfile`.
- **Conectando desde el host da `getaddrinfo ENOTFOUND mongo1`:** falta `directConnection=true` en la URI (ver la nota de la sección anterior).

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

### 3.9 Infraestructura MongoDB (Lab 3)

- `GET /api/mongo/health` — **público**. Estado del replica set: nodos y su rol, latencia, y si están disponibles transacciones multi-documento y change streams. Ejemplo de respuesta en la sección 2.

### 3.10 Productos más vendidos — vista materializada reactiva (Lab 3, punto 6)

Colección `productos_mas_vendidos`, mantenida al día por el proceso **`worker`** (change stream sobre `ordenes` + `$merge`), no por estos endpoints. Detalle completo del diseño en [`docs/06-change-streams-merge.md`](docs/06-change-streams-merge.md).

- `PATCH /api/ordenes/mongo/{ordenId}/confirmar` — **rol `ADMIN`**. Pasa una orden documental de `PENDIENTE` a `CONFIRMADA`. **Es el disparador**: el worker detecta el cambio y refresca el ranking de forma asíncrona (típicamente en menos de un segundo). El `{ordenId}` es el `ordenId` que devolvió `POST /api/checkout`.
  ```jsonc
  // 200
  {
    "ordenId": "66b6f0c1a2e4f51b3c9d7a04",
    "numeroOrden": "ORD-2026-000482",
    "estado": "CONFIRMADA",
    "mensaje": "Orden confirmada. El worker de change streams actualizará productos_mas_vendidos de forma asíncrona."
  }
  ```
  Responde `409` si la orden no existe o ya no estaba `PENDIENTE`.

- `GET /api/reportes/mongo/productos-mas-vendidos?limite=10` — **rol `ADMIN`**. Lee la vista materializada (un `find()` sobre el índice `ix_masvendidos_unidadesVendidas`, sin agregación en tiempo de request).
  ```jsonc
  // 200
  [
    {
      "_id": 15,
      "productoId": 15,
      "nombreProducto": "Resmas de Papel A4 (Caja de 10)",
      "unidadesVendidas": 340,
      "montoTotalVendido": "6120000.00",
      "ordenesConfirmadas": 12,
      "ultimaVentaEn": "2026-08-09T14:22:31Z",
      "actualizadoEn": "2026-08-09T14:22:31Z"
    }
  ]
  ```

- `POST /api/reportes/mongo/productos-mas-vendidos/recalcular` — **rol `ADMIN`**. Reconstruye el ranking completo con el mismo pipeline `$merge`. Escotilla de emergencia (worker caído más tiempo que la ventana del oplog); en operación normal no hace falta.

- `GET /api/reportes/mongo/volumen-proyectado` — **rol `ADMIN`**. Aggregation pipeline del punto 4 (`$group` + `$bucket` + `$sort`).

---

## 4. Seguridad y roles (RBAC)

- **`CLIENTE`**: crea/gestiona su propio carrito, realiza checkout, ve sus propias órdenes/facturas/direcciones de entrega.
- **`ADMIN`**: gestiona productos/categorías/stock/almacenes, aprueba órdenes, confirma órdenes documentales (`/api/ordenes/mongo/**`), accede a reportes y al mapa de logística.
- Middleware: `Config/JwtAuthenticationFilter.java` valida el JWT en cada request y puebla `SecurityContextHolder` con la autoridad `ROLE_<rol del token>`; `Config/SecurityConfig.java` define qué rutas requieren qué rol.

---

## 5. Integrantes

Grupo 4 — Taller de Base de Datos Diurno 1-2026: María Fuentes, Constanza Viera, Ambar Uzcátegui, Ignacio Ávila, Camilo Cuero.
