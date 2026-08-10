# Tarea 4 — Aggregation Pipeline: Volumen de Ventas Proyectado

## 1. Objetivo del pipeline

Calcular el **volumen de ventas proyectado** por cliente y por categoría de producto a partir de los carritos activos. El término "proyectado" refleja que son ventas potenciales (el cliente tiene esos ítems en el carrito, con precio y cantidad ya elegidos) pero aún no confirmadas como órdenes.

El pipeline implementa la secuencia `$match → $unwind → $group → $sort → $bucket`, tal como exige el enunciado.

---

## 2. Colección de entrada: `carritos`

Cada documento de `carritos` en estado `ACTIVO` tiene la siguiente forma relevante para el pipeline:

```jsonc
{
  "_id": ObjectId("..."),
  "clienteId": 3,
  "estado": "ACTIVO",
  "items": [
    {
      "productoId": 7,
      "categoriaNombre": "Redes",
      "cantidad": 20,
      "precioUnitario": 150000.00,
      "subtotal": 3000000.00      // precioUnitario × cantidad, calculado al agregar el ítem
    },
    {
      "productoId": 12,
      "categoriaNombre": "Almacenamiento",
      "cantidad": 15,
      "precioUnitario": 280000.00,
      "subtotal": 4200000.00
    }
  ]
}
```

El campo `subtotal` es escrito por `CarritoMongoServicio` al momento de agregar cada ítem. Es el que el pipeline usa para acumular el volumen, **no** el `precioUnitario × cantidad` calculado en tiempo de agregación — esto garantiza que el volumen proyectado use el precio congelado al momento de armar el carrito.

---

## 3. Pipeline etapa por etapa

### Etapa 1: `$match` — filtrar carritos activos

```js
{ $match: { estado: "ACTIVO" } }
```

**Por qué:** solo los carritos en estado `ACTIVO` representan intención de compra vigente. Los carritos `ABANDONADO` (expirados o inactivos) y `CONVERTIDO` (ya procesados como órdenes) no son ventas proyectadas. Este filtro se aplica primero para reducir el conjunto de documentos antes de `$unwind`, que es la etapa más costosa en términos de cardinalidad.

El validador `$jsonSchema` de la colección garantiza que `estado` siempre sea uno de `[ACTIVO, ABANDONADO, CONVERTIDO]`, así que el filtro es seguro.

---

### Etapa 2: `$unwind` — desenrollar el array de ítems

```js
{ $unwind: "$items" }
```

**Por qué:** cada carrito tiene un array `items[]` con varios productos de distintas categorías. Para poder agrupar **por categoría**, primero hay que "aplanar" el array: `$unwind` genera un documento separado por cada elemento del array, manteniendo el `clienteId` del carrito padre.

**Efecto sobre la cardinalidad:** si hay 5 carritos activos con 3 ítems promedio cada uno, el conjunto pasa de 5 documentos a 15 antes de llegar a `$group`.

---

### Etapa 3: `$group` — agrupar por cliente y categoría

```js
{
  $group: {
    _id: {
      clienteId: "$clienteId",
      categoriaNombre: "$items.categoriaNombre"
    },
    volumenProyectado: { $sum: "$items.subtotal" }
  }
}
```

**Por qué esta clave de agrupación:** el enunciado pide calcular el volumen "por cliente y por categoría de producto". La clave compuesta `{ clienteId, categoriaNombre }` produce una fila por cada par (cliente, categoría), que es exactamente la granularidad pedida.

**`$sum` sobre `subtotal`:** acumula el total de dinero proyectado para ese cliente en esa categoría. Usar `subtotal` (que incluye la cantidad multiplicada por el precio unitario) en vez de `precioUnitario` garantiza que el volumen refleje la intención real del pedido.

**Resultado intermedio tras `$group` (ejemplo):**

```jsonc
{ "_id": { "clienteId": 3, "categoriaNombre": "Redes" },         "volumenProyectado": 7500000 }
{ "_id": { "clienteId": 3, "categoriaNombre": "Almacenamiento" },"volumenProyectado": 4200000 }
{ "_id": { "clienteId": 1, "categoriaNombre": "Computación" },   "volumenProyectado": 9500000 }
{ "_id": { "clienteId": 2, "categoriaNombre": "Insumos" },       "volumenProyectado": 775000  }
{ "_id": { "clienteId": 3, "categoriaNombre": "Periféricos" },   "volumenProyectado": 45000   }
```

---

### Etapa 4: `$sort` — ordenar por volumen descendente

```js
{ $sort: { volumenProyectado: -1 } }
```

**Por qué antes de `$bucket`:** MongoDB recomienda ordenar antes del `$bucket` para que las proyecciones dentro de cada bucket aparezcan ya ordenadas de mayor a menor. Si el `$sort` fuera después del `$bucket`, ordenaría los buckets entre sí, no las proyecciones dentro de cada bucket.

---

### Etapa 5: `$bucket` — clasificar en rangos de volumen

```js
{
  $bucket: {
    groupBy: "$volumenProyectado",
    boundaries: [0, 50000, 200000],    // define los límites inferiores de cada bucket
    default: "ALTO (>= 200000)",       // todo valor fuera de los boundaries va aquí
    output: {
      cantidad: { $sum: 1 },
      proyecciones: {
        $push: {
          clienteId: "$_id.clienteId",
          categoria: "$_id.categoriaNombre",
          volumen: "$volumenProyectado"
        }
      }
    }
  }
}
```

#### Definición de los rangos

| Bucket | Rango de `volumenProyectado` | `_id` en el resultado | Significado negocio |
|---|---|---|---|
| **BAJO** | `[0, 50.000)` | `0` | Pedidos de bajo valor, típicamente muestras o compras esporádicas |
| **MEDIO** | `[50.000, 200.000)` | `50000` | Pedidos regulares de clientes medianos |
| **ALTO** | `≥ 200.000` | `"ALTO (>= 200000)"` | Pedidos mayoristas de alto valor; clientes clave |

Los límites `[0, 50000, 200000]` se expresan en pesos chilenos (CLP) sin IVA. Los valores están calibrados para el catálogo de productos del sistema, donde los precios unitarios van desde $35.000 (resmas de papel) hasta $2.500.000 (servidor rack). Un carrito con un solo notebook ($1.200.000) ya caería en el bucket ALTO.

**¿Por qué `default` en vez de un cuarto límite?** El campo `default` captura todos los valores mayores que el último boundary (`200000`), evitando tener que poner un límite superior arbitrario (e.g., `200000, 9999999999`). Esto hace el pipeline robusto ante pedidos extraordinariamente grandes.

#### Estructura del resultado final

```jsonc
[
  {
    "_id": 0,                        // bucket BAJO (< $50.000)
    "cantidad": 1,
    "proyecciones": [
      { "clienteId": 3, "categoria": "Periféricos", "volumen": 45000 }
    ]
  },
  {
    "_id": 50000,                    // bucket MEDIO ($50.000–$200.000)
    "cantidad": 1,
    "proyecciones": [
      { "clienteId": 2, "categoria": "Insumos", "volumen": 775000 }
    ]
  },
  {
    "_id": "ALTO (>= 200000)",       // bucket ALTO (≥ $200.000)
    "cantidad": 3,
    "proyecciones": [
      { "clienteId": 1, "categoria": "Computación",   "volumen": 9500000 },
      { "clienteId": 3, "categoria": "Redes",         "volumen": 7500000 },
      { "clienteId": 3, "categoria": "Almacenamiento","volumen": 4200000 }
    ]
  }
]
```

---

## 4. Implementación en Java (capa de servicios)

El pipeline se construye con la API fluida del driver nativo `mongodb-driver-sync`, sin strings de consulta manuales:

```java
// MongoReporteServicio.java · obtenerVolumenVentasProyectado()

// 1. $match
Bson match = Aggregates.match(Filters.eq("estado", "ACTIVO"));

// 2. $unwind
Bson unwind = Aggregates.unwind("$items");

// 3. $group por (clienteId, categoriaNombre)
Document groupId = new Document("clienteId", "$clienteId")
                       .append("categoriaNombre", "$items.categoriaNombre");
Bson group = Aggregates.group(groupId,
    Accumulators.sum("volumenProyectado", "$items.subtotal")
);

// 4. $sort descendente
Bson sort = Aggregates.sort(Sorts.descending("volumenProyectado"));

// 5. $bucket con los 3 rangos
BucketOptions opts = new BucketOptions()
    .defaultBucket("ALTO (>= 200000)")
    .output(
        Accumulators.sum("cantidad", 1),
        Accumulators.push("proyecciones",
            new Document("clienteId", "$_id.clienteId")
                .append("categoria", "$_id.categoriaNombre")
                .append("volumen", "$volumenProyectado"))
    );
Bson bucket = Aggregates.bucket("$volumenProyectado",
    Arrays.asList(0, 50000, 200000), opts);

List<Bson> pipeline = Arrays.asList(match, unwind, group, sort, bucket);
carritosCol.aggregate(pipeline).into(resultados);
```

El endpoint que expone este pipeline es `GET /api/reportes/mongo/volumen-proyectado` (rol `ADMIN`), definido en `MongoReporteControlador.java`. La respuesta es el resultado directo de la agregación serializado como JSON.

---

## 5. Ejecución directa con mongosh

El mismo pipeline está disponible como script standalone para verificación:

```bash
mongosh "mongodb://b2b_app:b2b_app_pass@localhost:27017/b2b?authSource=admin&replicaSet=rs0" \
    --file mongo/aggregation-pipeline.js
```

La salida es idéntica a la del endpoint REST, pero impresa en la consola con `printjson`.

---

## 6. Relación con la Vista Materializada (Tarea 6)

Este pipeline opera sobre **carritos activos** (ventas proyectadas, sin confirmar). La [Vista Materializada de la Tarea 6](06-change-streams-merge.md) opera sobre **órdenes confirmadas** (ventas reales). Son complementarios:

| Pipeline | Fuente | Estado | Resultado |
|---|---|---|---|
| Tarea 4 (`$bucket`) | `carritos` | `ACTIVO` | Ventas potenciales por cliente/categoría |
| Tarea 6 (`$merge`) | `ordenes` | `CONFIRMADA` | Ranking de productos más vendidos (datos reales) |
