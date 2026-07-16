# Documentación de Implementación

Sistema de Gestión de Tareas Geoespaciales — Control 2

---

## Índice

1. [Visión General de la Arquitectura](#1-visión-general-de-la-arquitectura)
2. [Backend — Spring Boot](#2-backend--spring-boot)
3. [Base de Datos — PostgreSQL + PostGIS](#3-base-de-datos--postgresql--postgis)
4. [Consultas Geoespaciales PostGIS](#4-consultas-geoespaciales-postgis)
5. [Seguridad](#5-seguridad)
6. [Frontend — Vue.js 3](#6-frontend--vuejs-3)
7. [Comunicación Frontend ↔ Backend](#7-comunicación-frontend--backend)
8. [Despliegue — Docker y Nginx](#8-despliegue--docker-y-nginx)

---

## 1. Visión General de la Arquitectura

La aplicación sigue una arquitectura de **tres capas** completamente desacopladas y comunicadas por HTTP:

```
┌─────────────────────────────────────────────────────┐
│                  NAVEGADOR (Cliente)                 │
│         Vue.js 3 SPA — Puerto 80 (Docker)           │
│         Puerto 5173 (desarrollo local)               │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP/REST + JSON
                       │ JWT en header Authorization
                       │ CSRF token en cookie + header
                       ▼
┌─────────────────────────────────────────────────────┐
│              NGINX (Proxy Reverso)                   │
│   /          → sirve archivos estáticos Vue          │
│   /api/*     → reenvía al backend Spring Boot        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│           BACKEND — Spring Boot 3                    │
│                  Puerto 8080                         │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Controllers  │→ │  Services    │→ │Repository │ │
│  │ (REST API)   │  │ (lógica)     │  │ (JPA+SQL) │ │
│  └──────────────┘  └──────────────┘  └─────┬─────┘ │
│                                             │       │
│  ┌─────────────────────────────────────────┘       │
│  │ Spring Security (JWT + CSRF)                     │
│  └──────────────────────────────────────────────────┘
└──────────────────────┬──────────────────────────────┘
                       │ JDBC / Hibernate Spatial
                       ▼
┌─────────────────────────────────────────────────────┐
│         PostgreSQL 15 + PostGIS 3.3                  │
│              Puerto 5432                             │
│   Tablas: usuarios, sectores, tareas                 │
│   Tipos:  geometry(Point, 4326)                      │
│   Índices: GIST espaciales                           │
└─────────────────────────────────────────────────────┘
```

---

## 2. Backend — Spring Boot

### Estructura de paquetes

```
com.example.control2_backend/
├── Control2BackendApplication.java   ← Punto de entrada (@SpringBootApplication)
├── config/
│   ├── SecurityConfig.java           ← CORS, CSRF, JWT, reglas de autorización
│   └── GlobalExceptionHandler.java   ← Manejo centralizado de errores HTTP
├── controller/
│   ├── TareaController.java          ← /api/tareas + /api/tareas/estadisticas/*
│   ├── SectorController.java         ← /api/sectores
│   ├── UsuarioController.java        ← /api/usuarios
│   └── Login/
│       └── AuthController.java       ← /api/auth/login
├── dtos/
│   ├── TareaDto.java                 ← Validaciones @NotBlank, @NotNull
│   ├── SectorDto.java                ← Validaciones @DecimalMin/@DecimalMax
│   ├── RegisterRequestDto.java
│   ├── UserUpdateRequestDto.java
│   ├── UserUpdateResponseDto.java
│   ├── DeleteAccountRequestDto.java
│   └── Login/
│       ├── LoginRequestDto.java
│       └── AuthResponseDto.java
├── entity/
│   ├── UsuarioEntity.java            ← Mapea tabla 'usuarios', campo JTS Point
│   ├── SectorEntity.java             ← Mapea tabla 'sectores', campo JTS Point
│   └── TareaEntity.java             ← Mapea tabla 'tareas', FK a usuario y sector
├── repository/
│   ├── UsuarioRepository.java        ← findByUsername
│   ├── SectorRepository.java         ← findByNombre
│   └── TareaRepository.java          ← CRUD + 7 consultas PostGIS nativas
├── security/
│   ├── JwtUtil.java                  ← Genera y valida tokens JWT (JJWT 0.12.5)
│   ├── JwtAuthFilter.java            ← Intercepta cada request, extrae el JWT
│   ├── CsrfCookieFilter.java         ← Materializa el token CSRF en cada response
│   └── UserDetailsServiceImpl.java   ← Carga usuario de BD para Spring Security
└── service/
    ├── TareaService.java             ← Lógica de tareas + mapeo de consultas PostGIS
    ├── SectorService.java            ← Lógica de sectores + construcción de Point JTS
    └── UsuarioService.java           ← Registro, actualización, eliminación + BCrypt
```

### Patrón de capas

Cada funcionalidad sigue el flujo:

```
Request HTTP
    ↓
Controller  →  valida @Valid, extrae @AuthenticationPrincipal
    ↓
Service     →  lógica de negocio, acceso a datos, conversión Entity↔DTO
    ↓
Repository  →  JPA / consultas SQL nativas PostGIS
    ↓
Base de Datos
```

Los **DTOs** (Data Transfer Objects) separan la representación de la API del modelo interno. Por ejemplo, `TareaDto` expone `sectorNombre` (string legible) aunque en la entidad `TareaEntity` solo existe la relación `@ManyToOne` al `SectorEntity`.

### Representación geoespacial con JTS

Para almacenar puntos geoespaciales se usa la librería **JTS (Java Topology Suite)** integrada con Hibernate Spatial:

```java
// UsuarioEntity.java
@Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
private Point ubicacionGeografica;
```

```java
// UsuarioService.java — construcción del punto
private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

Point point = GF.createPoint(new Coordinate(dto.getLongitud(), dto.getLatitud()));
// Importante: JTS usa Coordinate(x, y) = Coordinate(longitud, latitud)
```

El SRID `4326` corresponde al sistema WGS 84, el estándar global (el mismo que usa GPS, Google Maps, OpenStreetMap).

---

## 3. Base de Datos — PostgreSQL + PostGIS

### Modelo Entidad-Relación

```
┌─────────────────┐          ┌───────────────────┐
│    USUARIOS     │          │      SECTORES      │
├─────────────────┤          ├───────────────────┤
│ id (PK)         │          │ id (PK)           │
│ username UNIQUE │          │ nombre            │
│ password BCrypt │          │ ubicacion_espacial│
│ ubicacion_geo   │          │  geometry(Pt,4326)│
│  geometry(Pt,   │          └────────┬──────────┘
│  4326)          │                   │ 1
└────────┬────────┘                   │
         │ 1                          │
         │                  ┌─────────┴──────────┐
         └──────────────────┤       TAREAS        │
                         N  ├────────────────────┤
                            │ id (PK)             │
                            │ titulo              │
                            │ descripcion         │
                            │ fecha_vencimiento   │
                            │ estado_completada   │
                            │ usuario_id (FK)     │
                            │ sector_id (FK)      │
                            └────────────────────┘
```

### Índices espaciales GIST

Los índices espaciales tipo **GIST** (Generalized Search Tree) son fundamentales para el rendimiento de las consultas PostGIS. Sin ellos, funciones como `ST_Distance` y `ST_DWithin` harían un escaneo completo de la tabla.

```sql
CREATE INDEX idx_usuarios_ubicacion ON usuarios USING GIST(ubicacion_geografica);
CREATE INDEX idx_sectores_ubicacion ON sectores USING GIST(ubicacion_espacial);
```

Un índice GIST organiza las geometrías en un árbol R-Tree que permite encontrar rápidamente qué objetos están dentro de un bounding box, lo cual es la operación base de `ST_DWithin`.

### Inicialización automática

Spring Boot ejecuta `data.sql` en cada arranque (`spring.sql.init.mode=always`). El script usa `CREATE TABLE IF NOT EXISTS` e `INSERT ... ON CONFLICT DO NOTHING` para ser **idempotente**: puede ejecutarse múltiples veces sin error ni duplicados.

---

## 4. Consultas Geoespaciales PostGIS

Todas las consultas están en `TareaRepository.java` como `@Query(nativeQuery = true, ...)` y son ejecutadas por `TareaService.java`.

### El cast `geography` vs `geometry`

En PostGIS existen dos tipos de datos espaciales:

| Tipo | Sistema | Distancia en |
|---|---|---|
| `geometry` | Plano cartesiano | Grados (°) |
| `geography` | Esfera terrestre | Metros (m) |

Todas las consultas usan el cast `geography(columna)` para obtener distancias reales en metros, independientemente de dónde esté el usuario en el planeta.

---

### Q1 — Tareas por sector del usuario

```sql
SELECT s.nombre, COUNT(t.id) AS total
FROM tareas t
JOIN sectores s ON t.sector_id = s.id
WHERE t.usuario_id = :usuarioId
GROUP BY s.id, s.nombre
ORDER BY total DESC
```

Consulta SQL estándar sin funciones espaciales. Agrupa por sector y cuenta cuántas tareas tiene el usuario en cada uno.

---

### Q2 — Tarea pendiente más cercana (`ST_Distance`)

```sql
SELECT t.id, t.titulo, t.descripcion, t.fecha_vencimiento, t.estado_completada,
       t.sector_id, t.usuario_id, s.nombre AS sector_nombre,
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

**`ST_Distance(geography, geography)`** calcula la distancia geodésica en metros entre dos puntos sobre la esfera terrestre. Se ordena ascendentemente para que el `LIMIT 1` retorne el más cercano. La distancia se calcula entre la **ubicación del sector** (donde se realiza la tarea) y la **ubicación registrada del usuario**.

---

### Q3 — Sector con más tareas completadas en radio 2 km (`ST_DWithin`)

```sql
SELECT s.nombre, COUNT(t.id) AS total
FROM tareas t
JOIN sectores s ON t.sector_id = s.id
JOIN usuarios u ON u.id = :usuarioId
WHERE t.estado_completada = true
  AND ST_DWithin(
      geography(s.ubicacion_espacial),
      geography(u.ubicacion_geografica),
      2000   -- metros
  )
GROUP BY s.id, s.nombre
ORDER BY total DESC
LIMIT 1
```

**`ST_DWithin(geography, geography, metros)`** retorna `true` si dos geometrías están dentro de la distancia indicada. Es **más eficiente que** `ST_Distance(...) < 2000` porque puede usar el índice espacial GIST para predescartar candidatos antes de calcular la distancia exacta.

La misma lógica se aplica en Q7 cambiando `2000` por `5000` metros.

---

### Q4/Q8 — Promedio de distancia de tareas completadas (`AVG + ST_Distance`)

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

Combina la función de agregación estándar `AVG()` con `ST_Distance()` para calcular el promedio de distancias. El resultado está en metros. En el frontend se formatea a metros o kilómetros según el valor.

---

### Q5 — Concentración espacial con DBSCAN (`ST_ClusterDBSCAN`)

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

**`ST_ClusterDBSCAN`** implementa el algoritmo de agrupamiento **DBSCAN** (Density-Based Spatial Clustering of Applications with Noise) como función de ventana de PostgreSQL.

Parámetros usados:
- **`eps := 250`** — radio de vecindad en metros. Dos puntos están en el mismo cluster si están a menos de 250 m entre sí.
- **`minpoints := 1`** — mínimo de puntos para formar un cluster. Con valor 1, ningún punto queda como "ruido" (outlier).
- **`ST_Transform(geom, 3857)`** — proyecta de WGS84 (grados) a Web Mercator (metros planos) para que `eps` signifique metros reales.

El resultado agrupa sectores geográficamente cercanos bajo un mismo "Grupo espacial N".

---

### Q6 — Tareas por cada usuario y sector (vista global)

```sql
SELECT u.username, s.nombre, COUNT(t.id) AS total
FROM tareas t
JOIN usuarios u ON t.usuario_id = u.id
JOIN sectores s ON t.sector_id = s.id
GROUP BY u.id, u.username, s.id, s.nombre
ORDER BY u.username, s.nombre
```

Esta consulta **no filtra por usuario autenticado** — retorna datos de todos los usuarios del sistema. En el frontend se muestra como una tabla cruzada (usuario × sector).

---

## 5. Seguridad

### JWT (JSON Web Tokens)

El flujo de autenticación es completamente **stateless** — el servidor no almacena sesiones:

```
1. Cliente    →  POST /api/auth/login { username, password }
2. Backend    →  AuthenticationManager.authenticate()
                 → UserDetailsServiceImpl.loadUserByUsername()
                 → BCryptPasswordEncoder.matches(raw, hashed)
3. Backend    →  JwtUtil.generateToken(username)
                 → Jwts.builder().subject(username).expiration(24h).signWith(HMAC-SHA256)
4. Cliente    ←  { "token": "eyJhbGci..." }
5. Cliente    →  localStorage.setItem('token', ...)
6. Siguientes →  Authorization: Bearer eyJhbGci...
   requests      ↓
                 JwtAuthFilter.doFilterInternal()
                 → extrae token del header
                 → JwtUtil.extractUsername()
                 → JwtUtil.isTokenValid() (verifica firma + expiración)
                 → SecurityContextHolder.setAuthentication()
```

La implementación usa la librería **JJWT 0.12.5** con algoritmo HMAC-SHA256. La clave secreta se carga desde la variable de entorno `JWT_SECRET` vía `@Value("${jwt.secret}")`.

### CSRF — Double-Submit Cookie

El patrón Double-Submit Cookie protege contra ataques CSRF incluso con JWT:

```
1. Backend  →  CsrfCookieFilter materializa el token en CADA respuesta:
               cookie: XSRF-TOKEN=abc123; Path=/; SameSite=Lax
               (httpOnly=false → JavaScript puede leerla)

2. Axios    →  lee automáticamente document.cookie['XSRF-TOKEN']
               y lo envía en el header de peticiones mutantes:
               X-XSRF-TOKEN: abc123

3. Spring   →  CookieCsrfTokenRepository compara:
               cookie XSRF-TOKEN == header X-XSRF-TOKEN ?
               → Sí: petición legítima ✓
               → No: 403 Forbidden ✗

Ataque CSRF:
   Un atacante en otro dominio puede forzar al navegador a enviar
   la cookie, pero NO puede leerla (Same-Origin Policy).
   Sin poder leer la cookie, no puede poner el header X-XSRF-TOKEN correcto.
   → El ataque es bloqueado.
```

En `api.ts` la configuración de Axios es:
```typescript
xsrfCookieName: 'XSRF-TOKEN',   // nombre de la cookie que leer
xsrfHeaderName: 'X-XSRF-TOKEN', // nombre del header donde enviarlo
withCredentials: true,           // permite enviar cookies cross-origin
```

Los endpoints públicos (`/api/auth/**` y `/api/usuarios/register`) están excluidos de la protección CSRF porque el cliente aún no tiene la cookie cuando hace esas primeras peticiones.

### Protección contra SQL Injection

Todas las consultas usan parámetros nombrados con Spring Data JPA:

```java
// Correcto — parámetro vinculado, nunca concatenado:
@Query("SELECT t FROM TareaEntity t WHERE t.usuario.id = :usuarioId ...")
List<TareaEntity> findByUsuarioId(@Param("usuarioId") Long usuarioId);

// En consultas nativas:
@Query(nativeQuery = true, value = "SELECT ... WHERE u.id = :usuarioId")
```

Ninguna consulta construye SQL concatenando strings de entrada del usuario.

### Bean Validation

Los DTOs tienen anotaciones de validación de `jakarta.validation`:

```java
// TareaDto.java
@NotBlank(message = "El título de la tarea es obligatorio")
private String titulo;

@NotNull(message = "El sector es obligatorio")
private Long sectorId;

// SectorDto.java
@DecimalMin(value = "-90.0", message = "Latitud debe estar entre -90 y 90")
@DecimalMax(value = "90.0",  message = "Latitud debe estar entre -90 y 90")
private Double latitud;
```

Los controllers activan la validación con `@Valid`:
```java
public ResponseEntity<TareaDto> create(@Valid @RequestBody TareaDto dto, ...)
```

Si la validación falla, `GlobalExceptionHandler` captura `MethodArgumentNotValidException` y retorna un `400 Bad Request` con los mensajes de error campo por campo:
```json
{
  "status": 400,
  "error": "Validación fallida",
  "detalles": {
    "titulo": "El título de la tarea es obligatorio",
    "sectorId": "El sector es obligatorio"
  }
}
```

---

## 6. Frontend — Vue.js 3

### Tecnologías y decisiones

| Librería | Uso |
|---|---|
| Vue 3 Composition API | Framework base, `<script setup>` en todos los componentes |
| Vite | Build tool (reemplaza webpack, mucho más rápido) |
| TypeScript | Tipado estático en servicios, stores y componentes |
| Vue Router 4 | Navegación SPA con guards de autenticación |
| Pinia | State management (reemplaza Vuex en Vue 3) |
| Axios | Cliente HTTP con interceptores |
| @iconify/vue | Iconos vectoriales (lucide set) |

### Estructura de vistas

| Vista | Ruta | Descripción |
|---|---|---|
| `LoginView.vue` | `/login` | Formulario de inicio de sesión |
| `RegisterView.vue` | `/register` | Registro con latitud/longitud o geolocalización del browser |
| `DashboardView.vue` | `/dashboard` | CRUD de tareas, filtros, notificaciones |
| `EstadisticasView.vue` | `/estadisticas` | Dashboard con las 7 consultas geoespaciales |
| `SectoresView.vue` | `/sectores` | CRUD de sectores + estadísticas de uso |
| `PerfilView.vue` | `/perfil` | Editar cuenta, cambiar contraseña, eliminar cuenta |

### Componentes reutilizables

| Componente | Props | Evento | Uso |
|---|---|---|---|
| `TareaCard.vue` | `tarea` | `@toggle`, `@edit`, `@delete` | Tarjeta de tarea en el dashboard |
| `StatCard.vue` | `label`, `sublabel`, `icon`, `accent` | — | Card de métrica en estadísticas |
| `BarChart.vue` | `items[]`, `color`, `emptyMessage` | — | Gráfico de barras horizontales |
| `AlertBanner.vue` | `type`, `message` | `@close` | Banner de alerta/éxito |

### Estado global con Pinia (`auth.ts`)

El store de autenticación maneja:
- `token` — JWT almacenado en `localStorage`
- `user` — objeto `{id, username, latitud, longitud}` en `localStorage`
- `isAuthenticated` — computed que verifica que el token existe Y no está expirado (decodifica el payload JWT en el cliente para revisar el campo `exp`)
- `clearExpiredSession()` — llamado en cada navegación por el router guard

```typescript
// Verificación de expiración en el cliente (sin llamar al backend)
function isTokenExpired(rawToken: string | null) {
  const payload = JSON.parse(atob(rawToken.split('.')[1]))
  return payload.exp * 1000 <= Date.now()
}
```

### Guards de navegación (router)

```typescript
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  auth.clearExpiredSession()               // limpia tokens vencidos

  if (to.meta.requiresAuth && !auth.isAuthenticated)
    return '/login'                        // redirige si no autenticado

  if (to.meta.requiresGuest && auth.isAuthenticated)
    return '/dashboard'                    // redirige si ya logueado
})
```

### Sistema de notificaciones

La app implementa notificaciones in-app mediante **polling**:

1. `App.vue` inicia un `setInterval` de 60 segundos al detectar que el usuario está autenticado (via `watch(() => auth.isAuthenticated)`)
2. Cada 60s llama a `GET /api/tareas/notificaciones`
3. El backend retorna tareas pendientes con `fechaVencimiento` entre `now` y `now + 24h`
4. Si hay nuevas notificaciones, se muestra un menú desplegable con la campana en el navbar
5. `DashboardView.vue` tiene además un panel de alertas dedicado y muestra en rojo las tareas ya vencidas

---

## 7. Comunicación Frontend ↔ Backend

### Cliente HTTP — `services/api.ts`

Todas las llamadas HTTP pasan por una instancia centralizada de Axios:

```typescript
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,       // envía cookies cross-origin (necesario para CSRF)
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})
```

**Interceptor de request** — añade el JWT a cada petición:
```typescript
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

**Interceptor de response** — maneja errores automáticamente:
- `403` en petición mutante → reintenta una vez con el nuevo token CSRF
- `401` → limpia `localStorage` y redirige a `/login`

### Módulos de la API exportados

```typescript
authApi.login(username, password)           → POST /auth/login
authApi.register(username, password, lat, lon) → POST /usuarios/register
authApi.me()                                → GET  /usuarios/me
authApi.updateProfile(data)                 → PUT  /usuarios/me
authApi.deleteAccount(password)             → DELETE /usuarios/me

tareasApi.getAll(params?)                   → GET  /tareas?estado=&keyword=
tareasApi.create(data)                      → POST /tareas
tareasApi.update(id, data)                  → PUT  /tareas/:id
tareasApi.delete(id)                        → DELETE /tareas/:id
tareasApi.toggleCompletada(id)              → PATCH /tareas/:id/completar
tareasApi.getNotificaciones()               → GET  /tareas/notificaciones
tareasApi.estadisticas.porSector()          → GET  /tareas/estadisticas/por-sector
tareasApi.estadisticas.masCercana()         → GET  /tareas/estadisticas/mas-cercana
tareasApi.estadisticas.sectorRadio2km()     → GET  /tareas/estadisticas/sector-radio-2km
tareasApi.estadisticas.promedioDistancia()  → GET  /tareas/estadisticas/promedio-distancia
tareasApi.estadisticas.sectoresPendientes() → GET  /tareas/estadisticas/sectores-pendientes
tareasApi.estadisticas.porUsuarioSector()   → GET  /tareas/estadisticas/por-usuario-sector
tareasApi.estadisticas.sectorRadio5km()     → GET  /tareas/estadisticas/sector-radio-5km

sectoresApi.getAll()                        → GET  /sectores
sectoresApi.create(data)                    → POST /sectores
sectoresApi.update(id, data)                → PUT  /sectores/:id
sectoresApi.delete(id)                      → DELETE /sectores/:id
```

---

## 8. Despliegue — Docker y Nginx

### Dockerfiles multi-stage

Ambos Dockerfiles usan **builds multi-etapa** para mantener imágenes de producción pequeñas:

**Backend** (`control2_backend/Dockerfile`):
```
Etapa 1: maven:3.9.6-eclipse-temurin-17
  → mvn dependency:go-offline  (cache de dependencias)
  → mvn clean package -DskipTests
  → genera: target/control2_backend-0.0.1-SNAPSHOT.jar

Etapa 2: eclipse-temurin:17-jre-alpine  (imagen mínima, solo JRE)
  → COPY --from=build /app/target/*.jar app.jar
  → ENTRYPOINT ["java", "-jar", "app.jar"]
```

La imagen final solo contiene el JRE Alpine + el JAR. No incluye Maven, el código fuente, ni las dependencias de compilación.

**Frontend** (`control2_frontend/Dockerfile`):
```
Etapa 1: node:20-alpine
  → npm install
  → npm run build
  → genera: dist/ (archivos estáticos)

Etapa 2: nginx:alpine
  → COPY --from=build /app/dist /usr/share/nginx/html
  → COPY nginx.conf /etc/nginx/conf.d/default.conf
```

### Proxy Reverso Nginx

`nginx.conf` resuelve el problema de CORS y unifica el frontend y backend en el mismo puerto (80):

```nginx
server {
    listen 80;

    # Rutas Vue.js (SPA) — redirige todo a index.html
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;   # esencial para Vue Router history mode
    }

    # Proxy reverso al backend Spring Boot
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host            $host;
        proxy_set_header X-Real-IP       $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Con esta configuración:
- El **navegador** solo conoce el puerto 80 — no hay CORS porque todo viene del mismo origen
- Nginx reenvía internamente `/api/*` al contenedor `backend` usando el DNS interno de Docker
- `try_files $uri $uri/ /index.html` hace que al refrescar una ruta como `/dashboard` en el navegador, Nginx devuelva `index.html` en lugar de un 404, permitiendo que Vue Router maneje la ruta en el cliente

### Red interna Docker

Los tres contenedores se comunican por nombre de servicio dentro de la red `app-network`:
- `db` → PostgreSQL accesible como `db:5432`
- `backend` → Spring Boot accesible como `backend:8080`
- `frontend` → Nginx accesible como `frontend:80`

El **healthcheck** garantiza el orden de arranque:
```yaml
backend:
  depends_on:
    db:
      condition: service_healthy  # espera a que pg_isready retorne éxito
```
