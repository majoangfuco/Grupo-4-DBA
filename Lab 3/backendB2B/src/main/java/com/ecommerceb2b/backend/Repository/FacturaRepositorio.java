package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.inc;

/**
 * Facturas e ítems históricos persistidos como documentos MongoDB.
 *
 * <p>Escribe en {@code facturas_relacionales}, NO en {@code facturas}. Son
 * dos colecciones distintas a propósito: esta guarda el shape del flujo
 * relacional heredado del Lab 2 ({@code _id}/{@code ordenId} Long,
 * {@code clienteId} plano, {@code precioTotal}, {@code items[]} embebidos),
 * mientras que {@code facturas} guarda el shape documental que emite
 * {@link com.ecommerceb2b.backend.Services.CheckoutServicio} ({@code _id}/
 * {@code ordenId} ObjectId, {@code cliente{}} embebido, {@code estado},
 * {@code total}). MongoDB solo admite UN {@code $jsonSchema} activo por
 * colección, así que mientras convivieron en la misma, cualquier validador
 * rompía una de las dos rutas de checkout con "Document failed validation".
 * Ver mongo/schema-validation.js y docs/02-schema-validation.md.
 */
@Repository
public class FacturaRepositorio {

    /** Nombre de la colección del flujo relacional (ver Javadoc de clase). */
    public static final String COL_FACTURAS_RELACIONALES = "facturas_relacionales";

    private final MongoCollection<Document> facturas;
    private final MongoCollection<Document> contadores;
    private final JdbcTemplate jdbc;

    public FacturaRepositorio(MongoDatabase database, JdbcTemplate jdbc) {
        this.facturas = database.getCollection(COL_FACTURAS_RELACIONALES);
        this.contadores = database.getCollection("contadores");
        this.jdbc = jdbc;
    }

    public List<FacturaEntidad> findAll() {
        List<FacturaEntidad> resultado = new ArrayList<>();
        facturas.find().sort(descending("fechaEmision")).map(this::mapear).into(resultado);
        return resultado;
    }

    public Optional<FacturaEntidad> findById(Long id) {
        return Optional.ofNullable(facturas.find(eq("_id", id)).first()).map(this::mapear);
    }

    public List<FacturaEntidad> findByUsuarioId(Long usuarioId) {
        List<FacturaEntidad> resultado = new ArrayList<>();
        facturas.find(eq("clienteId", usuarioId)).sort(descending("fechaEmision"))
                .map(this::mapear).into(resultado);
        return resultado;
    }

    public Optional<FacturaEntidad> findByOrdenId(Long ordenId) {
        return Optional.ofNullable(facturas.find(eq("ordenId", ordenId)).first()).map(this::mapear);
    }

    public Long crear(FacturaEntidad factura) {
        long id = siguienteId("facturas");
        long correlativo = siguienteId("numeroFactura");
        String numeroFactura = "FAC-%010d".formatted(correlativo);

        List<Document> items = new ArrayList<>();
        if (factura.getItems() != null) {
            for (CarritoProductoEntidad item : factura.getItems()) {
                items.add(new Document("productoId", item.getProducto().getProducto_ID())
                        .append("nombreProducto", item.getProducto().getNombre_producto())
                        .append("sku", item.getProducto().getSku())
                        .append("precioUnitario", item.getProducto().getPrecio().doubleValue())
                        .append("cantidad", item.getUnidad_producto()));
            }
        }

        Document documento = new Document("_id", id)
                .append("numeroFactura", numeroFactura)
                .append("clienteId", factura.getUsuarioId())
                .append("datosPagoId", factura.getDatos_Pago_ID())
                .append("ordenId", factura.getOrdenId())
                .append("precioTotal", factura.getPrecio_Total().doubleValue())
                .append("fechaEmision", factura.getFecha_Emision())
                .append("totalNeto", factura.getTotal_Neto().doubleValue())
                .append("iva", factura.getIva().doubleValue())
                .append("costoEnvio", factura.getCosto_Envio() == null ? 0D : factura.getCosto_Envio().doubleValue())
                .append("rutEmpresa", obtenerRut(factura.getUsuarioId()))
                .append("items", items);
        facturas.insertOne(documento);
        factura.setFactura_ID(id);
        factura.setNumeroFactura(numeroFactura);
        factura.setRut_Empresa(documento.getString("rutEmpresa"));
        return id;
    }

    private FacturaEntidad mapear(Document documento) {
        FacturaEntidad factura = new FacturaEntidad();
        factura.setFactura_ID(numero(documento, "_id"));
        factura.setNumeroFactura(documento.getString("numeroFactura"));
        factura.setUsuarioId(numero(documento, "clienteId"));
        factura.setDatos_Pago_ID(numeroNullable(documento, "datosPagoId"));
        factura.setOrdenId(numero(documento, "ordenId"));
        factura.setPrecio_Total(documento.get("precioTotal", Number.class).floatValue());
        factura.setFecha_Emision(documento.getDate("fechaEmision"));
        factura.setTotal_Neto(documento.get("totalNeto", Number.class).floatValue());
        factura.setIva(documento.get("iva", Number.class).floatValue());
        Number envio = documento.get("costoEnvio", Number.class);
        factura.setCosto_Envio(envio == null ? 0F : envio.floatValue());
        factura.setRut_Empresa(documento.getString("rutEmpresa"));

        List<CarritoProductoEntidad> items = new ArrayList<>();
        for (Document item : documento.getList("items", Document.class, List.of())) {
            ProductoEntidad producto = new ProductoEntidad();
            producto.setProducto_ID(numero(item, "productoId"));
            producto.setNombre_producto(item.getString("nombreProducto"));
            producto.setSku(item.getString("sku"));
            producto.setPrecio(item.get("precioUnitario", Number.class).floatValue());
            CarritoProductoEntidad cp = new CarritoProductoEntidad();
            cp.setProducto(producto);
            cp.setUnidad_producto(numero(item, "cantidad"));
            items.add(cp);
        }
        factura.setItems(items);
        return factura;
    }

    private String obtenerRut(Long usuarioId) {
        List<String> valores = jdbc.query("SELECT rut_empresa FROM usuario_entidad WHERE usuario_id = ?",
                (rs, row) -> rs.getString(1), usuarioId);
        return valores.isEmpty() ? null : valores.get(0);
    }

    private long siguienteId(String secuencia) {
        Document contador = contadores.findOneAndUpdate(eq("_id", secuencia), inc("valor", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return contador.get("valor", Number.class).longValue();
    }

    private long numero(Document documento, String campo) {
        return documento.get(campo, Number.class).longValue();
    }

    private Long numeroNullable(Document documento, String campo) {
        Number valor = documento.get(campo, Number.class);
        return valor == null ? null : valor.longValue();
    }
}
