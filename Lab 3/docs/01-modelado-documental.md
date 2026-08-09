# Modelado documental — Embedding vs Referencing

**Laboratorio 3 · Grupo 4 · Plataforma de E-Commerce B2B**

Este documento responde al primer requerimiento del enunciado:

> *Justifique e implemente si los ítems del carrito de compras deben embeberse dentro del documento del cliente/carrito o referenciarse contra el catálogo de productos, considerando la necesidad de mantener el precio histórico al momento de la compra.*

Se documenta primero el mapa completo de colecciones (el enunciado exige justificar la decisión **para cada colección**) y luego el análisis detallado del carrito, que es el caso pedido explícitamente.

---

## 1. Contexto: sistema híbrido PostGIS + MongoDB

El sistema mantiene la arquitectura del Laboratorio 2 (Spring Boot + PostgreSQL/PostGIS + Vue 3) y suma MongoDB como motor documental para los requerimientos NoSQL del Laboratorio 3.

| Motor | Responsabilidad |
|---|---|
| PostgreSQL + PostGIS | Lógica geoespacial heredada: comunas, unidades vecinales, almacenes, zonas de cobertura, cálculo de distancias y asignación de almacén. |
| MongoDB (Replica Set `rs0`) | Dominio documental del Lab 3: catálogo, carritos, órdenes, facturación, agregaciones de venta y vista materializada de productos más vendidos. |

Ambos motores conviven en el mismo `docker-compose.yml` y en el mismo backend. MongoDB no reemplaza a PostGIS: lo complementa.

---

## 2. Criterio de decisión aplicado

Para cada colección se aplicó la misma regla de tres preguntas:

1. **¿Se leen juntos?** Si el dato siempre se consulta junto al padre, embeber evita un `$lookup` o una segunda consulta.
2. **¿Crece sin límite?** Un arreglo embebido no acotado hace crecer el documento hacia el límite de 16 MB y provoca reubicaciones costosas en disco. Si crece sin techo, se referencia.
3. **¿Debe congelarse en el tiempo?** Si el dato tiene que quedar inmutable aunque la fuente cambie después (precios, datos tributarios), se embebe una **copia** aunque exista la referencia.

La respuesta rara vez es "embeber" o "referenciar" en estado puro: el diseño usa el patrón **Extended Reference** (referencia + copia parcial de los campos que se necesitan leer o congelar).

---

## 3. Mapa de colecciones

| Colección | Decisión | Justificación breve |
|---|---|---|
| `clientes` | Embebe `direcciones[]` y `condicionesComerciales`; referencia lógica a las comunas de PostGIS por `comunaId` | Un cliente B2B tiene pocas direcciones de despacho (cardinalidad acotada) y siempre se leen junto a la ficha. Las condiciones comerciales (cantidad mínima de pedido, descuento por volumen) son 1:1 con el cliente. |
| `categorias` | Colección propia, referenciada desde `productos` con copia del nombre | Son pocas decenas de documentos, se editan raramente y se listan solas en el menú del catálogo. Se copia `categoria.nombre` dentro del producto para no hacer `$lookup` en cada listado. |
| `productos` | Colección propia. Campo `stock` escalar (~~`stockPorAlmacen[]`~~, ver nota) | El catálogo es la entidad más referenciada del sistema: duplicarlo sería inmantenible. El stock se lee y descuenta siempre junto al producto (actualización atómica de un solo documento). |
| `carritos` | Colección propia. **Embebe `items[]` con snapshot de precio** | Ver sección 4. |
| `ordenes` | Colección propia. **Embebe `items[]` (snapshot congelado) y `cliente` (snapshot)** | Una orden es un hecho histórico inmutable: debe poder reimprimirse años después exactamente como fue, aunque el producto se elimine o el cliente cambie de razón social. |
| `facturas` | Colección propia, referencia `ordenId` | Documento tributario con ciclo de vida y numeración propios (índice único sobre `numeroFactura`). Separarla permite emitir, anular o refacturar sin tocar la orden. |
| `productos_mas_vendidos` | Colección materializada (destino de `$merge`) | No es fuente de verdad: la reconstruye el change stream sobre órdenes confirmadas. Se aísla para que las lecturas del dashboard no compitan con el pipeline de agregación. |

> **Nota — `stockPorAlmacen[]` descartado para el alcance de Lab 3.** Una
> versión anterior de este documento proponía embeber `stockPorAlmacen[]`
> (stock por almacén) dentro de `productos`. Esa propuesta **nunca se
> implementó**: no llegó a tener JSON de ejemplo comprometido ni código
> (Java o Mongo) que la leyera o escribiera. Se descarta explícitamente
> porque el enunciado de este laboratorio solo pide "descontar el stock
> del producto" — sin granularidad por almacén — y porque **ese control ya
> existe y sigue funcionando en Postgres**: la tabla
> `stock_almacen_producto_entidad` y el `PROCEDURE procesar_checkout`
> (`backendB2B/init.sql`) resuelven la asignación de almacén y el
> descuento de stock por almacén como parte del checkout geoespacial del
> Lab 2, en una base de datos separada que este laboratorio no reemplaza.
> `productos.stock` en MongoDB es un campo **escalar** propio del dominio
> documental del Lab 3, sin relación con esa tabla. El detalle de esta
> decisión y el schema resultante están en
> [`docs/03-checkout-transaccion.md`](03-checkout-transaccion.md).

---

## 4. El caso pedido: ítems del carrito

### 4.1 Las tres alternativas evaluadas

**A. Carrito embebido dentro del documento del cliente.** Descartada.

- El índice **TTL** exigido por el enunciado (limpieza automática de carritos abandonados) expira **el documento completo**, no un subdocumento. Embeber el carrito en el cliente significaría que expirar un carrito abandonado **borra la cuenta del cliente**. Esto por sí solo invalida la alternativa.
- Un cliente B2B puede acumular muchos carritos a lo largo del tiempo; el arreglo crecería sin techo.
- La escritura del carrito es altísima (cada clic de "+1 unidad") mientras que la ficha del cliente es casi de solo lectura. Mezclarlas hace que cada modificación del carrito reescriba y bloquee un documento que otros procesos están leyendo.

**B. Ítems en una colección `carrito_items` aparte, referenciando el catálogo.** Descartada.

- Reproduce el modelo relacional del Lab 2 (`carrito_producto_entidad`) dentro de un motor documental, perdiendo su principal ventaja.
- Mostrar el carrito pasa a ser: 1 consulta al carrito + 1 consulta a los ítems + 1 `$lookup` al catálogo. Es la operación más frecuente de toda la aplicación.
- Se pierde la atomicidad natural: agregar un ítem y recalcular el total del carrito serían escrituras en documentos distintos, obligando a abrir una transacción para algo tan trivial como sumar una unidad.
- El validador `$jsonSchema` de cantidad mínima de pedido B2B (punto 2 del enunciado) necesita ver **todos** los ítems juntos para evaluar la regla; con los ítems dispersos, el validador de colección no puede expresarla.

**C. Ítems embebidos en el documento `carritos`, con snapshot de precio y `productoId` como referencia al catálogo. ✅ ADOPTADA.**

### 4.2 Por qué el snapshot de precio (y no leer el precio del catálogo)

Este es el núcleo de la justificación. El precio **no** se resuelve por referencia en el momento de leer el carrito, sino que se **copia dentro del ítem** al agregarlo:

1. **Precio histórico / trazabilidad legal.** En un mayorista B2B el precio no es un dato de presentación: es el valor que se factura. Si el carrito solo guardara `productoId` y el precio se leyera del catálogo al momento del checkout, un cambio de lista de precios entre el martes y el jueves alteraría retroactivamente lo que el cliente vio y aceptó. La factura tiene efectos tributarios: debe reflejar el precio pactado, no el vigente.

2. **Precio negociado por cliente.** El precio de un ítem B2B no siempre es el del catálogo: depende del descuento por volumen y de las condiciones comerciales del cliente. Ese precio efectivo **no existe en el catálogo**, se calcula al agregar el ítem. Sin snapshot, sería irreproducible.

3. **Patrón de acceso.** Ver el carrito, recalcular el total y renderizar el checkout se resuelven con **una sola lectura de un solo documento**, sin `$lookup` contra un catálogo de miles de productos. Es la consulta más frecuente del sistema.

4. **Cardinalidad acotada.** Un carrito B2B realista tiene decenas de líneas, no millones. Con ~200 bytes por ítem, el límite de 16 MB de BSON no es una restricción práctica. El arreglo tiene techo natural: al confirmar, el carrito se convierte en orden y deja de crecer.

5. **Atomicidad sin transacción.** Las escrituras sobre un único documento son atómicas por definición en MongoDB. Agregar un ítem, actualizar su cantidad y refrescar `ultimaActividad` es **un** `updateOne`. Las transacciones multi-documento quedan reservadas para donde de verdad hacen falta: el checkout (punto 3).

6. **Habilita el TTL.** Al ser documento propio, `carritos` puede llevar un índice TTL sobre `ultimaActividad`, refrescado en cada operación del cliente. El carrito abandonado se limpia solo, sin tocar cliente ni catálogo.

### 4.3 El costo asumido (y su mitigación)

Embeber duplica datos, y la duplicación puede quedar obsoleta. Se asume conscientemente:

| Riesgo | Mitigación |
|---|---|
| El nombre o precio del producto cambia y el carrito muestra el valor viejo | Es el comportamiento **deseado** para el precio. Para nombre/imagen, el snapshot es solo de presentación y se refresca al revalidar el carrito. |
| El precio snapshot está desactualizado al hacer checkout | La transacción de checkout **revalida** `precioUnitario` contra el catálogo. Si difiere, se marca el ítem y se pide confirmación explícita al cliente antes de facturar. El snapshot manda solo mientras esté vigente (`precioVigenteHasta`). |
| El producto se da de baja mientras está en un carrito | La revalidación del checkout detecta `activo: false` y bloquea la orden con un mensaje al cliente. |
| El stock cambia después de agregar el ítem | El validador `$jsonSchema` verifica la cantidad al escribir, pero **el stock real vive en `productos`**: la garantía dura se aplica en la transacción de checkout, con `readConcern: "snapshot"` y descuento condicional (`stock >= cantidad`). Ver punto 3. |

**Se conserva siempre `productoId`.** El snapshot no reemplaza la referencia: la acompaña. Gracias a `productoId` el checkout puede descontar stock, y los pipelines de agregación pueden agrupar por categoría o hacer `$lookup` al catálogo cuando se necesita el dato *actual* y no el histórico.

### 4.4 Estructura resultante

```jsonc
// Colección: carritos
{
  "_id": ObjectId("..."),
  "clienteId": ObjectId("..."),          // referencia a clientes
  "estado": "ACTIVO",                     // ACTIVO | ABANDONADO | CONVERTIDO
  "items": [                              // ── EMBEBIDO ──
    {
      "productoId": ObjectId("..."),      // referencia al catálogo (se conserva)
      "sku": "SKU-00123",                 // snapshot de presentación
      "nombreProducto": "Caja guantes nitrilo T-M",
      "categoriaId": ObjectId("..."),
      "categoriaNombre": "Insumos médicos",
      "cantidad": 120,
      "precioUnitario": 8990,             // ── SNAPSHOT DE PRECIO ──
      "descuentoAplicado": 0.05,          // negociado con este cliente
      "precioEfectivo": 8540.5,
      "subtotal": 1024860,
      "cantidadMinimaB2B": 50,            // copiada para validar en el mismo doc
      "agregadoEn": ISODate("..."),
      "precioVigenteHasta": ISODate("...")
    }
  ],
  "totalNeto": 1024860,
  "iva": 194723,
  "total": 1219583,
  "creadoEn": ISODate("..."),
  "ultimaActividad": ISODate("...")       // ← campo Date que alimenta el índice TTL
}
```

> **Nota para quien implemente el punto 5 (índices):** `ultimaActividad` debe ser de tipo `Date` (BSON date) y **refrescarse con `$currentDate` en cada operación del carrito**. Un TTL sobre un campo que no se actualiza expiraría carritos activos.

### 4.5 Diferencia entre el snapshot del carrito y el de la orden

Aunque la estructura de `items[]` se parece, el significado cambia:

- En **`carritos`** el snapshot es **provisional**: se revalida en el checkout y puede corregirse.
- En **`ordenes`** el snapshot es **definitivo**: una vez confirmada la orden, `items[]` no se modifica nunca más. Ahí el embedding no es una optimización de lectura, es un requisito de integridad histórica.

Por eso la orden embebe además un snapshot del cliente (razón social, RUT, dirección de despacho): la factura debe poder reimprimirse idéntica aunque el cliente cambie sus datos después.

---

## 5. Resumen ejecutivo

> Los ítems del carrito se **embeben dentro del documento `carritos`** —no dentro del cliente y no en una colección aparte— conservando `productoId` como referencia al catálogo y **copiando el precio al momento de agregar el ítem**.
>
> Se embebe porque los ítems se leen siempre junto al carrito, su cantidad está acotada y así las modificaciones son atómicas sobre un solo documento. Se guarda un carrito como documento independiente (y no dentro del cliente) porque el índice **TTL** de carritos abandonados expira documentos completos. Y se congela el precio porque en un mayorista B2B el precio es un compromiso comercial con efectos tributarios, además de depender de descuentos negociados que no existen en el catálogo.
