# Sistema de Gestión de Tareas Geoespaciales

Sistema completo de gestión de tareas con georreferenciación. Permite crear, editar, eliminar y hacer seguimiento de tareas vinculadas a sectores geográficos, con consultas espaciales avanzadas usando PostGIS.

## Tecnologías Utilizadas

| Capa | Tecnología |
|---|---|
| Frontend | Vue.js 3 (Composition API), Vite, TypeScript |
| Backend | Java 17, Spring Boot 3 (Web, Data JPA, Security) |
| Base de Datos | PostgreSQL 15 + PostGIS 3.3 |
| Seguridad | JWT (stateless) + CSRF Double-Submit Cookie |
| Despliegue | Docker + Docker Compose (Nginx como Proxy Reverso) |

---

## Modelo de Datos

### Diagrama de Relaciones

```
usuarios (1) ──────────< tareas (N) >────────── (1) sectores
```

### Tablas

#### `usuarios`
| Columna | Tipo | Descripción |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `username` | VARCHAR(255) UNIQUE | Nombre de usuario |
| `password` | VARCHAR(255) | Contraseña hasheada con BCrypt |
| `ubicacion_geografica` | geometry(Point, 4326) | Punto geoespacial del usuario (lon, lat) |

**Índice espacial:** `idx_usuarios_ubicacion` (GIST) — acelera las consultas de distancia.

#### `sectores`
| Columna | Tipo | Descripción |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `nombre` | VARCHAR(255) | Nombre del sector (ej: "Construcción", "Semáforos") |
| `ubicacion_espacial` | geometry(Point, 4326) | Punto geoespacial del sector (lon, lat) |

**Índice espacial:** `idx_sectores_ubicacion` (GIST) — acelera los cálculos de radio y distancia.

#### `tareas`
| Columna | Tipo | Descripción |
|---|---|---|
| `id` | BIGSERIAL PK | Identificador único |
| `titulo` | VARCHAR(255) NOT NULL | Título de la tarea |
| `descripcion` | TEXT | Descripción detallada |
| `fecha_vencimiento` | TIMESTAMP NOT NULL | Fecha/hora límite de completar |
| `estado_completada` | BOOLEAN DEFAULT false | Estado de la tarea |
| `usuario_id` | BIGINT FK → usuarios | Propietario de la tarea |
| `sector_id` | BIGINT FK → sectores | Sector geográfico asociado |

**Índices:** `idx_tareas_usuario`, `idx_tareas_sector`, `idx_tareas_estado`, `idx_tareas_fecha`.

---

## Documentación de la API REST

**Base URL local:** `http://localhost:8081/api`  
**Base URL en Docker:** `http://localhost/api`

> Todos los endpoints excepto `/auth/**` y `/usuarios/register` requieren el header:  
> `Authorization: Bearer <jwt_token>`

---

### 🔐 Autenticación (`/api/auth`)

#### `POST /api/auth/login`
Inicia sesión y retorna un JWT.

**Request Body:**
```json
{
  "username": "usuario1",
  "password": "123456"
}
```
**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
**Errores:** `401 Unauthorized` si las credenciales son incorrectas.

---

### 👤 Usuarios (`/api/usuarios`)

#### `POST /api/usuarios/register`
Registra un nuevo usuario con ubicación geoespacial.

**Request Body:**
```json
{
  "username": "nuevo_usuario",
  "password": "mi_password",
  "latitud": -33.4569,
  "longitud": -70.6483
}
```
**Response `200 OK`:**
```json
{
  "id": 4,
  "username": "nuevo_usuario",
  "latitud": -33.4569,
  "longitud": -70.6483
}
```
**Errores:** `400 Bad Request` si el username ya existe o los campos son inválidos.

#### `GET /api/usuarios/me`
Obtiene el perfil del usuario autenticado.

**Response `200 OK`:**
```json
{
  "id": 1,
  "username": "usuario1",
  "latitud": 40.7128,
  "longitud": -74.0060
}
```

#### `PUT /api/usuarios/me`
Actualiza el perfil (username, password y/o ubicación). Requiere la contraseña actual.

**Request Body:**
```json
{
  "username": "nuevo_nombre",
  "currentPassword": "password_actual",
  "newPassword": "nueva_password",
  "latitud": -33.4569,
  "longitud": -70.6483
}
```
**Response `200 OK`:**
```json
{
  "usuario": { "id": 1, "username": "nuevo_nombre", "latitud": -33.4569, "longitud": -70.6483 },
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
> ⚠️ El token en la respuesta es el **nuevo JWT** actualizado con el nuevo username. El frontend debe reemplazar el token guardado.

#### `DELETE /api/usuarios/me`
Elimina la cuenta del usuario autenticado (y en cascada todas sus tareas).

**Request Body:**
```json
{ "password": "mi_password" }
```
**Response `204 No Content`.**

---

### ✅ Tareas (`/api/tareas`)

#### `GET /api/tareas`
Lista las tareas del usuario autenticado. Soporta filtros opcionales via query params.

| Param | Tipo | Descripción |
|---|---|---|
| `estado` | `true` / `false` | Filtra por completada (true) o pendiente (false) |
| `keyword` | `string` | Busca en título y descripción (case-insensitive) |

Ejemplos:
- `GET /api/tareas` — todas las tareas
- `GET /api/tareas?estado=false` — solo pendientes
- `GET /api/tareas?keyword=parque` — que contengan "parque"
- `GET /api/tareas?estado=true&keyword=semáforo` — completadas que contengan "semáforo"

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "titulo": "Reparar acera",
    "descripcion": "Parchar agujeros en la calle principal",
    "fechaVencimiento": "2026-07-20T10:00:00",
    "estadoCompletada": false,
    "sectorId": 1,
    "sectorNombre": "Construcción Centro",
    "usuarioId": 1
  }
]
```

#### `GET /api/tareas/{id}`
Obtiene una tarea específica (solo si pertenece al usuario autenticado).

#### `POST /api/tareas`
Crea una nueva tarea.

**Request Body:**
```json
{
  "titulo": "Cambiar semáforo",
  "descripcion": "Reemplazar semáforo dañado en la esquina norte",
  "fechaVencimiento": "2026-07-25T08:00:00",
  "sectorId": 2
}
```

#### `PUT /api/tareas/{id}`
Actualiza una tarea existente. Acepta los mismos campos que `POST`.

#### `DELETE /api/tareas/{id}`
Elimina una tarea. **Response `204 No Content`.**

#### `PATCH /api/tareas/{id}/completar`
Alterna el estado `estadoCompletada` (toggle). Si era `false` pasa a `true` y viceversa.

#### `GET /api/tareas/notificaciones`
Retorna las tareas **pendientes** cuya `fechaVencimiento` vence en las próximas 24 horas.

---

### 📊 Estadísticas Geoespaciales (`/api/tareas/estadisticas`)

#### `GET /api/tareas/estadisticas/por-sector`
**Q1:** Cuántas tareas ha hecho el usuario autenticado, agrupadas por sector.

**Response `200 OK`:**
```json
[
  { "sector": "Construcción Centro", "total": 3 },
  { "sector": "Reparación Semáforos Norte", "total": 1 }
]
```

#### `GET /api/tareas/estadisticas/mas-cercana`
**Q2:** La tarea pendiente más cercana al usuario (según la ubicación del sector).

**Response `200 OK`:**
```json
{
  "id": 2,
  "titulo": "Cambiar poste de luz",
  "descripcion": "Cambiar poste dañado en esquina",
  "sectorNombre": "Reparación Semáforos Norte",
  "distanciaMetros": 234.5
}
```

#### `GET /api/tareas/estadisticas/sector-radio-2km`
**Q3:** El sector con más tareas completadas dentro de un radio de 2 km del usuario.

**Response `200 OK`:**
```json
{ "sector": "Construcción Centro", "total": 2 }
```

#### `GET /api/tareas/estadisticas/promedio-distancia`
**Q4 / Q8:** Promedio de distancia (en metros) entre las tareas completadas del usuario y su ubicación registrada.

**Response `200 OK`:**
```json
{ "promedioMetros": 187.43 }
```

#### `GET /api/tareas/estadisticas/sectores-pendientes`
**Q5:** Concentración espacial de tareas pendientes usando agrupación `ST_ClusterDBSCAN`. Los sectores geográficamente cercanos entre sí se agrupan en un mismo "grupo espacial".

**Response `200 OK`:**
```json
[
  { "sector": "Grupo espacial 1: Construcción Centro, Limpieza Vías Este", "total": 5 },
  { "sector": "Grupo espacial 2: Servicios Públicos Oeste", "total": 2 }
]
```

#### `GET /api/tareas/estadisticas/por-usuario-sector`
**Q6:** Cuántas tareas ha realizado **cada usuario del sistema** en cada sector (vista global, sin filtrar por usuario autenticado).

**Response `200 OK`:**
```json
[
  { "usuario": "usuario1", "sector": "Construcción Centro", "total": 2 },
  { "usuario": "usuario2", "sector": "Limpieza Vías Este", "total": 1 }
]
```

#### `GET /api/tareas/estadisticas/sector-radio-5km`
**Q7:** El sector con más tareas completadas dentro de un radio de 5 km del usuario.

---

### 🗺️ Sectores (`/api/sectores`)

#### `GET /api/sectores`
Lista todos los sectores disponibles.

**Response `200 OK`:**
```json
[
  { "id": 1, "nombre": "Construcción Centro", "latitud": 40.7130, "longitud": -74.0065 }
]
```

#### `POST /api/sectores`
Crea un nuevo sector georreferenciado.

**Request Body:**
```json
{
  "nombre": "Reparación Calles Sur",
  "latitud": -33.5000,
  "longitud": -70.7000
}
```

#### `PUT /api/sectores/{id}`
Actualiza un sector existente. Acepta los mismos campos que `POST`.

#### `DELETE /api/sectores/{id}`
Elimina un sector. **Response `204 No Content`.**

---

## Consultas Espaciales PostGIS — Implementación Técnica

### Q2 — Tarea pendiente más cercana (`ST_Distance`)

```sql
SELECT t.id, t.titulo, ...,
       ST_Distance(
           geography(s.ubicacion_espacial),
           geography(u.ubicacion_geografica)
       ) AS distancia_metros
FROM tareas t
JOIN sectores s ON t.sector_id = s.id
JOIN usuarios u ON u.id = :usuarioId
WHERE t.estado_completada = false AND t.usuario_id = :usuarioId
ORDER BY ST_Distance(
    geography(s.ubicacion_espacial),
    geography(u.ubicacion_geografica)
)
LIMIT 1
```

**`ST_Distance(geography, geography)`** calcula la distancia geodésica real en **metros** sobre la superficie terrestre, sin distorsiones de proyección. El cast a `geography` es clave para obtener metros en lugar de grados.

---

### Q3 y Q7 — Sector con más tareas en radio X (`ST_DWithin`)

```sql
SELECT s.nombre, COUNT(t.id) AS total
FROM tareas t
JOIN sectores s ON t.sector_id = s.id
JOIN usuarios u ON u.id = :usuarioId
WHERE t.estado_completada = true
  AND ST_DWithin(
      geography(s.ubicacion_espacial),
      geography(u.ubicacion_geografica),
      2000   -- metros (5000 para Q7)
  )
GROUP BY s.id, s.nombre
ORDER BY total DESC
LIMIT 1
```

**`ST_DWithin(geography, geography, distancia_metros)`** retorna `true` si los dos puntos están dentro de la distancia especificada. Es más eficiente que `ST_Distance(...) < X` porque aprovecha el índice espacial GIST.

---

### Q4 / Q8 — Promedio de distancia (`AVG + ST_Distance`)

```sql
SELECT AVG(ST_Distance(
    geography(s.ubicacion_espacial),
    geography(u.ubicacion_geografica)
))
FROM tareas t
JOIN sectores s ON t.sector_id = s.id
JOIN usuarios u ON u.id = :usuarioId
WHERE t.estado_completada = true AND t.usuario_id = :usuarioId
```

**`AVG(ST_Distance(...))`** agrega la función de distancia con el promedio estándar de SQL, calculando el promedio en metros de todas las tareas completadas respecto al punto del usuario.

---

### Q5 — Concentración espacial (`ST_ClusterDBSCAN`)

```sql
WITH pendientes AS (
    SELECT t.id, s.nombre, s.ubicacion_espacial
    FROM tareas t
    JOIN sectores s ON t.sector_id = s.id
    WHERE t.estado_completada = false
),
clusters AS (
    SELECT id, nombre,
           ST_ClusterDBSCAN(
               ST_Transform(ubicacion_espacial, 3857),
               eps := 250,
               minpoints := 1
           ) OVER () AS cluster_id
    FROM pendientes
)
SELECT 'Grupo espacial ' || (cluster_id + 1) || ': ' ||
       STRING_AGG(DISTINCT nombre, ', ' ORDER BY nombre) AS sector,
       COUNT(id) AS total
FROM clusters
GROUP BY cluster_id
ORDER BY total DESC
```

**`ST_ClusterDBSCAN`** es una función de ventana que implementa el algoritmo DBSCAN para agrupar geometrías cercanas. `ST_Transform(..., 3857)` proyecta a Web Mercator (metros) para que el parámetro `eps := 250` signifique 250 metros de radio de agrupación.

---

## Seguridad Implementada

### JWT (JSON Web Tokens)
- Autenticación **stateless**: el servidor no guarda sesiones.
- El token se genera en `/auth/login` y expira en 24 horas (configurable con `JWT_EXPIRATION`).
- Cada request protegido debe incluir `Authorization: Bearer <token>`.
- Implementado en [`JwtUtil.java`](./control2_backend/src/main/java/com/example/control2_backend/security/JwtUtil.java) y [`JwtAuthFilter.java`](./control2_backend/src/main/java/com/example/control2_backend/security/JwtAuthFilter.java).

### CSRF (Cross-Site Request Forgery)
- Patrón **Double-Submit Cookie**: Spring genera una cookie `XSRF-TOKEN` legible por JavaScript.
- Axios lee automáticamente esa cookie y la envía como header `X-XSRF-TOKEN` en cada petición mutante (POST/PUT/DELETE/PATCH).
- Un atacante de otro dominio no puede leer la cookie (Same-Origin Policy), por lo que no puede falsificar el header → bloqueado.
- Implementado en [`CsrfCookieFilter.java`](./control2_backend/src/main/java/com/example/control2_backend/security/CsrfCookieFilter.java) y configurado en [`SecurityConfig.java`](./control2_backend/src/main/java/com/example/control2_backend/config/SecurityConfig.java).

### Protección contra SQL Injection
- Todas las consultas usan **parámetros nombrados** (`@Param`) con Spring Data JPA / JPQL y queries nativas con `?` o `:param`. No se construye ninguna query concatenando strings de entrada del usuario.

### Validación de Entrada (Bean Validation)
- Todos los DTOs tienen anotaciones `@NotBlank`, `@NotNull`, `@DecimalMin`, `@DecimalMax`.
- Los controllers usan `@Valid` para activar la validación automáticamente.
- El [`GlobalExceptionHandler`](./control2_backend/src/main/java/com/example/control2_backend/config/GlobalExceptionHandler.java) captura `MethodArgumentNotValidException` y retorna un `400 Bad Request` con los mensajes de error detallados.

---

## Estructura del Proyecto

```
Control 2/
├── docker-compose.yml          # Orquesta DB + Backend + Frontend
├── README.md
├── control2_backend/           # Spring Boot API
│   ├── Dockerfile
│   ├── src/main/java/.../
│   │   ├── config/             # SecurityConfig, GlobalExceptionHandler
│   │   ├── controller/         # TareaController, SectorController, UsuarioController
│   │   │   └── Login/          # AuthController
│   │   ├── dtos/               # DTOs con validaciones Bean Validation
│   │   ├── entity/             # TareaEntity, SectorEntity, UsuarioEntity (JTS Point)
│   │   ├── repository/         # JPA Repositories + consultas PostGIS nativas
│   │   ├── security/           # JwtUtil, JwtAuthFilter, CsrfCookieFilter
│   │   └── service/            # TareaService, SectorService, UsuarioService
│   └── src/main/resources/
│       ├── application.properties
│       └── data.sql            # DDL + datos iniciales (PostGIS)
└── control2_frontend/          # Vue.js 3 + TypeScript
    ├── Dockerfile
    ├── nginx.conf              # Proxy reverso → backend
    └── src/
        ├── components/         # TareaCard, StatCard, BarChart, AlertBanner
        ├── router/             # Rutas protegidas (requiresAuth)
        ├── services/api.ts     # Cliente Axios con CSRF e interceptores JWT
        ├── stores/auth.ts      # Pinia store de autenticación
        └── views/              # DashboardView, EstadisticasView, SectoresView,
                                # LoginView, RegisterView, PerfilView
```

---

## Instrucciones para Configurar y Desplegar

### Requisitos Previos
- **Docker** y **Docker Compose** instalados.
- Puertos **80**, **8080** y **5432** libres.

### Despliegue en Producción (Docker)

```bash
# 1. Clonar el repositorio y navegar a la carpeta raíz
cd "Control 2"

# 2. Construir imágenes y levantar contenedores
docker-compose up --build

# 3. Acceder a la aplicación
# → http://localhost  (frontend)
# → http://localhost:8080/api  (backend directo, opcional)
```

Docker levanta automáticamente:
1. **PostgreSQL + PostGIS** — inicializa la BD con tablas e índices espaciales.
2. **Spring Boot** — compila y arranca la API (espera a que la DB esté lista).
3. **Nginx + Vue.js** — sirve el frontend y redirige `/api/*` al backend.

### Desarrollo Local (sin Docker)

**Backend:**
```bash
cd control2_backend
# Crear un .env con las variables (ver .env.example)
./mvnw spring-boot:run
# API disponible en http://localhost:8081/api
```

**Frontend:**
```bash
cd control2_frontend
npm install
npm run dev
# App disponible en http://localhost:5173
```

### Variables de Entorno

| Variable | Default | Descripción |
|---|---|---|
| `DB_HOST` | `localhost` | Host de PostgreSQL |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `control2_bd` | Nombre de la base de datos |
| `DB_USER` | `postgres` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `postgres` | Contraseña de PostgreSQL |
| `JWT_SECRET` | *(requerido)* | Clave secreta para firmar JWT (mín. 32 chars) |
| `JWT_EXPIRATION` | `86400000` | Expiración del JWT en milisegundos (24h) |
| `MAIL_ENABLED` | `false` | Activa notificaciones por correo |
| `MAIL_USERNAME` | — | Cuenta de correo SMTP |
| `MAIL_PASSWORD` | — | Contraseña de la cuenta SMTP |

### Usuarios de Prueba (datos iniciales)
| Username | Password | Ubicación |
|---|---|---|
| `usuario1` | `123456` | New York (40.7128, -74.0060) |
| `usuario2` | `123456` | New York (40.7140, -74.0070) |
| `usuario3` | `123456` | New York (40.7115, -74.0050) |
