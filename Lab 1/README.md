# Laboratorio 1 — Plataforma de E-Commerce B2B
**Grupo 4 | Taller de Base de Datos 1-2026**  

---

## Tabla de contenidos

1. [Descripción del proyecto](#descripción-del-proyecto)
2. [Arquitectura y tecnologías](#arquitectura-y-tecnologías)
3. [Estructura del repositorio](#estructura-del-repositorio)
4. [Componentes de base de datos](#componentes-de-base-de-datos)
5. [Manual de instalación y despliegue](#manual-de-instalación-y-despliegue)
6. [Credenciales de prueba](#credenciales-de-prueba)
7. [Documentación de la API](#documentación-de-la-api)

---

## Descripción del proyecto

Plataforma de comercio B2B (Business-to-Business) mayorista que permite a empresas clientes gestionar su catálogo de productos, realizar órdenes de compra y descargar facturas. El sistema está construido sobre una arquitectura desacoplada de tres capas con lógica de negocio implementada directamente en el motor de base de datos.

---

## Arquitectura y tecnologías

```
┌─────────────────┐      HTTP/REST      ┌─────────────────────┐
│   Frontend      │ ◄──────────────────► │   Backend (API)     │
│   Vue 3 + Vite  │                      │  Spring Boot 4.0.6  │
│   Puerto: 5173  │                      │   Puerto: 8090      │
└─────────────────┘                      └──────────┬──────────┘
                                                    │ JDBC
                                         ┌──────────▼──────────┐
                                         │   PostgreSQL 16      │
                                         │   Puerto: 5433       │
                                         │  (Triggers, SPs,     │
                                         │   Vistas Mat.)       │
                                         └─────────────────────┘
```

| Capa       | Tecnología                              | Versión |
|------------|-----------------------------------------|---------|
| Frontend   | Vue 3 + TypeScript + Vite + Pinia       | Vue 3.5 |
| Backend    | Spring Boot + Spring JDBC + Spring Security | 4.0.6 |
| Base de datos | PostgreSQL                           | 16      |
| Auth       | JWT (JSON Web Tokens)                   | —       |
| Build tool | Maven                                   | —       |
| Contenedores | Docker + Docker Compose               | —       |

> **Sin ORM:** el backend usa exclusivamente `JdbcTemplate` (Spring JDBC). No se utiliza JPA/Hibernate ni ningún ORM.

---

## Estructura del repositorio

```
Lab 1/
├── backendB2B/
│   ├── src/main/java/com/ecommerceb2b/backend/
│   │   ├── Config/          # JWT filter y Security config
│   │   ├── Controllers/     # Endpoints REST
│   │   ├── Entities/        # Modelos y DTOs
│   │   ├── Repository/      # Acceso a datos (JdbcTemplate)
│   │   └── Services/        # Lógica de negocio
│   ├── init.sql             # Script completo de BD (tablas, triggers, SPs, datos)
│   ├── docker-compose.yml   # Levanta backend + PostgreSQL
│   └── Dockerfile
├── frontendB2B/
│   ├── src/
│   │   ├── views/           # Páginas Vue
│   │   ├── components/      # Componentes reutilizables
│   │   ├── stores/          # Estado global (Pinia)
│   │   └── router/          # Rutas (Vue Router)
│   └── package.json
└── README.md
```

---

## Componentes de base de datos

### Tablas principales

| Tabla                        | Descripción                              |
|------------------------------|------------------------------------------|
| `usuario_entidad`            | Clientes y administradores (con roles)   |
| `producto_entidad`           | Catálogo de productos con stock          |
| `categoria_entidad`          | Categorías de productos                  |
| `carrito_entidad`            | Carrito de compras por usuario           |
| `carrito_producto_entidad`   | Productos dentro de cada carrito         |
| `ordenes_entidad`            | Órdenes generadas desde carritos         |
| `informacion_entrega_entidad`| Datos de entrega asociados a órdenes     |
| `datos_pago_entidad`         | Métodos de pago registrados por usuario  |
| `factura_entidad`            | Facturas emitidas por cada orden         |
| `factura_item_entidad`       | Líneas de detalle de cada factura        |
| `audit_ordenes`              | Registro de auditoría de cambios         |

### Procedimientos almacenados

| Procedimiento                   | Descripción                                                                  |
|---------------------------------|------------------------------------------------------------------------------|
| `procesar_checkout(carritoId, infoEntregaId)` | Transacción atómica: valida stock, descuenta inventario, crea orden y factura, vacía carrito |
| `aplicar_descuento_categoria(categoriaId, porcentaje)` | Aplica descuento masivo a todos los productos activos de una categoría |
| `reservar_stock(productoId, cantidad)` | Incrementa el stock reservado de un producto |
| `liberar_stock(productoId, cantidad)` | Libera stock reservado previamente |

### Triggers

| Trigger                    | Evento                     | Función                                                                 |
|----------------------------|----------------------------|-------------------------------------------------------------------------|
| `carrito_estado_cambio`    | UPDATE estado en carrito   | Libera, reserva o consume stock según el nuevo estado del carrito       |
| `prevenir_sobreventa`      | INSERT/UPDATE en ordenes   | Bloquea la confirmación de una orden si el stock es insuficiente        |
| `actualizar_ultima_compra` | INSERT/UPDATE en ordenes   | Actualiza el campo `ultima_compra` del usuario al aprobar una orden     |
| `auditar_cambios_orden`    | INSERT/UPDATE en ordenes   | Registra todos los cambios de estado en la tabla `audit_ordenes`        |

### Vista materializada

**`vw_ventas_mensuales_por_categoria`** — Consolida ventas agrupadas por mes y categoría de producto. Incluye cantidad de órdenes, unidades vendidas, total vendido y precio promedio.

```sql
-- Refrescar manualmente:
REFRESH MATERIALIZED VIEW vw_ventas_mensuales_por_categoria;

-- O vía API:
POST /api/reportes/refrescar
```

### Índices estratégicos

```sql
idx_producto_sku              -- Búsqueda por SKU (B-Tree)
idx_usuario_id                -- Acceso directo por ID de usuario
idx_carrito_usuario           -- Carritos por cliente
idx_carrito_producto_carrito  -- Productos de un carrito
idx_carrito_producto_producto -- Carritos que contienen un producto
idx_orden_carrito             -- Órdenes por carrito
idx_factura_usuario           -- Facturas por usuario
idx_factura_orden             -- Factura de una orden
idx_producto_categoria        -- Productos por categoría
idx_informacion_entrega_orden -- Entregas por orden
-- Índices sobre la vista materializada:
idx_vw_ventas_mes_ano, idx_vw_ventas_categoria, idx_vw_ventas_anio
```

---

## Manual de instalación y despliegue

### Prerrequisitos

| Herramienta  | Versión mínima | Verificar con         |
|--------------|----------------|-----------------------|
| Docker       | 24+            | `docker --version`    |
| Docker Compose | 2.20+        | `docker compose version` |
| Node.js      | 20.19+ o 22+   | `node --version`      |
| npm          | 10+            | `npm --version`       |

---

### Paso 1 — Clonar el repositorio

```bash
git clone https://github.com/majoangfuco/Grupo-4-DBA.git
cd "Grupo-4-DBA/Lab 1"
```

---

### Paso 2 — Levantar el backend y la base de datos (Docker)

El backend y PostgreSQL se levantan juntos con Docker Compose. El archivo `init.sql` se ejecuta automáticamente al inicializar la base de datos por primera vez.

```bash
cd backendB2B
docker compose up --build -d
```

Esto levanta dos contenedores:

| Contenedor   | Servicio    | Puerto local |
|--------------|-------------|--------------|
| `b2b-db`     | PostgreSQL  | `5433`       |
| `b2b-backend`| Spring Boot | `8090`       |

Esperar aproximadamente 30-60 segundos a que Spring Boot arranque. Verificar con:

```bash
docker logs b2b-backend --tail 20
```

Debe aparecer: `Started BackendApplication` al final del log.

> **Variables de entorno** ya configuradas en `docker-compose.yml`:
> ```
> DB_HOST=db | DB_PORT=5432 | DB_NAME=b2b
> DB_USER=postgres | DB_PASSWORD=postgres
> APP_PORT=8090
> JWT_SECRET=mySecureSecretKeyForJwtTokenGenerationAndValidation2024
> JWT_EXPIRATION_MS=86400000
> ```

---

### Paso 3 — Levantar el frontend

En una terminal separada, desde la raíz del repositorio:

```bash
cd frontendB2B
npm install
npm run dev
```

El frontend estará disponible en: **http://localhost:5173**

---

### Paso 4 — Verificar el sistema

| URL                                    | Descripción                    |
|----------------------------------------|--------------------------------|
| http://localhost:5173                  | Interfaz web (frontend)        |
| http://localhost:8090/api/productos    | API — listado de productos     |
| http://localhost:8090/api/categorias   | API — listado de categorías    |

---

### Resetear la base de datos

Si necesitas partir desde cero (re-ejecutar `init.sql`):

```bash
cd backendB2B
docker compose down -v
docker compose up --build -d
```

El flag `-v` elimina el volumen `db_data`, forzando a PostgreSQL a reinicializarse con los datos de prueba.

---

### Detener el sistema

```bash
# Detener contenedores (sin borrar datos)
cd backendB2B
docker compose down

# Detener frontend
# Ctrl+C en la terminal donde corre `npm run dev`
```

---

## Credenciales de prueba

El script `init.sql` carga los siguientes usuarios de prueba con contraseñas hasheadas en bcrypt:

| Rol     | Correo                          | Contraseña       |
|---------|---------------------------------|------------------|
| ADMIN   | admin@ecommerceb2b.cl           | Admin123!        |
| ADMIN   | dvar@adminb2b.cl                | Admin123!        |
| CLIENTE | jperez@techsolutions.cl         | Cliente123!      |
| CLIENTE | aso@construccionesdelnorte.cl   | Cliente123!      |
| CLIENTE | mlopez@techsolutions.cl         | Cliente123!      |
| CLIENTE | lfernandez@logisticadelsur.cl   | Cliente123!      |

> Los usuarios ADMIN tienen acceso completo (gestión de stock, aprobación de órdenes, reportes). Los CLIENTE solo pueden ver sus propios datos y crear órdenes.

---

## Documentación de la API

Base URL: `http://localhost:8090`

Las rutas protegidas requieren el header:
```
Authorization: Bearer <token>
```

El token se obtiene al hacer login.

---

### Autenticación — `/usuario`

#### POST `/usuario/login`
Autentica un usuario y retorna un JWT.

**Request:**
```json
{
  "correo": "admin@ecommerceb2b.cl",
  "contrasena": "Admin123!"
}
```

**Response 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "mensaje": "Login exitoso"
}
```

---

#### POST `/usuario/register`
Registra un nuevo usuario con rol CLIENTE.

**Request:**
```json
{
  "nombre": "Empresa Nueva",
  "correo": "contacto@empresa.cl",
  "contrasena": "Password123!",
  "rut_empresa": "76.000.000-1"
}
```

**Response 201:**
```json
{ "mensaje": "Usuario registrado exitosamente" }
```

---

#### GET `/usuario/buscar`
Retorna los datos del usuario autenticado según el token JWT.

**Headers:** `Authorization: Bearer <token>`

**Response 200:**
```json
{
  "usuario_id": 1,
  "nombre": "Juan Perez",
  "correo": "jperez@techsolutions.cl",
  "rol": "CLIENTE",
  "rut_empresa": "76.123.456-7"
}
```

---

#### GET `/usuario/clientes`
Lista todos los usuarios con rol CLIENTE. Requiere JWT.

**Response 200:**
```json
{
  "clientes": [
    { "usuario_ID": 1, "nombre_Usuario": "Juan Perez", "correo": "...", "rut_Empresa": "..." }
  ]
}
```

---

### Productos — `/api/productos`

#### GET `/api/productos`
Lista todos los productos activos.

**Response 200:**
```json
[
  {
    "producto_ID": 1,
    "nombre_producto": "Notebook Empresarial Pro 15\"",
    "descripcion": "Notebook Intel Core i7, 16GB RAM, 512GB SSD",
    "precio": 1200000.0,
    "stock": 150,
    "sku": "SKU-COMP-001",
    "activo": true
  }
]
```

---

#### GET `/api/productos/{id}`
Obtiene un producto por ID.

**Response 404:**
```json
"Producto no encontrado con ID: 999"
```

---

#### POST `/api/productos`
Crea un nuevo producto (requiere rol ADMIN).

**Request:**
```json
{
  "nombre_producto": "Teclado Mecánico Empresarial",
  "descripcion": "Teclado TKL con switches Cherry MX",
  "precio": 75000.0,
  "stock": 200,
  "sku": "SKU-COMP-005",
  "categoria": { "categoria_id": 1 }
}
```

---

#### PUT `/api/productos/{id}`
Actualiza un producto existente.

---

#### DELETE `/api/productos/{id}`
Elimina (desactiva) un producto.

---

#### GET `/api/productos/buscar?termino=laptop`
Busca productos por nombre o descripción (usa índice `idx_producto_sku`).

**Response 200:**
```json
[
  { "producto_ID": 1, "nombre_producto": "Notebook Empresarial Pro 15\"", ... }
]
```

---

#### GET `/api/productos/sku/{sku}`
Busca un producto por su SKU exacto.

**Ejemplo:** `GET /api/productos/sku/SKU-COMP-001`

---

#### GET `/api/productos/categoria/{categoriaId}`
Lista productos de una categoría.

---

#### POST `/api/productos/descuento?categoriaId=1&porcentaje=15`
Aplica un descuento masivo (Stored Procedure 2). Requiere rol ADMIN.

**Response 200:**
```json
"Descuento aplicado correctamente"
```

---

### Categorías — `/api/categorias`

#### GET `/api/categorias`
Lista categorías activas. Con `?incluirInactivas=true` y token ADMIN muestra todas.

**Response 200:**
```json
[
  { "categoria_id": 1, "nombre_categoria": "Equipos de Computación", "estado_categoria": true }
]
```

#### POST `/api/categorias`
```json
{ "nombre_categoria": "Periféricos" }
```

#### PUT `/api/categorias/{id}`
```json
{ "nombre_categoria": "Periféricos de Oficina", "estado_categoria": true }
```

#### GET `/api/categorias/buscar?nombre=redes`
Busca categorías por nombre parcial.

---

### Carrito — `/api/carritos`

#### GET `/api/carritos/cliente/{idCliente}/activo`
Obtiene el carrito activo del cliente, o crea uno nuevo si no existe.

**Response 200:**
```json
{
  "carrito_ID": 8,
  "estado": "ACTIVO",
  "costo_carrito": 35000,
  "items": [...]
}
```

---

#### POST `/api/carritos/{id}/checkout`
Procesa el checkout completo (llama al Stored Procedure 1). Crea orden, descuenta inventario y genera factura en una sola transacción atómica.

**Request:**
```json
{
  "infoEntregaId": 2,
  "datosPagoId": 1
}
```

**Response 201:**
```json
{
  "orden_ID": 12,
  "estado": "PAGADO",
  "fecha_orden": "2026-05-04T10:30:00"
}
```

**Response 400 (stock insuficiente):**
```json
"Stock insuficiente para producto Notebook Empresarial Pro 15\". Disponible: 0, Solicitado: 2"
```

---

#### PATCH `/api/carritos/{id}/cerrar`
Cambia el estado del carrito a ABANDONADO (libera stock reservado vía trigger).

#### PATCH `/api/carritos/{id}/pagar`
Cambia el estado a PAGADO (consume stock definitivamente vía trigger).

#### POST `/api/carritos/{id}/vaciar`
Elimina todos los productos del carrito.

---

### Órdenes — `/api/ordenes`

#### GET `/api/ordenes`
Lista todas las órdenes.

#### GET `/api/ordenes/{id}`
Obtiene una orden por ID.

**Response 200:**
```json
{
  "orden_ID": 1,
  "estado": "APROBADA",
  "fecha_orden": "2023-10-01T10:05:00",
  "carrito_ID": 1
}
```

#### GET `/api/ordenes/usuario/{usuarioId}`
Lista las órdenes de un cliente específico. Los clientes solo pueden ver las propias.

#### POST `/api/ordenes/solicitar/{carritoId}`
Alternativa al checkout del carrito. Acepta el mismo DTO.

#### PATCH `/api/ordenes/{id}/aprobar`
Cambia estado a APROBADA. Dispara el Trigger 2 (actualiza `ultima_compra`).

#### PATCH `/api/ordenes/{id}/cancelar`
Cambia estado a CANCELADA.

#### GET `/api/ordenes/estado?valor=PENDIENTE`
Filtra órdenes por estado. Valores posibles: `PENDIENTE`, `APROBADA`, `CANCELADA`, `PAGADO`, `EN_RUTA`, `PREPARANDO`, `ENTREGADO`.

---

### Facturas — `/api/facturas`

Todos los endpoints de facturas requieren `Authorization: Bearer <token>`. Los clientes solo ven sus propias facturas; los ADMIN ven todas.

#### GET `/api/facturas`
Lista facturas (filtradas por rol).

**Response 200:**
```json
[
  {
    "factura_ID": 1,
    "precio_Total": 3350000.0,
    "total_neto": 2815126.0,
    "iva": 534874.0,
    "fecha_Emision": "2023-10-01T10:30:00",
    "items": [
      {
        "nombre_producto": "Notebook Empresarial Pro 15\"",
        "cantidad": 2,
        "precio_unitario": 1200000.0
      }
    ]
  }
]
```

#### GET `/api/facturas/{id}`
Obtiene una factura por ID con su detalle de items.

#### GET `/api/facturas/usuario/{usuarioId}`
Historial de facturas de un cliente. Los clientes solo pueden consultar las propias (Req. 10).

#### GET `/api/facturas/orden/{ordenId}`
Obtiene la factura asociada a una orden específica.

#### GET `/api/facturas/orden/{ordenId}/descargar`
Descarga la factura en formato PDF.

**Response:** `application/pdf` — archivo `factura-{id}.pdf`

#### GET `/api/facturas/{id}/descargar`
Descarga una factura por su ID en formato PDF.

---

### Reportes — `/api/reportes`

Endpoints que consumen la vista materializada `vw_ventas_mensuales_por_categoria`.

#### GET `/api/reportes/ventas`
Retorna el histórico completo de ventas por mes y categoría.

**Response 200:**
```json
[
  {
    "mes_ano": "2024-03",
    "anio": 2024,
    "mes": 3,
    "nombre_categoria": "Equipos de Computación",
    "cantidad_ordenes": 5,
    "cantidad_productos": 12,
    "total_vendido": 6000000.0,
    "precio_promedio": 500000.0
  }
]
```

#### GET `/api/reportes/ventas/mes?mesAno=2024-03`
Filtra reporte por mes y año específico (formato `YYYY-MM`).

#### GET `/api/reportes/ventas/categoria?nombre=Equipos de Computación`
Filtra reporte por nombre de categoría.

#### GET `/api/reportes/ventas/anio?anio=2024`
Filtra reporte por año.

#### GET `/api/reportes/ventas/total`
Retorna el total consolidado de todas las ventas.

#### POST `/api/reportes/refrescar`
Refresca la vista materializada con los datos más recientes.

**Response 200:**
```json
"Vista materializada refrescada correctamente"
```

---

### Información de entrega — `/api/entregas`

#### GET `/api/entregas/usuario/{usuarioId}`
Lista las direcciones de entrega de un usuario.

#### POST `/api/entregas`
Registra una nueva dirección de entrega.

**Request:**
```json
{
  "usuario_usuario": 1,
  "direccion": "Av. Providencia",
  "numero": "1234",
  "rut_recibe_entrega": "12.345.678-9",
  "rut_empresa": "76.123.456-7",
  "estado_entrega": "PENDIENTE",
  "activa": true
}
```

