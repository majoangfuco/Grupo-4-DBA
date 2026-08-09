package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.CheckoutRequestDto;
import com.ecommerceb2b.backend.Entities.CheckoutResultadoDto;
import com.ecommerceb2b.backend.Entities.DatosPagoMockDto;
import com.ecommerceb2b.backend.Exceptions.PagoRechazadoException;
import com.ecommerceb2b.backend.Exceptions.StockInsuficienteException;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;

/**
 * Checkout documental del Lab 3: transacción multi-documento sobre las
 * colecciones {@code carritos}/{@code productos}/{@code ordenes}/{@code
 * facturas} definidas en {@code docs/03-checkout-transaccion.md}.
 *
 * <p>Acceso a datos con {@code mongodb-driver-sync} puro (sin Spring Data
 * Mongo), mismo patrón que {@link MongoSesionServicio} ya establece para el
 * resto del proyecto: la transacción se abre con
 * {@link MongoSesionServicio#enTransaccion}, que delega en
 * {@code ClientSession.withTransaction(...)}. Ese método del driver ya
 * reintenta automáticamente los {@code TransientTransactionError} (p. ej.
 * un write conflict al incrementar el contador de facturación, o una
 * elección de PRIMARY a mitad de transacción) y los commits inciertos
 * ({@code UnknownTransactionCommitResult}) — es el "patrón estándar
 * recomendado por el driver", así que este servicio no implementa ningún
 * retry manual.</p>
 *
 * <p><b>Fuera de alcance a propósito:</b> este checkout NO asigna almacén
 * ni calcula costo de envío. Esa lógica (distancia, stock por almacén,
 * cobertura geográfica) sigue viviendo en PostgreSQL/PostGIS —
 * {@code PROCEDURE procesar_checkout} de {@code backendB2B/init.sql},
 * Lab 2— y es un sistema aparte que este servicio no toca ni sincroniza.
 * {@code productos.stock} en Mongo es un contador escalar independiente del
 * stock por almacén relacional (ver nota en
 * {@code docs/01-modelado-documental.md} §3).</p>
 *
 * <p><b>{@code clienteId}/{@code productoId} son {@code Long}, no {@code
 * ObjectId}:</b> mismo valor que {@code usuario_ID}/{@code producto_ID} de
 * Postgres, tal como quedó definido en el validador de {@code carritos}
 * ({@code mongo/schema-validation.js}) y en {@code CarritoMongoServicio}.
 * La colección {@code productos} que este servicio lee/escribe es una
 * copia acotada, exclusiva de esta transacción — no el catálogo (ver
 * {@code mongo/seeders/productos-seed.js}). El {@code _id} de cada
 * documento de {@code productos} es ese mismo {@code producto_ID}, para
 * que el filtro {@code {_id: productoId}} calce directo contra lo que ya
 * trae {@code carritos.items[].productoId} sin tabla de mapeo. Solo
 * {@code carritoId} (el {@code _id} propio del documento de carrito) sigue
 * siendo {@code ObjectId}.</p>
 */
@Service
public class CheckoutServicio {

    private static final String COL_CARRITOS = "carritos";
    private static final String COL_PRODUCTOS = "productos";
    private static final String COL_ORDENES = "ordenes";
    private static final String COL_FACTURAS = "facturas";
    private static final String COL_CONTADORES = "contadores";

    // precioUnitario en carritos.items se considera SIN IVA (ver docs/03).
    // 19% es la tasa vigente en Chile; si alguna vez se vuelve variable
    // (exenciones, otra región), esto deja de ser una constante.
    private static final BigDecimal TASA_IVA = new BigDecimal("0.19");

    private final MongoSesionServicio mongoSesionServicio;

    public CheckoutServicio(MongoSesionServicio mongoSesionServicio) {
        this.mongoSesionServicio = mongoSesionServicio;
    }

    /**
     * Ejecuta el checkout completo en una única transacción ACID.
     *
     * Recibe el DTO completo (mismo patrón que
     * {@code OrdenesServicio.solicitarOrdenAtomica(Long, CheckoutPedidoDto)}
     * en el checkout relacional) en vez de explotar cada campo como
     * parámetro suelto, porque además de {@code clienteId}/{@code
     * carritoId}/{@code datosPago} ahora también carga el snapshot de
     * cliente ({@code razonSocial}/{@code rutEmpresa}/{@code
     * direccionEnvio}) que se congela en la orden y la factura.
     *
     * @param pedido body de {@code POST /api/checkout}.
     * @return snapshot de la orden y la factura recién creadas.
     * @throws StockInsuficienteException si algún ítem no tiene stock
     *                                    suficiente al momento del checkout.
     * @throws PagoRechazadoException     si {@code datosPago.aprobado} es
     *                                    {@code false}.
     * @throws IllegalArgumentException   si los parámetros son inválidos.
     * @throws IllegalStateException      si el carrito no existe, no
     *                                    pertenece al cliente, no está
     *                                    ACTIVO o está vacío.
     */
    public CheckoutResultadoDto procesarCheckout(CheckoutRequestDto pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("El pedido de checkout es obligatorio");
        }
        if (pedido.getClienteId() == null || pedido.getClienteId() <= 0) {
            throw new IllegalArgumentException("El cliente es obligatorio");
        }
        if (pedido.getCarritoId() == null || pedido.getCarritoId().isBlank()) {
            throw new IllegalArgumentException("El carrito es obligatorio");
        }
        if (pedido.getDatosPago() == null) {
            throw new IllegalArgumentException("Los datos de pago son obligatorios");
        }
        if (isBlank(pedido.getRazonSocial()) || isBlank(pedido.getRutEmpresa())
                || isBlank(pedido.getDireccionEnvio())) {
            throw new IllegalArgumentException(
                    "razonSocial, rutEmpresa y direccionEnvio son obligatorios para el snapshot de cliente");
        }

        Long clienteId = pedido.getClienteId();
        ObjectId carritoOid = aObjectId(pedido.getCarritoId(), "carritoId");

        return mongoSesionServicio.enTransaccion(
                (sesion, db) -> ejecutarCheckout(sesion, db, clienteId, carritoOid, pedido));
    }

    private CheckoutResultadoDto ejecutarCheckout(ClientSession sesion, MongoDatabase db,
            Long clienteId, ObjectId carritoId, CheckoutRequestDto pedido) {

        DatosPagoMockDto datosPago = pedido.getDatosPago();

        MongoCollection<Document> carritos = db.getCollection(COL_CARRITOS);
        MongoCollection<Document> productos = db.getCollection(COL_PRODUCTOS);
        MongoCollection<Document> ordenes = db.getCollection(COL_ORDENES);
        MongoCollection<Document> facturas = db.getCollection(COL_FACTURAS);

        // ── 1. Leer el carrito del cliente ──────────────────────────────
        Document carrito = carritos.find(sesion, and(
                eq("_id", carritoId),
                eq("clienteId", clienteId),
                eq("estado", "ACTIVO"))).first();

        if (carrito == null) {
            throw new IllegalStateException(
                    "El carrito " + carritoId.toHexString()
                            + " no existe, no pertenece al cliente " + clienteId
                            + " o no está ACTIVO");
        }

        List<Document> items = carrito.getList("items", Document.class);
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("El carrito " + carritoId.toHexString() + " está vacío");
        }

        // ── 2. Descuento de stock, ítem por ítem, condicionado ──────────
        // updateOne con filtro {_id, stock: {$gte: cantidad}}: si no matchea
        // NADA (producto no existe o sin stock suficiente), se aborta. No
        // se relee productos.precio en ningún punto: el precio que se
        // congela en la orden es el que ya traía el carrito (punto 3).
        List<Document> itemsOrden = new ArrayList<>();
        BigDecimal totalNeto = BigDecimal.ZERO;

        for (Document item : items) {
            long productoId = aEntero(item.get("productoId"), "productoId");
            long cantidad = aEntero(item.get("cantidad"), "cantidad");
            BigDecimal precioUnitario = aBigDecimal(item.get("precioUnitario"), "precioUnitario");
            String nombreProducto = item.getString("nombreProducto");

            UpdateResult resultado = productos.updateOne(sesion,
                    and(eq("_id", productoId), gte("stock", cantidad)),
                    Updates.inc("stock", -cantidad));

            if (resultado.getMatchedCount() == 0) {
                // Dispara el abort de la transacción completa (ver Javadoc
                // de clase): cualquier $inc ya aplicado en items previos de
                // este mismo loop se revierte solo, porque nada de esto
                // hizo commit todavía.
                throw new StockInsuficienteException(productoId, cantidad);
            }

            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
            totalNeto = totalNeto.add(subtotal);

            itemsOrden.add(new Document()
                    .append("productoId", productoId)
                    .append("nombreProducto", nombreProducto)
                    .append("cantidad", cantidad)
                    .append("precioUnitario", new Decimal128(precioUnitario))
                    .append("subtotal", new Decimal128(subtotal)));
        }

        // ── IVA: precioUnitario es SIN IVA (ver docs/03), 19% sobre el
        // subtotal acumulado. Redondeo a 2 decimales, mismo criterio que
        // usa el checkout relacional (OrdenesServicio, RoundingMode.HALF_UP).
        BigDecimal iva = totalNeto.multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = totalNeto.add(iva);

        // ── 3 y 4. Numeración compartida + orden + factura ──────────────
        // Un mismo checkout siempre produce exactamente UNA orden y UNA
        // factura (docs/03 muestra ambas con el mismo correlativo:
        // ORD-2026-000482 / F-2026-000482), así que comparten secuencia.
        // Si más adelante se permite reemitir una factura o cancelar una
        // orden sin facturar, esto deja de ser 1:1 y hay que separarlas.
        long secuencia = siguienteSecuencia(sesion, db);
        int anio = Year.now(ZoneOffset.UTC).getValue();
        String numeroOrden = "ORD-%d-%06d".formatted(anio, secuencia);
        String numeroFactura = "F-%d-%06d".formatted(anio, secuencia);

        ObjectId ordenId = new ObjectId();
        ObjectId facturaId = new ObjectId();
        Date ahora = new Date();

        // Snapshot de cliente: cada colección lo embebe con un shape
        // distinto, tal cual lo fija docs/03 —
        //   ordenes.cliente     = {razonSocial, rutEmpresa, direccionEnvio}
        //                          (clienteId ya va como campo hermano)
        //   facturas.cliente    = {clienteId, razonSocial, rutEmpresa}
        //                          (sin direccionEnvio: no es un dato
        //                          tributario, es de despacho)
        // No sale de Postgres ni de una colección `clientes` de Mongo (no
        // existe): lo manda el frontend con los datos del cliente logueado.
        Document clienteOrden = new Document("razonSocial", pedido.getRazonSocial())
                .append("rutEmpresa", pedido.getRutEmpresa())
                .append("direccionEnvio", pedido.getDireccionEnvio());

        Document clienteFactura = new Document("clienteId", clienteId)
                .append("razonSocial", pedido.getRazonSocial())
                .append("rutEmpresa", pedido.getRutEmpresa());

        Document orden = new Document("_id", ordenId)
                .append("numeroOrden", numeroOrden)
                .append("clienteId", clienteId)
                .append("cliente", clienteOrden)
                .append("carritoId", carritoId)
                .append("estado", "PENDIENTE")
                .append("items", itemsOrden)
                .append("totalNeto", new Decimal128(totalNeto))
                .append("iva", new Decimal128(iva))
                .append("total", new Decimal128(total))
                .append("fechaOrden", ahora)
                .append("facturaId", facturaId);

        ordenes.insertOne(sesion, orden);

        Document factura = new Document("_id", facturaId)
                .append("numeroFactura", numeroFactura)
                .append("ordenId", ordenId)
                .append("cliente", clienteFactura)
                .append("totalNeto", new Decimal128(totalNeto))
                .append("iva", new Decimal128(iva))
                .append("total", new Decimal128(total))
                // montoTotal se mantiene además de `total` por fidelidad al
                // shape de facturas que ya fija docs/03 (ese ejemplo solo
                // tenía montoTotal, sin iva/total separados). Son el mismo
                // valor; si el equipo prefiere consolidar en un solo campo,
                // hay que decidir cuál de los dos nombres se cae.
                .append("montoTotal", new Decimal128(total))
                .append("estado", "EMITIDA")
                .append("fechaEmision", ahora)
                .append("fechaAnulacion", null)
                .append("motivoAnulacion", null);

        facturas.insertOne(sesion, factura);

        // ── 5. Validar el pago (mock) ───────────────────────────────────
        // Se valida después de escribir orden/factura porque así se pidió;
        // como todavía no hubo commit, el efecto de abortar acá es idéntico
        // a validarlo antes del paso 3 (nada de lo escrito en esta
        // transacción persiste). Si se prefiere fail-fast, este bloque se
        // puede mover justo después del loop de stock sin cambiar el
        // resultado.
        if (!datosPago.isAprobado()) {
            throw new PagoRechazadoException(
                    datosPago.getReferencia() != null ? datosPago.getReferencia() : "sin referencia");
        }

        // ── 6. Carrito → CONVERTIDO (no se borra, queda de historial) ───
        UpdateResult cierreCarrito = carritos.updateOne(sesion,
                eq("_id", carritoId),
                Updates.set("estado", "CONVERTIDO"));

        if (cierreCarrito.getMatchedCount() == 0) {
            throw new IllegalStateException(
                    "No se pudo marcar como convertido el carrito " + carritoId.toHexString());
        }

        return new CheckoutResultadoDto(
                ordenId.toHexString(), numeroOrden,
                facturaId.toHexString(), numeroFactura,
                "EMITIDA", totalNeto, iva, total, ahora, ahora);
    }

    /**
     * Correlativo atómico compartido orden/factura, vía patrón "counters
     * collection" (findOneAndUpdate + $inc + upsert dentro de la misma
     * sesión transaccional). Al vivir DENTRO de la transacción, si el
     * checkout aborta (stock o pago) el número también se revierte: no
     * quedan huecos en la numeración por intentos fallidos. La clave se
     * scopea por año ({@code checkout-2026}) para que el correlativo
     * reinicie cada año, como es habitual en numeración de facturación.
     *
     * Dos checkouts concurrentes que caen sobre el mismo documento de
     * contador producen un write conflict real de MongoDB: eso es
     * exactamente un {@code TransientTransactionError}, así que
     * {@code withTransaction} reintenta la transacción completa sola (ver
     * Javadoc de clase) — no hace falta manejarlo acá.
     */
    private long siguienteSecuencia(ClientSession sesion, MongoDatabase db) {
        String clave = "checkout-" + Year.now(ZoneOffset.UTC).getValue();
        MongoCollection<Document> contadores = db.getCollection(COL_CONTADORES);

        Document actualizado = contadores.findOneAndUpdate(sesion,
                eq("_id", clave),
                Updates.inc("secuencia", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));

        return ((Number) actualizado.get("secuencia")).longValue();
    }

    private static boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ObjectId aObjectId(String valor, String campo) {
        if (!ObjectId.isValid(valor)) {
            throw new IllegalArgumentException(campo + " no es un ObjectId válido: " + valor);
        }
        return new ObjectId(valor);
    }

    private static long aEntero(Object valor, String campo) {
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        throw new IllegalStateException("El campo '" + campo + "' del ítem del carrito no es numérico: " + valor);
    }

    private static BigDecimal aBigDecimal(Object valor, String campo) {
        if (valor instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (valor instanceof Number numero) {
            return BigDecimal.valueOf(numero.doubleValue());
        }
        throw new IllegalStateException("El campo '" + campo + "' del ítem del carrito no es numérico: " + valor);
    }
}
