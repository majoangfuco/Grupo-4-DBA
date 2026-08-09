# Vista materializada de "productos más vendidos" con Change Streams + `$merge`

**Laboratorio 3 · Grupo 4 · Plataforma de E-Commerce B2B · Punto 6**

> **Enunciado:** *"Genere una colección materializada (`$merge`) con los «productos
> más vendidos», actualizada reactivamente mediante Change Streams cada vez que se
> confirma una nueva orden."*

---

## 1. Qué se construyó

| Pieza | Archivo | Rol |
|---|---|---|
| Colección destino + validador + índices + backfill | [`mongo/change-streams-merge.js`](../mongo/change-streams-merge.js) | Script de BD (entregable): crea `productos_mas_vendidos`, `change_stream_checkpoints`, y corre el `$merge` completo una vez. |
| Pipeline `$merge` en Java | [`Services/ProductosMasVendidosServicio.java`](../backendB2B/src/main/java/com/ecommerceb2b/backend/Services/ProductosMasVendidosServicio.java) | Mismo pipeline que el script, ejecutable en caliente (modo completo e incremental) + lectura del ranking. |
| **Listener (proceso worker)** | [`Workers/ProductosMasVendidosWorker.java`](../backendB2B/src/main/java/com/ecommerceb2b/backend/Workers/ProductosMasVendidosWorker.java) | Change stream sobre `ordenes`; dispara el `$merge` ante cada orden `CONFIRMADA`. Corre **fuera** del server HTTP. |
| Disparador de negocio | [`Services/OrdenMongoServicio.java`](../backendB2B/src/main/java/com/ecommerceb2b/backend/Services/OrdenMongoServicio.java) + `OrdenMongoControlador` | `PATCH /api/ordenes/mongo/{id}/confirmar`: transición `PENDIENTE → CONFIRMADA`. |
| Consumo | `MongoReporteControlador` | `GET /api/reportes/mongo/productos-mas-vendidos` y `POST .../recalcular`. |
| Perfil del worker | `application-worker.properties` + servicio `worker` en `docker-compose.yml` | Mismo jar, sin Tomcat. |

---

## 2. Por qué un proceso worker aparte del server HTTP

Un change stream es un **cursor *tailing* que vive para siempre**: se abre una vez y
queda bloqueado esperando eventos. Eso no encaja en el ciclo request/response de un
controlador REST, y meterlo dentro del backend HTTP trae tres problemas concretos:

1. **Unicidad del listener.** Si el listener viviera dentro del backend, escalar el
   backend a *N* réplicas abriría *N* listeners consumiendo los mismos eventos y
   ejecutando el mismo `$merge` *N* veces. El worker es un servicio aparte que se
   despliega con **una sola instancia** por diseño.
2. **Aislamiento de fallos.** Si el oplog rota, el replica set pierde el PRIMARY o el
   `$merge` falla, el que se cae es el worker; la API sigue vendiendo. Y al revés: un
   redeploy del backend no interrumpe el procesamiento del stream.
3. **Ciclo de vida distinto.** El worker no tiene puertos ni rutas; su vida útil la
   define un hilo no-daemon, no un servidor web.

**Implementación:** es el **mismo jar** que el backend, arrancado con el perfil
`worker` (`SPRING_PROFILES_ACTIVE=worker`). Ese perfil pone
`spring.main.web-application-type=none`, así que el proceso levanta el contexto de
Spring **sin Tomcat**. `ProductosMasVendidosWorker` está anotado con
`@Profile("worker")` → el backend HTTP jamás lo instancia, y el worker no arrastra el
cargador de geometrías ni el scheduler de vistas PostGIS (ambos marcados
`@Profile("!worker")`).

```
┌──────────────┐  PATCH /confirmar   ┌───────────┐
│  backend     │────────────────────>│  ordenes  │  estado: CONFIRMADA
│  (HTTP:8090) │                     └─────┬─────┘
└──────────────┘                           │ oplog
                                           v
                                  ┌──────────────────┐
                                  │  worker          │  change stream
                                  │  (sin HTTP)      │
                                  └────────┬─────────┘
                                           │ aggregate([... $merge])
                                           v
                              ┌───────────────────────────┐
                              │ productos_mas_vendidos    │ <── GET /api/reportes/...
                              └───────────────────────────┘
```

---

## 3. El evento escuchado

El checkout ([`docs/03`](03-checkout-transaccion.md)) crea las órdenes en estado
`PENDIENTE`: el pago es un mock y la orden **todavía no es una venta**. El enunciado
pide reaccionar cuando *se confirma* una orden, así que el evento es la transición
`PENDIENTE → CONFIRMADA`, expuesta como `PATCH /api/ordenes/mongo/{id}/confirmar`.

Esa transición se hace con un `findOneAndUpdate` cuyo filtro incluye
`estado: "PENDIENTE"`, así que es **atómica y no re-confirmable**: dos requests
concurrentes producen una sola confirmación y, por lo tanto, un solo evento.

El filtro del stream se aplica **en el servidor**, no en el cliente:

```js
ordenes.watch(
  [{ $match: {
      operationType: { $in: ["insert", "update", "replace"] },
      "fullDocument.estado": "CONFIRMADA"
  }}],
  { fullDocument: "updateLookup" }
)
```

- `updateLookup` es imprescindible: un evento `update` trae solo el **delta**
  (`{estado: "CONFIRMADA"}`), sin los `items[]` que necesitamos para saber qué
  productos recalcular. Con `updateLookup` el servidor adjunta el documento completo.
- Se escuchan los tres tipos de operación: `update` es el caso real, `replace` cubre
  una reescritura completa del documento, e `insert` deja preparada una eventual ruta
  que cree la orden ya confirmada. `delete` no aplica (no trae `fullDocument`, y una
  orden borrada no es una venta nueva).

---

## 4. El pipeline `$merge`

```js
[
  { $match:  { estado: "CONFIRMADA" } },
  { $unwind: "$items" },
  // Solo en modo incremental (el worker pasa los productoId de la orden confirmada):
  { $match:  { "items.productoId": { $in: [ ...ids ] } } },
  { $sort:   { fechaOrden: 1 } },
  { $group:  {
      _id:                "$items.productoId",
      nombreProducto:     { $last: "$items.nombreProducto" },
      unidadesVendidas:   { $sum:  "$items.cantidad" },
      montoTotalVendido:  { $sum:  "$items.subtotal" },
      ordenesConfirmadas: { $sum:  1 },
      ultimaVentaEn:      { $max:  { $ifNull: ["$fechaConfirmacion", "$fechaOrden"] } }
  }},
  { $sort:    { unidadesVendidas: -1 } },
  { $project: { _id: 1, productoId: "$_id", nombreProducto: 1, unidadesVendidas: 1,
                montoTotalVendido: 1, ordenesConfirmadas: 1, ultimaVentaEn: 1,
                actualizadoEn: "$$NOW" } },
  { $merge:   { into: "productos_mas_vendidos", on: "_id",
                whenMatched: "replace", whenNotMatched: "insert" } }
]
```

### Decisiones que importan

**`_id = productoId`.** El `$merge` hace upsert `on: "_id"`, así que un producto ocupa
una sola fila del ranking *por construcción*, sin necesidad de un índice único extra.

**Recalcular en vez de acumular (`$sum` desde cero, no `$inc` del delta).** Aunque el
modo incremental toca solo los productos de la orden recién confirmada, **suma todas
sus órdenes confirmadas de nuevo**. Esto es deliberado: un change stream garantiza
entrega ***at-least-once***. Si el worker muere entre aplicar el `$merge` y guardar el
resume token, o si se reanuda desde un token ya procesado, el evento vuelve. Con un
`$inc` esa venta se contaría dos veces; recalculando, el resultado es **idempotente** y
el reproceso es inofensivo.

**`whenMatched: "replace"`** y no `"merge"`: el documento recalculado es el estado
completo del producto en el ranking, no un parche parcial.

**`$sort: { fechaOrden: 1 }` antes del `$group`** para que el `$last` de
`nombreProducto` sea el nombre de la venta **más reciente**. Los `items[]` guardan un
*snapshot* del nombre al momento de comprar (ver [`docs/01`](01-modelado-documental.md)),
así que órdenes viejas pueden traer otro texto; sin el `$sort`, `$last` devolvería uno
arbitrario.

**Sin transacción.** `$merge` no puede ejecutarse dentro de una transacción
multi-documento, y no hace falta: cada corrida deja la colección consistente por sí
sola.

### Shape resultante (validado con `$jsonSchema`)

```jsonc
{
  "_id": 15,
  "productoId": 15,
  "nombreProducto": "Resmas de Papel A4 (Caja de 10)",
  "unidadesVendidas": 340,
  "montoTotalVendido": NumberDecimal("6120000.00"),
  "ordenesConfirmadas": 12,
  "ultimaVentaEn": ISODate("2026-08-09T14:22:31Z"),
  "actualizadoEn": ISODate("2026-08-09T14:22:31Z")
}
```

El validador de `productos_mas_vendidos` usa `additionalProperties: false` y lista
exactamente los campos que emite el `$project`. Es un contrato real: si alguien cambia
el pipeline sin actualizar el validador, el `$merge` **falla** en vez de escribir
documentos con otro shape.

### Índices

| Índice | Para qué |
|---|---|
| `ix_masvendidos_unidadesVendidas` (`{unidadesVendidas: -1}`) | Ranking "top N por unidades" resuelto con `IXSCAN`, sin etapa `SORT` en memoria. Es la consulta del endpoint. |
| `ix_masvendidos_montoTotalVendido` (`{montoTotalVendido: -1}`) | Mismo ranking, ordenado por facturación. |

---

## 5. Reanudación: resume tokens

Un change stream solo ve lo que ocurre **desde que se abre**. Sin persistencia, cada
reinicio del worker perdería todas las órdenes confirmadas mientras estuvo caído.

Después de procesar cada evento, el worker guarda su *resume token* en
`change_stream_checkpoints`:

```jsonc
{
  "_id": "productos_mas_vendidos",
  "resumeToken": { "_data": "82650F..." },
  "actualizadoEn": ISODate("2026-08-09T14:22:31Z"),
  "eventosProcesados": 128
}
```

Al arrancar:

| Situación | Comportamiento |
|---|---|
| Hay checkpoint | `resumeAfter(token)`: se reprocesa exactamente desde el evento siguiente al último confirmado. |
| No hay checkpoint (primer arranque) | Abre el cursor **y luego** corre el backfill completo. Ese orden evita un hueco entre el backfill y el inicio de la escucha; si algún evento cae en medio, se procesa dos veces y no pasa nada (el `$merge` es idempotente). |
| El token quedó fuera del oplog (`ChangeStreamHistoryLost`, código 286) | El worker estuvo caído más que la ventana del oplog: no hay forma de saber qué se perdió. Borra el checkpoint, recalcula el ranking completo y abre un stream nuevo. |

**El checkpoint se guarda *después* de aplicar el `$merge`, nunca antes.** Si el worker
muere en medio, el evento se reprocesa (*at-least-once*) en lugar de perderse.
Reprocesar es seguro; perder una venta del ranking, no.

---

## 6. Cómo probarlo

```bash
# 1. Todo arriba (el servicio `worker` incluido)
docker compose up --build -d
docker compose logs -f worker      # dejar abierto en otra terminal

# 2. Login como CLIENTE y checkout -> devuelve ordenId, estado PENDIENTE
curl -X POST http://localhost:8090/api/checkout \
  -H "Authorization: Bearer $TOKEN_CLIENTE" -H "Content-Type: application/json" \
  -d '{"clienteId":1,"carritoId":1000000001,"razonSocial":"Comercial X SpA",
       "rutEmpresa":"76.543.210-9","direccionEnvio":"Av. Siempre Viva 742",
       "datosPago":{"aprobado":true,"referencia":"MOCK-1"}}'

# 3. Confirmar la orden (ADMIN) -> ESTE es el disparador
curl -X PATCH http://localhost:8090/api/ordenes/mongo/<ordenId>/confirmar \
  -H "Authorization: Bearer $TOKEN_ADMIN"

# 4. En los logs del worker aparece, en menos de un segundo:
#    Orden ORD-2026-000482 (...) confirmada -> 3 producto(s) recalculados en productos_mas_vendidos

# 5. Leer la vista materializada (ADMIN)
curl "http://localhost:8090/api/reportes/mongo/productos-mas-vendidos?limite=10" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Verificación directa en `mongosh`:

```bash
docker compose exec mongo1 mongosh -u b2b_app -p b2b_app_pass --authenticationDatabase admin b2b \
  --eval 'db.productos_mas_vendidos.find().sort({unidadesVendidas:-1}).limit(5).toArray()'

# El checkpoint avanzando confirma que el listener está vivo:
docker compose exec mongo1 mongosh -u b2b_app -p b2b_app_pass --authenticationDatabase admin b2b \
  --eval 'db.change_stream_checkpoints.find().toArray()'
```

**Prueba de la reanudación** (lo que distingue un listener real de un `while(true)`):

```bash
docker compose stop worker
# confirmar 2-3 órdenes más mientras está apagado
docker compose start worker
# en los logs: "Reanudando el change stream desde el último checkpoint guardado",
# y a continuación se procesan las órdenes confirmadas durante la caída.
```

---

## 7. Límites conocidos

- **Una sola instancia del worker.** No escalar el servicio `worker`: cada réplica
  abriría su propio listener y haría el mismo trabajo. Es redundante, no incorrecto
  (el `$merge` es idempotente), pero no aporta nada.
- **El ranking cuenta solo órdenes documentales de MongoDB.** Las órdenes relacionales
  del Lab 2 (`orden_entidad` en PostgreSQL, con asignación de almacén por PostGIS) son
  un sistema aparte que no se sincroniza — misma separación que documenta
  [`docs/03`](03-checkout-transaccion.md).
- **Una orden cancelada después de confirmarse no se descuenta hoy.** El pipeline
  filtra por `estado: "CONFIRMADA"`, así que el soporte ya existe a nivel de datos:
  bastaría con que la cancelación cambie el estado y con agregar `CANCELADA` al
  `$match` del `watch()` para que el worker recalcule esos productos. No está
  implementado porque el flujo de cancelación de órdenes documentales tampoco existe.
