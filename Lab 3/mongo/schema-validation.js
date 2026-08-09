
// Reglas de la colección "carritos":
//   1. Cantidad pedida <= Stock disponible.
//   2. Cantidad pedida >= Mínimo de pedido.

// Para poder validar, el backend debe adjuntar una copia del stock
// y precio en el carrito al momento de agregar el ítem.

const DB_NAME = process.env.MONGO_DB || "b2b";
const db = db.getSiblingDB(DB_NAME);

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
            // clienteId/productoId: Long (int/long), NO objectId. Se
            // corresponden 1:1 con usuario_ID / producto_ID de Postgres
            // (integración real con el catálogo/clientes existentes,
            // en vez de IDs Mongo inventados para la demo aislada).
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
                        "cantidadMinimaB2B",
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
                            description: "Copiada del catálogo: pedido mínimo B2B para este producto"
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
                        in: { $gte: ["$$it.cantidad", "$$it.cantidadMinimaB2B"] }
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
    // Validar inserts Y updates
    validationLevel: "strict",
    // Rechazar si la escritura no cumple con el validador.
    // (sobreventa / incumplimiento de pedido mínimo)
    validationAction: "error"
};

// ─── Aplicar validador ─────────────────────────────
const coleccionesExistentes = db.getCollectionNames();

if (coleccionesExistentes.includes("carritos")) {
    db.runCommand({ collMod: "carritos", ...opcionesValidacion });
    log('Validador aplicado sobre la colección "carritos" existente (collMod).');
} else {
    db.createCollection("carritos", opcionesValidacion);
    log('Colección "carritos" creada con validador (createCollection).');
}

// ─── Verificación rápida ─────────────────────────────────────────
const info = db.getCollectionInfos({ name: "carritos" })[0];
log(`Validador activo (Nivel: ${info.options.validationLevel} | Acción: ${info.options.validationAction}). La colección "carritos" está lista y protegida.`);

// ──// ─── Facturas documentales ────────────────────────────────────────────────
     const facturaValidator = {
         $jsonSchema: {
             bsonType: "object",
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
                     minLength: 1
                 },
                 clienteId: {
                     bsonType: ["int", "long"],
                     minimum: 1
                 },
                 ordenId: {
                     bsonType: ["int", "long"],
                     minimum: 1
                 },
                 fechaEmision: {
                     bsonType: "date"
                 },
                 precioTotal: {
                     bsonType: ["double", "decimal"],
                     minimum: 0
                 },
                 totalNeto: {
                     bsonType: ["double", "decimal"],
                     minimum: 0
                 },
                 iva: {
                     bsonType: ["double", "decimal"],
                     minimum: 0
                 },
                 costoEnvio: {
                     bsonType: ["double", "decimal"],
                     minimum: 0
                 },
                 items: {
                     bsonType: "array",
                     items: {
                         bsonType: "object",
                         required: [
                             "productoId",
                             "nombreProducto",
                             "precioUnitario",
                             "cantidad"
                         ],
                         properties: {
                             productoId: {
                                 bsonType: ["int", "long"],
                                 minimum: 1
                             },
                             nombreProducto: {
                                 bsonType: "string"
                             },
                             sku: {
                                 bsonType: ["string", "null"]
                             },
                             precioUnitario: {
                                 bsonType: ["double", "decimal"],
                                 minimum: 0
                             },
                             cantidad: {
                                 bsonType: ["int", "long"],
                                 minimum: 1
                             }
                         }
                     }
                 }
             }
         }
     };

     if (db.getCollectionNames().includes("facturas")) {
         db.runCommand({
             collMod: "facturas",
             validator: facturaValidator,
             validationLevel: "strict",
             validationAction: "error"
         });

         log('Validador actualizado en la colección "facturas".');
     } else {
         db.createCollection("facturas", {
             validator: facturaValidator,
             validationLevel: "strict",
             validationAction: "error"
         });

         log('Colección "facturas" creada con su validador.');
     }