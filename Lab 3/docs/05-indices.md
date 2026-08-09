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
| `facturas` | `{ clienteId: 1, fechaEmision: -1 }` | Resuelve el historial de un cliente desde la factura más reciente. |
| `carritos` | `{ ultimaActividad: 1 }`, TTL parcial | Elimina únicamente carritos con `estado: "ABANDONADO"` tras el plazo configurado. |

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

## Ejecución y verificación

`mongo-init` ejecuta, en orden, el replica set, el validador y los índices. Los
scripts son idempotentes y se aplican automáticamente con `docker compose up`.

```javascript
db.facturas.getIndexes();
db.carritos.getIndexes();

db.facturas.find({ clienteId }).sort({ fechaEmision: -1 }).explain("executionStats");
```
