/** 
 *
 *  Reglas exigidas por el enunciado sobre la colección "carritos":
 *  1. La cantidad de cada ítem no puede superar el stock disponible.
 *  2. La cantidad de cada ítem no puede ser menor a la cantidad
 *    mínima de pedido B2B. Si no existe configuración especial para
 *    el producto, el mínimo efectivo es 1.
*/

const DB_NAME = process.env.MONGO_DB || "b2b";
const database = db.getSiblingDB(DB_NAME);

// SIEMPRE usar `database`, NUNCA el `db` suelto de mongosh. docker-compose
// invoca este script con `mongosh --host mongo1:27017 -u root ...`, sin base
// en la cadena de conexión, así que `db` apunta a `test` y no a `b2b` (los
// seeders sí usan una URI con /b2b, por eso ellos no tenían el problema).
// Con `db` suelto, cada collMod/createCollection creaba una colección
// fantasma vacía en `test` y la base real se quedaba con el validador de la
// última revisión que sí acertó — es decir, la Tarea 2 quedaba sin aplicar
// sobre `b2b` sin dar ningún error visible.

function log(msg) {
    print(`[schema-validation] ${msg}`);
}

// ─── Validador de la colección "carritos" ($jsonSchema) ────────────────────────
// Define tipos de datos, campos requeridos y enumeradores básicos.

const carritoValidator = {
    $jsonSchema: {
        bsonType: "object",
        title: "Validación de estructura del carrito",
        required: ["clienteId", "estado", "items", "ultimaActividad"],
        properties: {
        
            clienteId: { bsonType: ["int", "long"] },
            estado: {
                enum: ["ACTIVO", "ABANDONADO", "CONVERTIDO"],
                description: "Debe ser uno de los 3 estados válidos del carrito"
            },
            ultimaActividad: {
                bsonType: "date",
                description: "Requerido y de tipo Date: alimenta el índice TTL (punto 5)"
            },
            items: {
                bsonType: "array",
                description: "Ítems embebidos con snapshot de precio y stock",
                items: {
                    bsonType: "object",
                    required: [
                        "itemId",
                        "productoId",
                        "cantidad",
                        "precioUnitario",
                        "stockDisponibleAlAgregar"
                    ],
                    properties: {
                        productoId: {
                            bsonType: ["int", "long"],
                            minimum: 1
                        },
                        itemId: {
                            bsonType: ["int", "long"],
                            minimum: 1
                        },
                        sku: { bsonType: "string" },
                        nombreProducto: { bsonType: "string" },
                        cantidad: {
                            bsonType: ["int", "long"],
                            minimum: 1,
                            description: "Entero positivo: cantidad solicitada del ítem"
                        },
                        precioUnitario: {
                            bsonType: ["double", "decimal"],
                            minimum: 0,
                            description: "Snapshot de precio al momento de agregar el ítem"
                        },
                        cantidadMinimaB2B: {
                            bsonType: ["int", "long"],
                            minimum: 1,
                            description: "Pedido mínimo B2B para este producto; si no hay configuración especial, se toma 1"
                        },
                        stockDisponibleAlAgregar: {
                            bsonType: ["int", "long"],
                            minimum: 0,
                            description: "Snapshot de stock disponible al momento de agregar el ítem"
                        }
                    }
                }
            }
        }
    }
};

// ─── Regla de negocio: comparación entre campos del MISMO ítem ──
// $jsonSchema no permite comparar dos campos del mismo documento.
// Por lo tanto usamos $expr para habilitar operadores lógicos ($lte, $gte)
// y comparar la cantidad solicitada contra el stock y el mínimo B2B.
const reglasDeNegocio = {
    $expr: {
        $and: [
            // Ningún ítem puede pedir más de lo que había disponible al agregarlo.
            {
                $allElementsTrue: {
                    $map: {
                        input: "$items",
                        as: "it",
                        in: { $lte: ["$$it.cantidad", "$$it.stockDisponibleAlAgregar"] }
                    }
                }
            },
            // Ningún ítem puede quedar bajo el mínimo de pedido B2B.
            {
                $allElementsTrue: {
                    $map: {
                        input: "$items",
                        as: "it",
                        in: {
                            $gte: [
                                "$$it.cantidad",
                                { $ifNull: ["$$it.cantidadMinimaB2B", 1] }
                            ]
                        }
                    }
                }
            }
        ]
    }
};

// ───  Fusión de Validadores ($and) ─────────────────────────────
// Se exige que el documento cumpla tanto la estructura estática
// como las reglas dinámicas para ser insertado/actualizado.

const validator = { $and: [carritoValidator, reglasDeNegocio] };

const opcionesValidacion = {
    validator: validator,
    // "strict": valida inserts Y updates.
    validationLevel: "strict",
    // "error": rechaza la escritura si no cumple: es una regla dura de
    // negocio (sobreventa / incumplimiento de pedido mínimo), no una
    // advertencia.
    validationAction: "error"
};

// ─── Normalización de documentos existentes ─────────────────────
if (database.getCollectionNames().includes("carritos")) {
    database.carritos.updateMany(
        { items: { $type: "array" } },
        [
            {
                $set: {
                    items: {
                        $map: {
                            input: "$items",
                            as: "it",
                            in: {
                                $mergeObjects: [
                                    "$$it",
                                    {
                                        cantidadMinimaB2B: {
                                            $ifNull: ["$$it.cantidadMinimaB2B", 1]
                                        }
                                    }
                                ]
                            }
                        }
                    }
                }
            }
        ]
    );
}

// ─── Aplicar validador (idempotente) ─────────────────────────────
const coleccionesExistentes = database.getCollectionNames();

if (coleccionesExistentes.includes("carritos")) {
    database.runCommand({ collMod: "carritos", ...opcionesValidacion });
    log('Validador aplicado sobre la colección "carritos" existente (collMod).');
} else {
    database.createCollection("carritos", opcionesValidacion);
    log('Colección "carritos" creada con validador (createCollection).');
}
// ─── Verificación rápida ─────────────────────────────────────────
const info = database.getCollectionInfos({ name: "carritos" })[0];
log(`Validador activo (Nivel: ${info.options.validationLevel} | Acción: ${info.options.validationAction}). La colección "carritos" está lista y protegida.`);

// ═══════════════════════════════════════════════════════════════
// Colección "productos" — SOLO para CheckoutServicio
//
// No es el catálogo (ese sigue siendo producto_entidad en Postgres,
// que consultan ProductoServicio/ProductoRepositorio, y del que lee
// CarritoMongoServicio.agregarItem para armar el snapshot del ítem del
// carrito). Esta colección es una copia acotada, exclusiva de la
// transacción ACID de checkout (CheckoutServicio): el driver de Mongo
// solo puede hacer $inc condicional (updateOne con stock: {$gte: N})
// dentro de una transacción multi-documento sobre datos que YA viven
// en Mongo — no puede tocar Postgres en el mismo commit/abort. De ahí
// la copia, en vez de leer/descontar el stock real.
//
// `_id` es el MISMO valor que producto_ID en Postgres (Long), no un
// ObjectId nuevo: es lo que permite poblarla 1:1 desde
// mongo/seeders/productos-seed.js y lo que hace que
// carritos.items[].productoId (también Long, ver bloque de arriba)
// calce directo como filtro { _id: productoId } sin tabla de mapeo.
// ═══════════════════════════════════════════════════════════════

const productoValidator = {
    $jsonSchema: {
        bsonType: "object",
        title: "Validación de estructura de producto (copia acotada para checkout)",
        required: ["nombre", "precioUnitario", "stock", "cantidadMinimaB2B"],
        properties: {
            _id: {
                bsonType: ["int", "long"],
                description: "Igual a producto_ID de Postgres (producto_entidad), no un ObjectId generado por Mongo"
            },
            nombre: { bsonType: "string" },
            precioUnitario: {
                bsonType: ["double", "decimal"],
                minimum: 0,
                description: "SIN IVA — CheckoutServicio aplica 19% sobre esto, no se recalcula acá"
            },
            stock: {
                bsonType: ["int", "long"],
                minimum: 0,
                description: "Lo único que CheckoutServicio descuenta ($inc condicional). No confundir con producto_entidad.stock de Postgres: son copias independientes."
            },
            cantidadMinimaB2B: {
                bsonType: ["int", "long"],
                minimum: 1,
                description: "Referencial, para que el shape sea consistente con carritos.items — CheckoutServicio no la vuelve a validar (ya se validó al armar el carrito)."
            }
        }
    }
};

const opcionesValidacionProductos = {
    validator: productoValidator,
    validationLevel: "strict",
    validationAction: "error"
};

if (coleccionesExistentes.includes("productos")) {
    database.runCommand({ collMod: "productos", ...opcionesValidacionProductos });
    log('Validador aplicado sobre la colección "productos" existente (collMod).');
} else {
    database.createCollection("productos", opcionesValidacionProductos);
    log('Colección "productos" creada con validador (createCollection).');
}

const infoProductos = database.getCollectionInfos({ name: "productos" })[0];
log(`productos: validationLevel=${infoProductos.options.validationLevel}, validationAction=${infoProductos.options.validationAction}`);

// ─── Órdenes documentales (checkout transaccional — CheckoutServicio) ──────
// Shape sacado directamente de CheckoutServicio.ejecutarCheckout() (no del
// diseño previo de docs/03, que quedó desactualizado — ver nota al inicio de
// ese doc). Dos escritores tocan esta colección:
//   - CheckoutServicio.ejecutarCheckout(): inserta el documento completo con
//     estado "PENDIENTE" (sin fechaConfirmacion todavía).
//   - OrdenMongoServicio.confirmar(): transición PENDIENTE -> CONFIRMADA,
//     agrega `fechaConfirmacion` (Date). Es el único otro estado que el
//     código real produce; el código nunca escribe "APROBADA" ni
//     "CANCELADA" pese a que docs/03 los menciona como diseño original.
const ordenValidator = {
    $jsonSchema: {
        bsonType: "object",
        title: "Orden documental emitida por el checkout transaccional (CheckoutServicio)",
        required: [
            "numeroOrden",
            "clienteId",
            "cliente",
            "carritoId",
            "estado",
            "items",
            "totalNeto",
            "iva",
            "total",
            "fechaOrden",
            "facturaId"
        ],
        properties: {
            numeroOrden: {
                bsonType: "string",
                minLength: 1,
                description: "Correlativo ORD-AAAA-NNNNNN, comparte secuencia con numeroFactura (CheckoutServicio.siguienteSecuencia)"
            },
            clienteId: { bsonType: ["int", "long"], minimum: 1 },
            cliente: {
                bsonType: "object",
                required: ["razonSocial", "rutEmpresa", "direccionEnvio"],
                description: "Snapshot congelado al momento del checkout (pedido.razonSocial/rutEmpresa/direccionEnvio)",
                properties: {
                    razonSocial: { bsonType: "string", minLength: 1 },
                    rutEmpresa: { bsonType: "string", minLength: 1 },
                    direccionEnvio: { bsonType: "string", minLength: 1 }
                }
            },
            carritoId: {
                bsonType: ["int", "long"],
                minimum: 1,
                description: "_id del carrito (colección carritos) que originó esta orden, no un ObjectId"
            },
            estado: {
                enum: ["PENDIENTE", "CONFIRMADA"],
                description: "PENDIENTE al crearse (CheckoutServicio); CONFIRMADA solo vía OrdenMongoServicio.confirmar(), que es lo que dispara el change stream del punto 6"
            },
            items: {
                bsonType: "array",
                minItems: 1,
                description: "Snapshot congelado e inmutable de las líneas compradas",
                items: {
                    bsonType: "object",
                    required: ["productoId", "nombreProducto", "cantidad", "precioUnitario", "subtotal"],
                    properties: {
                        productoId: { bsonType: ["int", "long"], minimum: 1 },
                        nombreProducto: { bsonType: "string" },
                        cantidad: { bsonType: ["int", "long"], minimum: 1 },
                        precioUnitario: { bsonType: ["double", "decimal"], minimum: 0 },
                        subtotal: { bsonType: ["double", "decimal"], minimum: 0 }
                    }
                }
            },
            totalNeto: { bsonType: ["double", "decimal"], minimum: 0 },
            iva: { bsonType: ["double", "decimal"], minimum: 0 },
            total: { bsonType: ["double", "decimal"], minimum: 0 },
            fechaOrden: { bsonType: "date" },
            fechaConfirmacion: {
                bsonType: ["date", "null"],
                description: "Solo presente después de OrdenMongoServicio.confirmar(); ausente mientras la orden está PENDIENTE"
            },
            facturaId: {
                bsonType: ["objectId", "int", "long"],
                description: "Referencia al _id de la factura gemela. ObjectId cuando la orden viene del checkout documental (facturas), Long cuando es el espejo del checkout relacional (facturas_relacionales)."
            },
            // ─── Campos solo del espejo del flujo relacional ─────────────
            // OrdenesServicio.solicitarOrdenAtomica() replica acá la orden que
            // creó procesar_checkout en Postgres. Sin este espejo la compra
            // real de la UI nunca entra a `ordenes`, y como el change stream
            // del punto 6 escucha ESTA colección, jamás llegaba a
            // productos_mas_vendidos por más que se apretara "Actualizar".
            origen: {
                enum: ["DOCUMENTAL", "RELACIONAL"],
                description: "DOCUMENTAL = CheckoutServicio; RELACIONAL = espejo de OrdenesServicio. Ausente en las órdenes previas a este campo (se asumen DOCUMENTAL)."
            },
            ordenRelacionalId: {
                bsonType: ["int", "long"],
                minimum: 1,
                description: "orden_id de ordenes_entidad (Postgres) que este documento espeja. Es la llave que usa confirmarEspejoRelacional() al aprobar la orden."
            }
        }
    }
};

if (coleccionesExistentes.includes("ordenes")) {
    database.runCommand({
        collMod: "ordenes",
        validator: ordenValidator,
        validationLevel: "strict",
        validationAction: "error"
    });
    log('Validador aplicado sobre la colección "ordenes" existente (collMod).');
} else {
    database.createCollection("ordenes", {
        validator: ordenValidator,
        validationLevel: "strict",
        validationAction: "error"
    });
    log('Colección "ordenes" creada con validador (createCollection).');
}

const infoOrdenes = database.getCollectionInfos({ name: "ordenes" })[0];
log(`ordenes: validationLevel=${infoOrdenes.options.validationLevel}, validationAction=${infoOrdenes.options.validationAction}`);

// ─── Facturas ─────────────────────────────────────────────────────────────
// RESUELTO (antes: "conflicto de arquitectura sin resolver"). Dos servicios
// del backend emiten facturas con shapes incompatibles entre sí:
//
//   1. FacturaRepositorio.java (flujo RELACIONAL, Lab 2 — el que usa HOY
//      el frontend real vía POST /api/ordenes/solicitar/{id}): escribe
//      _id Long, clienteId/datosPagoId/ordenId planos (Long), precioTotal,
//      costoEnvio, rutEmpresa plano, items[] embebidos con
//      {productoId,nombreProducto,sku,precioUnitario,cantidad}.
//
//   2. CheckoutServicio.java (flujo documental Mongo, punto 3 del Lab 3,
//      POST /api/checkout): escribe _id ObjectId,
//      cliente{clienteId,razonSocial,rutEmpresa} embebido, ordenId
//      ObjectId, estado/total, SIN items[] (el detalle vive en
//      ordenes.items — ver docs/03-checkout-transaccion.md §1.4).
//
// Como MongoDB solo admite UN $jsonSchema activo por colección, mientras
// ambos compartieron la colección `facturas` cualquier validador rompía una
// de las dos rutas con "Document failed validation" — no había forma de
// tener las dos andando a la vez. La salida es separarlas:
//
//   facturas               -> shape (2), documental (CheckoutServicio)
//   facturas_relacionales  -> shape (1), relacional (FacturaRepositorio)
//
// `facturas` conserva el nombre porque es la colección que describen
// docs/01-modelado-documental.md y docs/03-checkout-transaccion.md, y sobre
// la que indexes.js ya definía "cliente.clienteId" (campo que solo existe
// en el shape documental).
const facturaRelacionalValidator = {
    $jsonSchema: {
        bsonType: "object",
        title: "Factura del flujo relacional (FacturaRepositorio.java)",
        required: [
            "numeroFactura",
            "clienteId",
            "ordenId",
            "precioTotal",
            "fechaEmision",
            "totalNeto",
            "iva",
            "items"
        ],
        properties: {
            numeroFactura: {
                bsonType: "string",
                minLength: 1,
                description: "Correlativo tributario (FAC-NNNNNNNNNN, ver FacturaRepositorio.siguienteId). Índice único."
            },
            clienteId: {
                bsonType: ["int", "long"],
                minimum: 1,
                description: "usuario_ID de Postgres, plano en la raíz (no embebido en cliente{})"
            },
            datosPagoId: {
                bsonType: ["int", "long", "null"],
                description: "Referencia a datos_pago_entidad en Postgres; opcional"
            },
            ordenId: {
                bsonType: ["int", "long"],
                minimum: 1,
                description: "orden_ID de PostgreSQL (orden_entidad), NO un ObjectId de Mongo"
            },
            precioTotal: {
                bsonType: ["double", "decimal"],
                minimum: 0,
                description: "Total final (neto + IVA + envío) tal como lo calcula procesar_checkout en Postgres"
            },
            fechaEmision: { bsonType: "date" },
            totalNeto: { bsonType: ["double", "decimal"], minimum: 0 },
            iva: { bsonType: ["double", "decimal"], minimum: 0 },
            costoEnvio: { bsonType: ["double", "decimal"], minimum: 0 },
            rutEmpresa: {
                bsonType: ["string", "null"],
                description: "Copiado de usuario_entidad.rut_empresa al momento de emitir (FacturaRepositorio.obtenerRut)"
            },
            items: {
                bsonType: "array",
                description: "Detalle de línea embebido (a diferencia del shape documental, acá SÍ se duplica en la factura)",
                items: {
                    bsonType: "object",
                    required: ["productoId", "nombreProducto", "precioUnitario", "cantidad"],
                    properties: {
                        productoId: { bsonType: ["int", "long"], minimum: 1 },
                        nombreProducto: { bsonType: "string" },
                        sku: { bsonType: ["string", "null"] },
                        precioUnitario: { bsonType: ["double", "decimal"], minimum: 0 },
                        cantidad: { bsonType: ["int", "long"], minimum: 1 }
                    }
                }
            }
        }
    }
};

// Shape (2): el que emite CheckoutServicio dentro de la transacción del
// punto 3. `items[]` NO va acá: el detalle de línea vive en ordenes.items y
// la factura solo referencia la orden (docs/03-checkout-transaccion.md §1.4).
const facturaDocumentalValidator = {
    $jsonSchema: {
        bsonType: "object",
        title: "Factura documental emitida por el checkout transaccional (CheckoutServicio)",
        required: [
            "numeroFactura",
            "ordenId",
            "cliente",
            "totalNeto",
            "iva",
            "total",
            "estado",
            "fechaEmision"
        ],
        properties: {
            numeroFactura: {
                bsonType: "string",
                minLength: 1,
                description: "Correlativo tributario F-AAAA-NNNNNN (CheckoutServicio.siguienteSecuencia). Índice único."
            },
            ordenId: {
                bsonType: "objectId",
                description: "Referencia al _id de la orden documental que originó la factura"
            },
            cliente: {
                bsonType: "object",
                required: ["clienteId", "razonSocial", "rutEmpresa"],
                description: "Snapshot tributario del cliente, congelado al emitir. Sin direccionEnvio: eso es despacho, no dato tributario.",
                properties: {
                    clienteId: { bsonType: ["int", "long"], minimum: 1 },
                    razonSocial: { bsonType: "string", minLength: 1 },
                    rutEmpresa: { bsonType: "string", minLength: 1 }
                }
            },
            totalNeto: { bsonType: ["double", "decimal"], minimum: 0 },
            iva: { bsonType: ["double", "decimal"], minimum: 0 },
            total: { bsonType: ["double", "decimal"], minimum: 0 },
            montoTotal: {
                bsonType: ["double", "decimal"],
                minimum: 0,
                description: "Mismo valor que `total`; se conserva por fidelidad al shape de docs/03"
            },
            estado: {
                enum: ["EMITIDA", "ANULADA"],
                description: "EMITIDA al crearse; ANULADA requiere fechaAnulacion y motivoAnulacion"
            },
            fechaEmision: { bsonType: "date" },
            fechaAnulacion: { bsonType: ["date", "null"] },
            motivoAnulacion: { bsonType: ["string", "null"] }
        }
    }
};

// ─── Migración: sacar de `facturas` lo que es del flujo relacional ────────
// Hasta esta versión ambos flujos escribían en `facturas`, así que la
// colección puede traer documentos del shape (1) que el validador documental
// rechazaría. Se reconocen sin ambigüedad por `_id` numérico (el shape
// documental usa ObjectId). Idempotente: en una base limpia no mueve nada.
if (coleccionesExistentes.includes("facturas")) {
    const relacionalesEnColeccionEquivocada = database.facturas
        .find({ _id: { $type: ["int", "long", "double"] } })
        .toArray();

    if (relacionalesEnColeccionEquivocada.length > 0) {
        // Sin validador todavía en el destino: se crea abajo, ya con los
        // documentos adentro y cumpliendo el shape (1).
        database.facturas_relacionales.insertMany(relacionalesEnColeccionEquivocada);
        database.facturas.deleteMany({
            _id: { $in: relacionalesEnColeccionEquivocada.map((f) => f._id) }
        });
        log(`Migrados ${relacionalesEnColeccionEquivocada.length} documento(s) del flujo relacional de "facturas" a "facturas_relacionales".`);
    }
}

// ─── Aplicar ambos validadores ───────────────────────────────────────────
const coleccionesFactura = [
    { nombre: "facturas", validador: facturaDocumentalValidator },
    { nombre: "facturas_relacionales", validador: facturaRelacionalValidator }
];

// Se relee: la migración de arriba pudo crear "facturas_relacionales".
const coleccionesTrasMigracion = database.getCollectionNames();

for (const { nombre, validador } of coleccionesFactura) {
    const opciones = {
        validator: validador,
        validationLevel: "strict",
        validationAction: "error"
    };

    if (coleccionesTrasMigracion.includes(nombre)) {
        database.runCommand({ collMod: nombre, ...opciones });
        log(`Validador actualizado en la colección "${nombre}".`);
    } else {
        database.createCollection(nombre, opciones);
        log(`Colección "${nombre}" creada con su validador.`);
    }
}