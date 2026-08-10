# Tarea 2 — Schema Validation ($jsonSchema + $expr)

## 1. Contexto y motivación

MongoDB no impone un esquema rígido por defecto: cualquier documento puede insertarse en cualquier colección con cualquier forma. Para una plataforma B2B esto es inadmisible — dos reglas de negocio son críticas para la integridad del inventario:

1. **No se puede agregar al carrito una cantidad mayor al stock disponible** (sobreventa).
2. **No se puede agregar una cantidad menor al pedido mínimo B2B del producto** (incumplimiento de condición comercial mayorista).

Ambas reglas comparan campos del **mismo documento** (la cantidad pedida contra el stock y contra el mínimo B2B). El operador `$jsonSchema` solo puede validar tipos, rangos y presencia de campos de forma estática; para comparaciones entre campos del mismo documento se necesita `$expr`. La solución combina ambos en un validador compuesto `{ $and: [ $jsonSchema, $expr ] }`.

El script se encuentra en [`mongo/schema-validation.js`](../mongo/schema-validation.js) y es aplicado automáticamente por el servicio `mongo-init` al levantar el stack con `docker compose up`.

---

## 2. Colecciones con validador

### 2.1 `carritos` — Reglas completas (estructura + negocio)

Esta es la colección crítica: tiene tanto el validador de estructura (`$jsonSchema`) como las reglas dinámicas (`$expr`).

#### 2.1.1 `$jsonSchema` — validador de estructura estática

```js
// mongo/schema-validation.js · líneas 21-84
const carritoValidator = {
  $jsonSchema: {
    bsonType: "object",
    required: ["clienteId", "estado", "items", "ultimaActividad"],
    properties: {
      clienteId:       { bsonType: ["int", "long"] },
      estado:          { enum: ["ACTIVO", "ABANDONADO", "CONVERTIDO"] },
      ultimaActividad: { bsonType: "date" },       // alimenta el índice TTL
      items: {
        bsonType: "array",
        items: {
          bsonType: "object",
          required: ["itemId","productoId","cantidad","precioUnitario","stockDisponibleAlAgregar"],
          properties: {
            productoId:             { bsonType: ["int","long"], minimum: 1 },
            cantidad:               { bsonType: ["int","long"], minimum: 1 },
            precioUnitario:         { bsonType: ["double","decimal"], minimum: 0 },
            cantidadMinimaB2B:      { bsonType: ["int","long"],  minimum: 1 },
            stockDisponibleAlAgregar: { bsonType: ["int","long"], minimum: 0 }
          }
        }
      }
    }
  }
};
```

| Campo | Tipo exigido | Regla adicional | Justificación |
|---|---|---|---|
| `clienteId` | `int` o `long` | — | Referencia lógica al `usuario_id` de PostgreSQL |
| `estado` | `string` | `enum: [ACTIVO, ABANDONADO, CONVERTIDO]` | Máquina de estados del carrito; impide estados arbitrarios |
| `ultimaActividad` | `date` | — | BSON Date requerido para que el índice TTL funcione correctamente |
| `items[].cantidad` | `int` o `long` | `minimum: 1` | No se puede agregar 0 o negativo |
| `items[].precioUnitario` | `double` o `decimal` | `minimum: 0` | Snapshot de precio; no puede ser negativo |
| `items[].stockDisponibleAlAgregar` | `int` o `long` | `minimum: 0` | Snapshot de stock al momento de agregar; 0 es válido (el $expr luego lo rechaza si la cantidad > 0) |
| `items[].cantidadMinimaB2B` | `int` o `long` | `minimum: 1` | El mínimo comercial B2B nunca es menor a 1 |

#### 2.1.2 `$expr` — reglas de negocio B2B

`$jsonSchema` no puede comparar dos campos entre sí. Para eso se usa `$expr` con el operador `$allElementsTrue` aplicado sobre `$map` del array de ítems:

```js
// mongo/schema-validation.js · líneas 91-119
const reglasDeNegocio = {
  $expr: {
    $and: [
      // REGLA 1: cantidad ≤ stock disponible al agregar
      {
        $allElementsTrue: {
          $map: {
            input: "$$items",
            as: "it",
            in: { $lte: ["$$it.cantidad", "$$it.stockDisponibleAlAgregar"] }
          }
        }
      },
      // REGLA 2: cantidad ≥ mínimo B2B del producto
      {
        $allElementsTrue: {
          $map: {
            input: "$items",
            as: "it",
            in: { $gte: ["$$it.cantidad", { $ifNull: ["$$it.cantidadMinimaB2B", 1] }] }
          }
        }
      }
    ]
  }
};
```

**Por qué `$ifNull`:** `cantidadMinimaB2B` es un campo opcional en el documento de ítem (puede llegar como `null` si el item se creó antes de que la configuración B2B existiera). El `$ifNull` garantiza que el fallback sea `1` — el mismo default que usa `CarritoMongoServicio.MINIMO_POR_DEFECTO` en Java.

#### 2.1.3 Fusión en validador compuesto

```js
// mongo/schema-validation.js · línea 126
const validator = { $and: [carritoValidator, reglasDeNegocio] };
```

El `$and` exige que ambas condiciones se cumplan simultáneamente. Si falla el `$jsonSchema`, MongoDB rechaza la escritura antes de evaluar el `$expr` (más eficiente).

#### 2.1.4 Opciones: nivel y acción

```js
validationLevel:  "strict",   // valida tanto inserts como updates
validationAction: "error"     // rechaza la escritura, no solo advierte
```

Se eligió `error` (y no `warn`) porque ambas reglas son **reglas duras de negocio**:
- `warn` permitiría insertar el documento y solo lo registraría en el log — la sobreventa ocurriría igualmente.
- `strict` (en vez de `moderate`) garantiza que incluso documentos ya existentes que se actualicen sean re-validados.

---

### 2.2 `productos` — Validador de la copia de checkout

```js
// mongo/schema-validation.js · líneas 201-229
const productoValidator = {
  $jsonSchema: {
    required: ["nombre", "precioUnitario", "stock", "cantidadMinimaB2B"],
    properties: {
      _id:               { bsonType: ["int","long"] },         // igual al producto_ID de Postgres
      precioUnitario:    { bsonType: ["double","decimal"], minimum: 0 },
      stock:             { bsonType: ["int","long"], minimum: 0 },
      cantidadMinimaB2B: { bsonType: ["int","long"], minimum: 1 }
    }
  }
};
```

> Esta colección no es el catálogo maestro (eso sigue siendo `producto_entidad` en PostgreSQL). Es una **copia acotada** que `CheckoutServicio` necesita para descontar stock con `$inc` condicional dentro de una transacción ACID de MongoDB — el driver no puede tocar PostgreSQL en el mismo commit/rollback.

El campo `_id` usa el mismo `Long` que `producto_ID` en Postgres para que el `updateOne({ _id: productoId, stock: { $gte: cantidad } })` del checkout no necesite ninguna tabla de mapeo.

---

### 2.3 `facturas` — Validador tributario

```js
// mongo/schema-validation.js · líneas 259-309
const facturaValidator = {
  $jsonSchema: {
    required: ["numeroFactura","ordenId","cliente","totalNeto","iva","total","estado","fechaEmision"],
    properties: {
      numeroFactura: { bsonType: "string", minLength: 1 },
      ordenId:       { bsonType: "objectId" },
      cliente: {
        bsonType: "object",
        required: ["clienteId","razonSocial","rutEmpresa"]
      },
      estado: { enum: ["EMITIDA", "ANULADA"] },
      fechaEmision:   { bsonType: "date" },
      fechaAnulacion: { bsonType: ["date", "null"] }
    }
  }
};
```

Los datos del cliente se embeben como **snapshot tributario** (`razonSocial`, `rutEmpresa`) en el momento de emitir la factura — si el cliente cambia sus datos después, la factura ya emitida queda inmutable. Esto implementa directamente el patrón "snapshot" descrito en [`docs/01-modelado-documental.md`](01-modelado-documental.md).

No hay `items[]` en la factura: el detalle de línea vive en `ordenes.items[]`. Duplicarlo crearía dos copias divergentes de la misma verdad histórica.

---

## 3. Ejemplos de documentos rechazados

### 3.1 Sobreventa — `cantidad > stockDisponibleAlAgregar`

```js
// Intento de inserción con cantidad (20) > stock (10)
db.carritos.insertOne({
  clienteId: NumberLong(1),
  estado: "ACTIVO",
  ultimaActividad: new Date(),
  items: [{
    itemId: NumberLong(1), productoId: NumberLong(5),
    cantidad: NumberLong(20),
    precioUnitario: NumberDecimal("450000"),
    cantidadMinimaB2B: NumberLong(1),
    stockDisponibleAlAgregar: NumberLong(10)   // ← 20 > 10, falla $expr
  }]
})
```

```
MongoServerError: Document failed validation
{
  "failingDocumentId": ObjectId("..."),
  "details": {
    "operatorName": "$expr",
    "reason": "Expression returned false"
  }
}
```

### 3.2 Incumplimiento de mínimo B2B — `cantidad < cantidadMinimaB2B`

```js
// Intento con cantidad (3) < mínimo B2B (5)
db.carritos.insertOne({
  clienteId: NumberLong(2),
  estado: "ACTIVO",
  ultimaActividad: new Date(),
  items: [{
    itemId: NumberLong(2), productoId: NumberLong(6),
    cantidad: NumberLong(3),                   // ← 3 < 5, falla $expr
    precioUnitario: NumberDecimal("85000"),
    cantidadMinimaB2B: NumberLong(5),
    stockDisponibleAlAgregar: NumberLong(2000)
  }]
})
```

```
MongoServerError: Document failed validation
{
  "failingDocumentId": ObjectId("..."),
  "details": {
    "operatorName": "$expr",
    "reason": "Expression returned false"
  }
}
```

### 3.3 Estado inválido — rechazado por `$jsonSchema`

```js
db.carritos.insertOne({
  clienteId: NumberLong(1),
  estado: "BORRADOR",          // ← no está en el enum
  ultimaActividad: new Date(),
  items: []
})
```

```
MongoServerError: Document failed validation
{
  "details": {
    "operatorName": "$jsonSchema",
    "schemaRulesNotSatisfied": [
      { "propertyName": "estado", "description": "value was not found in enum" }
    ]
  }
}
```

---

## 4. Aplicación del validador (idempotente)

El script detecta si la colección ya existe y aplica `collMod` (modificar) en vez de `createCollection` (crear):

```js
if (db.getCollectionNames().includes("carritos")) {
  db.runCommand({ collMod: "carritos", validator, validationLevel, validationAction });
} else {
  db.createCollection("carritos", { validator, validationLevel, validationAction });
}
```

Esto permite re-ejecutar `schema-validation.js` en cualquier momento sin borrar datos ni crear colecciones duplicadas. El servicio `mongo-init` lo ejecuta automáticamente en cada `docker compose up`.

### Verificación rápida

```js
// Comprobar el validador activo en mongosh
db.getCollectionInfos({ name: "carritos" })[0].options
// → { validator: { $and: [...] }, validationLevel: "strict", validationAction: "error" }
```
