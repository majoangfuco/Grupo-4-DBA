# Esquema de colecciones para la transacción de checkout

**Laboratorio 3 · Grupo 4 · Plataforma de E-Commerce B2B**

Este documento define el shape de `ordenes` y `facturas` — las dos colecciones
que la transacción multi-documento de checkout necesita crear — y **confirma**
(sin rediseñar) el shape de `productos` y `carritos`, que ya están descritas
en distinto grado de detalle en el repo. No incluye implementación: es
diseño previo al código, igual que `01-modelado-documental.md`.

---

## 0. Qué ya existe en el repo (punto de partida)

| Colección | Estado actual en el repo | Fuente |
|---|---|---|
| `carritos` | **Definida y validada.** `$jsonSchema` comprometido, con reglas de negocio (`$expr`) sobre stock y mínimo B2B. | [`mongo/schema-validation.js`](../mongo/schema-validation.js), [`docs/01-modelado-documental.md`](01-modelado-documental.md) §4.4 |
| `productos` | **Descrita en prosa**, sin JSON de ejemplo comprometido. La versión previa mencionaba `stockPorAlmacen[]`; ya descartado (ver nota abajo). | `docs/01-modelado-documental.md` §3 (fila `productos`) |
| `ordenes` | Solo aparece en el mapa de colecciones, con la decisión (embeber `items[]` + `cliente` snapshot) pero sin schema. | `docs/01-modelado-documental.md` §3 (fila `ordenes`), §4.5 |
| `facturas` | Solo aparece en el mapa de colecciones, con la decisión (referencia `ordenId`) pero sin schema. | `docs/01-modelado-documental.md` §3 (fila `facturas`) |

Ninguna de las cuatro tiene todavía código Java que la lea o escriba
(`MongoConfig`/`MongoSesionServicio` solo abren la conexión). El checkout
que existe hoy en producción es el stored procedure `procesar_checkout` de
PostgreSQL/PostGIS (asignación de almacén por distancia). Esta transacción
Mongo es el requerimiento NoSQL del Lab 3: **no reemplaza** ese SP, es el
flujo documental que se pide justificar e implementar aparte.

> **Decisión tomada — `stockPorAlmacen[]` descartado para el alcance de Lab 3.**
> `docs/01` proponía en una versión anterior embeber `stockPorAlmacen[]`
> (stock por almacén) en `productos`, pero esa propuesta nunca se
> implementó (sin JSON de ejemplo comprometido, sin código que la usara).
> Se descarta explícitamente: el enunciado de este laboratorio pide solo
> "descontar el stock del producto", sin granularidad por almacén, y ese
> control granular **ya existe y sigue intacto en Postgres**
> (`stock_almacen_producto_entidad` + `PROCEDURE procesar_checkout` en
> `backendB2B/init.sql`, parte del checkout geoespacial del Lab 2, en una
> base de datos separada que este documento no toca). `docs/01` §3 ya
> quedó actualizado con esta nota. `productos.stock` en MongoDB es,
> entonces, un campo **escalar simple** — no hay ambigüedad ni fuente de
> verdad dual que resolver.

---

## 1. Esquemas propuestos

### 1.1 `productos` (confirmado — `stock` escalar, sin `stockPorAlmacen[]`)

```jsonc
// Colección: productos
{
  "_id": ObjectId("64f0a1b2c3d4e5f6a7b8c9d0"),
  "sku": "SKU-00123",
  "nombre": "Caja guantes nitrilo T-M",
  "descripcion": "Caja de 100 unidades, nitrilo sin polvo",
  "categoriaId": ObjectId("64f0a1b2c3d4e5f6a7b8c9d1"),
  "categoriaNombre": "Insumos médicos",       // copiado, evita $lookup en el catálogo
  "precio": 8990,                              // precio de lista vigente
  "stock": 342,                                 // ── DESCONTADO POR EL CHECKOUT ──
  "cantidadMinimaB2B": 50,                      // ya lo exige el validador de carritos.items
  "activo": true,
  "creadoEn": ISODate("2026-01-10T00:00:00Z"),
  "actualizadoEn": ISODate("2026-08-01T12:00:00Z")
}
```

- `cantidadMinimaB2B` no es una novedad de este documento: `mongo/schema-validation.js`
  ya exige que cada ítem de `carritos.items` traiga una copia de este valor
  (`cantidadMinimaB2B` es *requerido* en el validador), así que el campo
  fuente tiene que existir en `productos`. Sin él, el snapshot del carrito
  no se podría construir.
- `stock` es el campo que la transacción de checkout descuenta con un
  `updateOne` condicional (`stock: { $gte: cantidad }`) — es lo que da la
  garantía de no sobreventa dentro de la transacción, junto con el
  `readConcern`/`writeConcern` de sesión.
- No hay `stockPorAlmacen[]`. Es un campo escalar único: la granularidad
  por almacén queda fuera del alcance de Lab 3 y sigue viviendo en
  Postgres (ver nota de §0 y `docs/01` §3).

### 1.2 `carritos` (confirmado, sin cambios)

Ya está completamente definido en `docs/01` §4.4 y validado por
`mongo/schema-validation.js`. Se reproduce resumido solo como referencia
de lo que la transacción de checkout **lee** al empezar:

```jsonc
// Colección: carritos — ver docs/01-modelado-documental.md §4.4 para el detalle completo
{
  "_id": ObjectId("..."),
  "clienteId": 7,
  "estado": "ACTIVO",                     // ACTIVO | ABANDONADO | CONVERTIDO
  "items": [
    {
      "productoId": 123,                  // referencia al catálogo
      "sku": "SKU-00123",
      "nombreProducto": "Caja guantes nitrilo T-M",
      "cantidad": 120,
      "precioUnitario": 8990,             // snapshot al agregar — se REVALIDA en el checkout
      "cantidadMinimaB2B": 50,
      "stockDisponibleAlAgregar": 342,
      "subtotal": 1078800
    }
  ],
  "total": 1298772,
  "ultimaActividad": ISODate("...")       // TTL
}
```

La transacción de checkout no confía ciegamente en `precioUnitario` del
carrito: lo revalida contra `productos.precio` antes de congelar el
snapshot en la orden (ver §3, docs/01 §4.3).

### 1.3 `ordenes` (nuevo)

```jsonc
// Colección: ordenes
{
  "_id": ObjectId("..."),
  "numeroOrden": "ORD-2026-000482",
  "clienteId": 7,
  "cliente": {                                    // snapshot congelado, no se toca nunca más
    "razonSocial": "Distribuidora Andes SpA",
    "rutEmpresa": "76.123.456-7",
    "direccionEnvio": "Av. Libertador 1234, Providencia"
  },
  "carritoId": ObjectId("..."),                   // trazabilidad hacia el carrito que la originó
  "estado": "PENDIENTE",                          // PENDIENTE | APROBADA | CANCELADA
  "items": [                                       // ── SNAPSHOT CONGELADO, INMUTABLE ──
    {
      "productoId": 123,                           // referencia al catálogo (se conserva)
      "nombreProducto": "Caja guantes nitrilo T-M",
      "cantidad": 120,
      "precioUnitario": 8990,                      // precio REVALIDADO al momento del checkout
      "subtotal": 1078800
    }
  ],
  "totalNeto": 1078800,
  "iva": 204972,
  "costoEnvio": 15000,
  "total": 1298772,
  "fechaOrden": ISODate("2026-08-09T14:32:00Z"),
  "facturaId": ObjectId("...")                     // null hasta que la misma transacción emite la factura
}
```

- `estado` reutiliza el enum `PENDIENTE | APROBADA | CANCELADA` que ya usa
  `OrdenesEntidad.estado` en el lado relacional, para no introducir dos
  vocabularios distintos para el mismo concepto de negocio.
- `items[]` trae exactamente los cuatro campos que pediste
  (`productoId`, `nombreProducto`, `cantidad`, `precioUnitario`, `subtotal`)
  y nada más: a diferencia del ítem de carrito, la orden no necesita
  `stockDisponibleAlAgregar` ni `cantidadMinimaB2B` — esos campos existen
  para *validar antes de comprar*, no para el registro histórico.

### 1.4 `facturas` (nuevo)

```jsonc
// Colección: facturas
{
  "_id": ObjectId("..."),
  "numeroFactura": "F-2026-000482",               // índice único
  "ordenId": ObjectId("..."),                     // referencia — NO reembebe items[]
  "cliente": {
    "clienteId": 7,
    "razonSocial": "Distribuidora Andes SpA",
    "rutEmpresa": "76.123.456-7"
  },
  "montoTotal": 1298772,
  "estado": "EMITIDA",                            // EMITIDA | ANULADA
  "fechaEmision": ISODate("2026-08-09T14:32:05Z"),
  "fechaAnulacion": null,
  "motivoAnulacion": null
}
```

- **Por qué no reembebe `items[]`:** el detalle de línea ya quedó congelado
  e inmutable en `ordenes.items` en el mismo paso transaccional. Duplicarlo
  en la factura crearía dos copias de la misma verdad histórica que
  podrían divergir. Es la misma lógica que `docs/01` ya aplicó para decidir
  que `facturas` referencia `ordenId` en vez de embeber: la factura es un
  documento tributario con numeración y ciclo de vida propios (emitir,
  anular, refacturar), no un contenedor de líneas de producto.
- **Trade-off asumido:** descargar el PDF de la factura (`GET /api/facturas/{id}/descargar`,
  ya existe en el lado relacional) necesita un `findOne` extra a `ordenes`
  por `ordenId`. Se acepta porque es una operación de baja frecuencia
  comparada con leer el carrito o listar el catálogo — no es el mismo caso
  que justificó embeber en `carritos`/`ordenes`.
- `numeroFactura` necesita índice único (lo señalo aquí para quien
  implemente el punto de índices, igual que `docs/01` deja notas para el
  TTL de `carritos`; no es parte del alcance de este documento).

---

## 2. Embedding vs referencing — los dos casos pedidos

Mismo criterio de tres preguntas que `docs/01` §2: ¿se leen juntos?,
¿crece sin techo?, ¿debe congelarse en el tiempo?

| Caso | ¿Se leen juntos? | ¿Crece sin techo? | ¿Debe congelarse? | Decisión |
|---|---|---|---|---|
| Ítems del carrito, dentro de `carritos` (no dentro del cliente) | Sí — ver el carrito siempre implica ver sus líneas | No — decenas de líneas por carrito, con techo natural al convertirse en orden | No — es *provisional*, se revalida en cada checkout | **Embeber** `items[]` en `carritos` como documento propio, referenciando `productoId`. Ítems dentro del *cliente* se descarta porque el TTL de carrito abandonado expira el documento completo, y expirar el cliente junto con el carrito no es aceptable. |
| Ítems dentro de `ordenes` | Sí — reimprimir/consultar una orden requiere sus líneas completas, siempre | No — el número de líneas se fija para siempre en el momento del checkout | **Sí** — es un hecho tributario/histórico: debe verse igual años después aunque el producto cambie de precio, nombre o se elimine | **Embeber** `items[]` en `ordenes` como snapshot inmutable. Aquí embeber no es una optimización de lectura, es un requisito de integridad histórica (mismo argumento que `docs/01` §4.5). |

La diferencia de fondo entre ambos casos: en `carritos` el embedding se
justifica por patrón de acceso (lectura conjunta, escritura atómica); en
`ordenes` se justifica además — y sobre todo — porque el dato **no puede
cambiar nunca más**, ni siquiera si se referenciara.

---

## 3. Flujo conceptual de la transacción de checkout (resumen, sin código)

Para que el schema de arriba tenga sentido en conjunto, así se usan las
cuatro colecciones dentro de **una sola transacción multi-documento**
(sesión Mongo con `startTransaction()` / `commitTransaction()`):

1. Leer el `carrito` activo del cliente (`estado: "ACTIVO"`).
2. Por cada ítem, releer el `producto` correspondiente y **revalidar**:
   precio vigente (¿cambió desde que se agregó al carrito?) y stock
   suficiente (`producto.stock >= item.cantidad`). Si algo no calza, se
   aborta la transacción antes de escribir nada.
3. Descontar stock con un `updateOne` condicional por producto
   (`{ stock: { $gte: cantidad } }` → `$inc: { stock: -cantidad } }`). Si
   la condición falla para cualquier ítem (carrera con otra compra),
   se aborta toda la transacción.
4. Insertar el documento en `ordenes` con `items[]` y `cliente` ya
   congelados (usando el precio *revalidado* del paso 2, no el snapshot
   crudo del carrito).
5. Insertar el documento en `facturas`, referenciando `ordenId`, con
   `numeroFactura` nuevo y `estado: "EMITIDA"`.
6. Marcar el `carrito` como `CONVERTIDO` (no se borra: queda como
   trazabilidad de qué carrito originó qué orden).
7. `commitTransaction()`. Si cualquier paso 2–6 falla, la transacción se
   aborta completa: no queda stock descontado sin orden, ni orden sin
   factura.

Este flujo es la razón por la que `productos`, `carritos`, `ordenes` y
`facturas` tienen que vivir en el mismo replica set (`rs0`): una
transacción multi-documento de MongoDB no puede cruzar bases de datos ni
depender de que alguna de las cuatro esté en un nodo no replicado.

---

## 4. Resumen ejecutivo

> Se agregan `ordenes` y `facturas` como colecciones nuevas para el
> checkout: `ordenes` embebe un snapshot **inmutable** de `items[]` y
> `cliente` (mismo criterio que ya fijó `docs/01` para el caso del
> carrito, llevado a su versión definitiva/congelada); `facturas`
> referencia `ordenId` sin reembeber líneas, porque es un documento
> tributario con numeración y ciclo de vida propios.
>
> Se confirma `carritos` tal como ya está definido y validado en el repo
> (`mongo/schema-validation.js`), y se confirma `productos` con un campo
> `stock` **escalar**. El diseño anterior de `stockPorAlmacen[]` queda
> descartado para el alcance de Lab 3 —decisión ya reflejada en `docs/01`
> §3— porque el control de stock por almacén sigue siendo responsabilidad
> de Postgres/Lab 2 (`stock_almacen_producto_entidad` +
> `procesar_checkout`), un sistema aparte que esta transacción documental
> no reemplaza ni depende.
