// MongoDB index setup for the requested indexing exercise.
// Run this after the collections exist.

const dbName = db.getName();

// 1) Unique invoice number.
db.facturas.createIndex(
  { numeroFactura: 1 },
  {
    name: "ux_facturas_numeroFactura",
    unique: true,
  }
);

// 2) Customer + date history for orders.
db.ordenes.createIndex(
  { clienteId: 1, fechaOrden: -1 },
  {
    name: "idx_ordenes_cliente_fecha",
  }
);

// 3) TTL for abandoned carts.
// ultimaActividad must be a BSON Date and refreshed on every cart activity.
db.carritos.createIndex(
  { ultimaActividad: 1 },
  {
    name: "ttl_carritos_ultimaActividad",
    expireAfterSeconds: 60 * 60 * 24 * 30,
  }
);

print(`Indexes created in ${dbName}`);