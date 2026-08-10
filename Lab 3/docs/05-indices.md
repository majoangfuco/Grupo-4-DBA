# Índices MongoDB — punto 5

La solución mantiene la arquitectura híbrida: estos índices se crean solamente
en las colecciones documentales `facturas` y `carritos`. PostgreSQL/PostGIS
continúa siendo la fuente de verdad del dominio geoespacial; este punto no
migra comunas, unidades vecinales, almacenes ni zonas.

El backend usa MongoDB como fuente operativa de carritos y facturas. Los IDs
`clienteId` y `productoId` son referencias lógicas `long` hacia PostgreSQL;
usuarios, catálogo, reservas de stock, órdenes y geometrías permanecen allí.
Durante el checkout se crea una proyección SQL técnica del carrito para
reutilizar el procedimiento geoespacial y sus bloqueos de inventario. Esa
proyección no atiende los endpoints del carrito.

## Índices implementados

| Colección | Índice | Propósito |
|---|---|---|
| `facturas` | `{ numeroFactura: 1 }`, `unique: true` | Impide números de factura duplicados. |
| `facturas` | `{ "cliente.clienteId": 1, fechaEmision: -1 }` | Resuelve el historial de un cliente desde la factura más reciente. El campo va dentro del snapshot embebido `cliente{}` (ver [`docs/03`](03-checkout-transaccion.md) §1.4), no en la raíz del documento. |
| `facturas` | `{ ordenId: 1 }`, `unique: true` | Una orden documental origina una sola factura. |
| `facturas_relacionales` | `{ numeroFactura: 1 }` y `{ ordenId: 1 }`, ambos `unique`; `{ clienteId: 1, fechaEmision: -1 }` | Mismos propósitos que en `facturas`, pero sobre los campos del shape relacional (`clienteId` plano en la raíz). Es una colección aparte porque su shape es incompatible y Mongo solo admite un `$jsonSchema` por colección — ver [`docs/02`](02-schema-validation.md) §2.3. |
| `carritos` | `{ ultimaActividad: 1 }`, TTL parcial | Elimina únicamente carritos con `estado: "ABANDONADO"` tras el plazo configurado. |
| `productos` | `{ nombre: "text" }`, idioma español | Búsqueda por contenido sobre el nombre del producto en la copia de checkout. |

El plazo predeterminado es 30 días (`2592000` segundos) y se puede cambiar con
`MONGO_CART_TTL_SECONDS`. La eliminación TTL no es instantánea: la ejecuta el
monitor interno de MongoDB de forma periódica.

## Refresco obligatorio de actividad

`ultimaActividad` es un BSON `Date`, no un texto. Toda escritura que represente
actividad (crear u obtener el carrito activo, agregar o quitar ítems, cambiar
cantidades, cambiar estado o reabrirlo)
debe incluir `$currentDate` en el mismo `updateOne`, por ejemplo:

```javascript
db.carritos.updateOne(
  { _id: carritoId, estado: { $in: ["ACTIVO", "ABANDONADO"] } },
  {
    $set: { estado: "ACTIVO" },
    $inc: { "items.$[item].cantidad": 1 },
    $currentDate: { ultimaActividad: true }
  },
  { arrayFilters: [{ "item.productoId": productoId }] }
);
```

Al marcarlo como abandonado también se fija la referencia desde la cual corre
el TTL:

```javascript
db.carritos.updateOne(
  { _id: carritoId },
  {
    $set: { estado: "ABANDONADO" },
    $currentDate: { ultimaActividad: true }
  }
);
```

El filtro parcial es importante: un TTL sin filtro borraría también carritos
activos o convertidos cuando envejezca su fecha.

## Índice de texto

El enunciado exige índices de texto "cuando se requiera búsqueda por contenido".
La colección `productos` (copia acotada de checkout en Mongo) tiene un campo
`nombre` de tipo `string` que es el candidato natural — el mismo nombre que
`CarritoMongoServicio` guarda en el snapshot de cada ítem del carrito.

```javascript
// mongo/indexes.js
database.productos.createIndex(
  { nombre: "text" },
  { name: "text_productos_nombre", default_language: "spanish" }
);
```

Con este índice se puede buscar por nombre directamente en mongosh:

```javascript
// Búsqueda por palabra clave — usa el índice de texto, sin collscan
db.productos.find(
  { $text: { $search: "notebook" } },
  { score: { $meta: "textScore" }, nombre: 1 }
).sort({ score: { $meta: "textScore" } })

// Resultado:
// { _id: 1, nombre: 'Notebook Empresarial Pro 15"', score: 0.75 }
```

El idioma `spanish` activa el stemmer en español de MongoDB: búsquedas como
`"impresora"` también encuentran `"impresoras"` y `"impresora láser"` sin
necesidad de wildcards. Solo puede existir un índice de tipo `text` por
colección en MongoDB; si en el futuro se quisiera buscar también por otros
campos de texto, hay que ampliar este mismo índice (e.g. `{ nombre: "text",
descripcion: "text" }`) en vez de crear uno nuevo.

## Ejecución y verificación

`mongo-init` ejecuta, en orden, el replica set, el validador y los índices. Los
scripts son idempotentes y se aplican automáticamente con `docker compose up`.

```javascript
db.facturas.getIndexes();
db.facturas_relacionales.getIndexes();
db.carritos.getIndexes();
db.productos.getIndexes();   // debe mostrar el índice de texto text_productos_nombre

// Historial del cliente 1. El campo difiere según la colección: embebido en
// el snapshot `cliente{}` en las facturas documentales, plano en la raíz en
// las relacionales.
db.facturas.find({ "cliente.clienteId": NumberLong(1) })
    .sort({ fechaEmision: -1 }).explain("executionStats");
db.facturas_relacionales.find({ clienteId: NumberLong(1) })
    .sort({ fechaEmision: -1 }).explain("executionStats");
```
