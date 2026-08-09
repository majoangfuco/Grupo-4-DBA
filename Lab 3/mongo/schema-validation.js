/** 
 *
 *  Reglas exigidas por el enunciado sobre la colección "carritos":
 *  1. La cantidad de cada ítem no puede superar el stock disponible.
 *  2. La cantidad de cada ítem no puede ser menor a la cantidad
 *    mínima de pedido B2B. Si no existe configuración especial para
 *    el producto, el mínimo efectivo es 1.
*/

const DB_NAME = process.env.MONGO_DB || "b2b";
const db = db.getSiblingDB(DB_NAME);

function log(msg) {
    print(`[schema-validation] ${msg}`);
}

// ─── Validador de la colección "carritos" ────────────────────────
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
                        "productoId",
                        "cantidad",
                        "precioUnitario",
                        "stockDisponibleAlAgregar"
                    ],
                    properties: {
                        productoId: { bsonType: ["int", "long"] },
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
if (db.getCollectionNames().includes("carritos")) {
    db.carritos.updateMany(
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
log(`validationLevel=${info.options.validationLevel}, validationAction=${info.options.validationAction}`);