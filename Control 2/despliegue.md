# Guía de Configuración y Despliegue

Sistema de Gestión de Tareas Geoespaciales — Control 2

---

## Índice

1. [Requisitos Previos](#1-requisitos-previos)
2. [Estructura del Repositorio](#2-estructura-del-repositorio)
3. [Despliegue con Docker (Producción)](#3-despliegue-con-docker-producción)
4. [Desarrollo Local sin Docker](#4-desarrollo-local-sin-docker)
5. [Variables de Entorno](#5-variables-de-entorno)
6. [Base de Datos — Inicialización Manual](#6-base-de-datos--inicialización-manual)
7. [Comandos Útiles](#7-comandos-útiles)
8. [Resolución de Problemas Comunes](#8-resolución-de-problemas-comunes)

---

## 1. Requisitos Previos

### Para despliegue con Docker (recomendado)

| Herramienta | Versión mínima | Verificación |
|---|---|---|
| Docker | 24.x | `docker --version` |
| Docker Compose | 2.x | `docker compose version` |

> **Puertos requeridos libres:** `80` (frontend), `8080` (backend), `5432` (PostgreSQL)

### Para desarrollo local sin Docker

| Herramienta | Versión mínima | Verificación |
|---|---|---|
| Java (JDK) | 17 | `java -version` |
| Maven | 3.9.x | `mvn -version` |
| Node.js | 20.x | `node --version` |
| npm | 10.x | `npm --version` |
| PostgreSQL | 15 | `psql --version` |
| PostGIS | 3.3 | `SELECT PostGIS_Version();` en psql |

---

## 2. Estructura del Repositorio

```
Control 2/
├── docker-compose.yml          ← Orquesta los 3 servicios
├── README.md                   ← Documentación general + API
├── DEPLOY.md                   ← Esta guía
├── IMPLEMENTATION.md           ← Documentación de implementación
│
├── control2_backend/           ← API REST (Spring Boot)
│   ├── Dockerfile
│   ├── pom.xml
│   ├── .env                    ← Variables para desarrollo local (NO subir a git)
│   ├── .env.example            ← Plantilla de variables
│   └── src/
│       └── main/
│           ├── java/           ← Código Java
│           └── resources/
│               ├── application.properties
│               └── data.sql    ← DDL + datos de prueba
│
└── control2_frontend/          ← SPA (Vue.js 3)
    ├── Dockerfile
    ├── nginx.conf              ← Proxy reverso en producción
    ├── .env.development        ← Apunta a localhost:8081
    ├── .env.production         ← Apunta a /api (proxy Nginx)
    └── src/
```

---

## 3. Despliegue con Docker (Producción)

Este es el método **recomendado**. Un solo comando construye y levanta todo.

### Paso 1 — Clonar y navegar al proyecto

```bash
cd "Control 2"
```

### Paso 2 — (Opcional) Configurar JWT Secret

El `docker-compose.yml` incluye un secret de ejemplo. Para producción real, cámbialo editando la línea:

```yaml
# docker-compose.yml, línea 48
- JWT_SECRET=tu-clave-secreta-de-al-menos-32-caracteres-aqui
```

### Paso 3 — Construir y levantar

```bash
docker-compose up --build
```

Este comando:
1. Descarga la imagen `postgis/postgis:15-3.3` y levanta la base de datos
2. Compila el backend Spring Boot dentro de un contenedor Maven (esto toma ~3 minutos la primera vez)
3. Compila el frontend Vue.js con Vite y lo empaqueta en un contenedor Nginx
4. Conecta los 3 servicios en una red interna `app-network`

### Paso 4 — Acceder a la aplicación

| Servicio | URL |
|---|---|
| **Frontend (app principal)** | http://localhost |
| Backend (acceso directo, opcional) | http://localhost:8080/api |
| PostgreSQL | `localhost:5432` (usuario: `postgres`, pass: `root`) |

### Paso 5 — Detener

```bash
# Detener manteniendo los datos
docker-compose down

# Detener y borrar la base de datos (reinicio limpio)
docker-compose down -v
```

### Flujo interno de Docker

```
Navegador → :80 (Nginx)
                ├── /        → archivos estáticos Vue.js
                └── /api/*   → proxy reverso → backend:8080
                                                    ↓
                                            PostgreSQL:5432
                                           (control2_bd + PostGIS)
```

---

## 4. Desarrollo Local sin Docker

Útil para hacer cambios y ver resultados inmediatos sin reconstruir imágenes.

### 4.1 Configurar PostgreSQL local

```sql
-- Conectarse como superusuario
psql -U postgres

-- Crear la base de datos
CREATE DATABASE control2_bd;

-- Conectarse a ella y habilitar PostGIS
\c control2_bd
CREATE EXTENSION IF NOT EXISTS postgis;
```

Verificar que PostGIS está activo:
```sql
SELECT PostGIS_Version();
-- Debe retornar algo como: 3.3 USE_GEOS=1 USE_PROJ=1 ...
```

### 4.2 Configurar el Backend

```bash
cd control2_backend
```

Crear el archivo `.env` a partir de la plantilla:
```bash
cp .env.example .env
```

Editar `.env` con los datos de tu PostgreSQL local:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=control2_bd
DB_USER=postgres
DB_PASSWORD=tu_password_local
JWT_SECRET=clave-secreta-minimo-32-caracteres-aqui
JWT_EXPIRATION=86400000
```

Levantar el backend:
```bash
./mvnw spring-boot:run
```

> En Windows usar `mvnw.cmd spring-boot:run`

El backend queda disponible en `http://localhost:8081/api`

Al iniciar, Spring Boot ejecuta automáticamente `data.sql` que:
- Crea las tablas (`usuarios`, `sectores`, `tareas`) si no existen
- Habilita la extensión PostGIS
- Crea los índices espaciales GIST
- Inserta datos de prueba

### 4.3 Configurar el Frontend

```bash
cd control2_frontend
npm install
npm run dev
```

El frontend queda disponible en `http://localhost:5173`

El archivo `.env.development` ya está configurado para apuntar al backend local:
```env
VITE_API_BASE_URL=http://localhost:8081/api
```

### 4.4 Verificar que todo funciona

1. Abrir `http://localhost:5173`
2. Registrar un usuario con cualquier coordenada (ej: latitud `-33.45`, longitud `-70.65`)
3. Iniciar sesión
4. Crear una tarea asignándola a un sector

---

## 5. Variables de Entorno

### Backend (`control2_backend/.env` o variables Docker)

| Variable | Requerida | Default | Descripción |
|---|---|---|---|
| `DB_HOST` | No | `localhost` | Host de PostgreSQL |
| `DB_PORT` | No | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | No | `control2_bd` | Nombre de la base de datos |
| `DB_USER` | No | `postgres` | Usuario de PostgreSQL |
| `DB_PASSWORD` | No | `postgres` | Contraseña de PostgreSQL |
| `SERVER_PORT` | No | `8081` | Puerto del servidor Spring (8080 en Docker) |
| `JWT_SECRET` | **Sí** | — | Clave HMAC para firmar JWT (mínimo 32 caracteres) |
| `JWT_EXPIRATION` | No | `86400000` | Expiración del JWT en ms (default: 24 horas) |
| `JPA_HIBERNATE_DDL_AUTO` | No | `update` | `create`, `update`, `validate`, `none` |
| `SQL_INIT_MODE` | No | `always` | `always` ejecuta `data.sql` en cada inicio |
| `MAIL_ENABLED` | No | `false` | Activar notificaciones por correo |
| `MAIL_HOST` | No | `smtp.gmail.com` | Servidor SMTP |
| `MAIL_PORT` | No | `587` | Puerto SMTP |
| `MAIL_USERNAME` | No | — | Cuenta de correo |
| `MAIL_PASSWORD` | No | — | Contraseña de la cuenta |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:*` | Orígenes permitidos (separados por coma) |
| `LOG_LEVEL` | No | `INFO` | Nivel de logging (`DEBUG`, `INFO`, `WARN`) |

### Frontend (`control2_frontend/.env.*`)

| Variable | Descripción |
|---|---|
| `VITE_API_BASE_URL` | URL base de la API (ej: `http://localhost:8081/api` o `/api`) |

---

## 6. Base de Datos — Inicialización Manual

Si necesitas crear las tablas manualmente (sin Spring Boot):

```sql
-- Conectarse a control2_bd
\c control2_bd

-- Habilitar PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- Tabla usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    ubicacion_geografica geometry(Point, 4326) NOT NULL
);

-- Tabla sectores
CREATE TABLE IF NOT EXISTS sectores (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    ubicacion_espacial geometry(Point, 4326) NOT NULL
);

-- Tabla tareas
CREATE TABLE IF NOT EXISTS tareas (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    fecha_vencimiento TIMESTAMP NOT NULL,
    estado_completada BOOLEAN NOT NULL DEFAULT false,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    sector_id BIGINT NOT NULL REFERENCES sectores(id) ON DELETE CASCADE
);

-- Índices espaciales GIST (cruciales para el rendimiento de ST_Distance, ST_DWithin)
CREATE INDEX IF NOT EXISTS idx_usuarios_ubicacion ON usuarios USING GIST(ubicacion_geografica);
CREATE INDEX IF NOT EXISTS idx_sectores_ubicacion ON sectores USING GIST(ubicacion_espacial);
CREATE INDEX IF NOT EXISTS idx_tareas_usuario    ON tareas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_tareas_sector     ON tareas(sector_id);
CREATE INDEX IF NOT EXISTS idx_tareas_estado     ON tareas(estado_completada);
CREATE INDEX IF NOT EXISTS idx_tareas_fecha      ON tareas(fecha_vencimiento);
```

### Insertar datos de prueba

```sql
-- Usuarios (password: 123456 hasheada con BCrypt)
INSERT INTO usuarios (username, password, ubicacion_geografica) VALUES
('usuario1', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-70.6483 -33.4569)', 4326)),
('usuario2', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-70.6500 -33.4580)', 4326));

-- Sectores
INSERT INTO sectores (nombre, ubicacion_espacial) VALUES
('Construcción Centro',        ST_GeomFromText('POINT(-70.6490 -33.4575)', 4326)),
('Reparación Semáforos Norte', ST_GeomFromText('POINT(-70.6470 -33.4560)', 4326)),
('Mantenimiento Parques',      ST_GeomFromText('POINT(-70.6510 -33.4590)', 4326));
```

---

## 7. Comandos Útiles

### Docker

```bash
# Construir y levantar todo
docker-compose up --build

# Levantar en segundo plano
docker-compose up -d --build

# Ver logs de todos los servicios
docker-compose logs -f

# Ver logs solo del backend
docker-compose logs -f backend

# Ver logs solo de la BD
docker-compose logs -f db

# Reconstruir solo un servicio
docker-compose up --build backend

# Detener todo (conserva datos)
docker-compose down

# Detener y borrar volúmenes (borra la BD)
docker-compose down -v

# Ver estado de los contenedores
docker-compose ps

# Entrar a la BD dentro del contenedor
docker exec -it control2_db psql -U postgres -d control2_bd
```

### Maven (desarrollo backend)

```bash
# Compilar y ejecutar
./mvnw spring-boot:run

# Solo compilar (sin tests)
./mvnw clean package -DskipTests

# Ejecutar tests
./mvnw test
```

### npm (desarrollo frontend)

```bash
# Instalar dependencias
npm install

# Servidor de desarrollo con hot-reload
npm run dev

# Compilar para producción
npm run build

# Revisar errores de linting
npm run lint
```

---

## 8. Resolución de Problemas Comunes

### ❌ Error: `Connection refused` al iniciar backend

**Causa:** Spring Boot intenta conectarse a PostgreSQL antes de que esté listo.

**Solución en Docker:** El `healthcheck` en `docker-compose.yml` hace que el backend espere a la DB. Si igual falla, aumentar `start_period`:
```yaml
healthcheck:
  start_period: 40s  # aumentar de 20s a 40s
```

**Solución local:** Verificar que PostgreSQL está corriendo:
```bash
# Windows
Get-Service postgresql*

# Linux/Mac
sudo systemctl status postgresql
```

---

### ❌ Error: `PostGIS extension not found`

**Causa:** La extensión PostGIS no está instalada en la BD.

**Solución:**
```sql
\c control2_bd
CREATE EXTENSION IF NOT EXISTS postgis;
```

Si el comando falla, PostGIS no está instalado en el servidor PostgreSQL. En Ubuntu:
```bash
sudo apt install postgresql-15-postgis-3
```

---

### ❌ Error: `jwt.secret` requerido pero no configurado

**Causa:** La variable `JWT_SECRET` no está definida.

**Solución local:** Agregar al `.env`:
```env
JWT_SECRET=mi-clave-secreta-de-prueba-32chars!
```

**Solución Docker:** Ya está configurada en `docker-compose.yml`. Si se modificó el archivo, verificar que la línea existe.

---

### ❌ Error `403 Forbidden` al hacer POST/PUT desde el frontend

**Causa:** Token CSRF no sincronizado.

**Solución:** El frontend (`api.ts`) tiene un interceptor que reintenta automáticamente la petición con el nuevo token CSRF. Si persiste:
1. Limpiar cookies del navegador para `localhost`
2. Cerrar sesión y volver a iniciar sesión

---

### ❌ Las estadísticas geoespaciales retornan vacío

**Causa más común:** El usuario no tiene ubicación registrada, o no hay tareas asociadas a sectores.

**Verificación:**
```sql
-- Ver ubicaciones de usuarios
SELECT username, ST_AsText(ubicacion_geografica) FROM usuarios;

-- Ver tareas con sectores
SELECT t.titulo, s.nombre, t.estado_completada
FROM tareas t JOIN sectores s ON t.sector_id = s.id;
```

---

### ❌ El frontend muestra pantalla en blanco en producción Docker

**Causa:** Vue Router en modo `history` necesita que Nginx redirija todas las rutas a `index.html`.

**Verificar `nginx.conf`:**
```nginx
location / {
    try_files $uri $uri/ /index.html;  # esta línea debe existir
}
```

---

### ❌ `docker-compose up` falla con "port already in use"

**Causa:** Otro servicio usa el puerto 80, 8080 o 5432.

**Solución:** Encontrar y detener el proceso:
```bash
# Windows PowerShell
netstat -ano | findstr :80
Stop-Process -Id <PID>

# Linux/Mac
lsof -i :80
kill <PID>
```

O cambiar los puertos en `docker-compose.yml`:
```yaml
ports:
  - "8081:8080"   # puerto_host:puerto_contenedor
```
