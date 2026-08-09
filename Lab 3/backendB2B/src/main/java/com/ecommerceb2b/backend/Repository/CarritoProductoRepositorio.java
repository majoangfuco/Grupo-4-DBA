package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/** Ítems embebidos en carritos Mongo; catálogo y reserva de stock permanecen en PostgreSQL. */
@Repository
public class CarritoProductoRepositorio {

    private final MongoCollection<Document> carritos;
    private final MongoCollection<Document> contadores;
    private final JdbcTemplate jdbc;

    public CarritoProductoRepositorio(MongoDatabase database, JdbcTemplate jdbc) {
        this.carritos = database.getCollection("carritos");
        this.contadores = database.getCollection("contadores");
        this.jdbc = jdbc;
    }

    public Optional<CarritoProductoEntidad> encontrarPorId(Long itemId) {
        Document carrito = carritos.find(eq("items.itemId", itemId)).first();
        return buscarItem(carrito, itemId);
    }

    public boolean carritoEstaActivo(Long carritoId) {
        return carritos.countDocuments(and(eq("_id", carritoId), eq("estado", "ACTIVO"))) == 1;
    }

    public Optional<CarritoProductoEntidad> encontrarPorCarritoYProducto(Long carritoId, Long productoId) {
        Document carrito = carritos.find(and(eq("_id", carritoId), eq("items.productoId", productoId))).first();
        if (carrito == null) return Optional.empty();
        return items(carrito).stream()
                .filter(item -> numero(item, "productoId") == productoId)
                .findFirst().map(item -> mapearItem(carritoId, item));
    }

    public List<CarritoProductoEntidad> listarPorCarrito(Long carritoId) {
        Document carrito = carritos.find(eq("_id", carritoId)).first();
        if (carrito == null) return List.of();
        List<CarritoProductoEntidad> resultado = new ArrayList<>();
        for (Document item : items(carrito)) resultado.add(mapearItem(carritoId, item));
        return resultado;
    }

    public int crear(Long carritoId, Long productoId, Long cantidad, Integer cantidadMinimaB2B) {
        Document producto = jdbc.queryForObject("""
                SELECT producto_id, nombre_producto, descripcion, precio, stock,
                       stock_reservado, sku, activo
                FROM producto_entidad WHERE producto_id = ? AND activo = TRUE
                """, (rs, row) -> new Document("productoId", rs.getLong("producto_id"))
                        .append("nombreProducto", rs.getString("nombre_producto"))
                        .append("descripcion", rs.getString("descripcion"))
                        .append("precioUnitario", rs.getDouble("precio"))
                        // reservarStock se ejecuta antes de este insert dentro de la
                        // transacción SQL; se suma la cantidad recién reservada para
                        // conservar el disponible observado al iniciar la actividad.
                        .append("stockDisponibleAlAgregar", Math.max(0,
                                rs.getInt("stock") - rs.getInt("stock_reservado") + cantidad))
                        .append("cantidadMinimaB2B", cantidadMinimaB2B != null ? cantidadMinimaB2B.longValue() : 1L)
                        .append("sku", rs.getString("sku")), productoId);
        producto.append("itemId", siguienteId("carritoItems"))
                .append("cantidad", cantidad)
                .append("agregadoEn", new java.util.Date());

        long modificados = carritos.updateOne(
                and(eq("_id", carritoId), eq("estado", "ACTIVO"), ne("items.productoId", productoId)),
                combine(push("items", producto), currentDate("ultimaActividad"))).getModifiedCount();
        if (modificados == 0) throw new IllegalStateException("Carrito inexistente, inactivo o producto duplicado");
        recalcularCosto(carritoId);
        return 1;
    }

    public int actualizarCantidad(Long itemId, Long cantidad, Integer cantidadMinimaB2B) {
        Document carrito = carritos.find(eq("items.itemId", itemId)).first();
        if (carrito == null) return 0;
        long modificados = carritos.updateOne(
                and(eq("_id", carrito.get("_id")), eq("estado", "ACTIVO"), eq("items.itemId", itemId)),
                combine(
                        set("items.$.cantidad", cantidad),
                        set("items.$.cantidadMinimaB2B", cantidadMinimaB2B != null ? cantidadMinimaB2B : 1),
                        currentDate("ultimaActividad"))).getModifiedCount();
        if (modificados > 0) recalcularCosto(numero(carrito, "_id"));
        return modificados > 0 ? 1 : 0;
    }

    public int eliminarPorId(Long itemId) {
        Document carrito = carritos.find(eq("items.itemId", itemId)).first();
        if (carrito == null) return 0;
        long carritoId = numero(carrito, "_id");
        long modificados = carritos.updateOne(and(eq("_id", carritoId), eq("estado", "ACTIVO")),
                combine(pull("items", new Document("itemId", itemId)), currentDate("ultimaActividad")))
                .getModifiedCount();
        if (modificados > 0) recalcularCosto(carritoId);
        return modificados > 0 ? 1 : 0;
    }

    public void reservarStock(Long productoId, int cantidad) {
        jdbc.update("CALL reservar_stock(?, ?)", productoId, cantidad);
    }

    public void liberarStock(Long productoId, int cantidad) {
        jdbc.update("CALL liberar_stock(?, ?)", productoId, cantidad);
    }

    public BigDecimal calcularSubtotal(Long carritoId) {
        return listarPorCarrito(carritoId).stream()
                .map(item -> BigDecimal.valueOf(item.getProducto().getPrecio())
                        .multiply(BigDecimal.valueOf(item.getUnidad_producto())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recalcularCosto(Long carritoId) {
        long total = calcularSubtotal(carritoId).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        carritos.updateOne(eq("_id", carritoId), combine(set("costoCarrito", total), currentDate("ultimaActividad")));
    }

    private Optional<CarritoProductoEntidad> buscarItem(Document carrito, Long itemId) {
        if (carrito == null) return Optional.empty();
        return items(carrito).stream().filter(item -> numero(item, "itemId") == itemId)
                .findFirst().map(item -> mapearItem(numero(carrito, "_id"), item));
    }

    @SuppressWarnings("unchecked")
    private List<Document> items(Document carrito) {
        List<Document> valor = (List<Document>) carrito.get("items");
        return valor == null ? List.of() : valor;
    }

    private CarritoProductoEntidad mapearItem(Long carritoId, Document item) {
        CarritoProductoEntidad entidad = new CarritoProductoEntidad();
        entidad.setCarrito_Producto_ID(numero(item, "itemId"));
        CarritoEntidad carrito = new CarritoEntidad();
        carrito.setCarrito_ID(carritoId);
        entidad.setCarrito(carrito);
        ProductoEntidad producto = new ProductoEntidad();
        producto.setProducto_ID(numero(item, "productoId"));
        producto.setNombre_producto(item.getString("nombreProducto"));
        producto.setDescripcion(item.getString("descripcion"));
        producto.setSku(item.getString("sku"));
        producto.setPrecio(item.get("precioUnitario", Number.class).floatValue());
        entidad.setProducto(producto);
        entidad.setUnidad_producto(numero(item, "cantidad"));
        return entidad;
    }

    private long siguienteId(String secuencia) {
        Document contador = contadores.findOneAndUpdate(eq("_id", secuencia), inc("valor", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return contador.get("valor", Number.class).longValue();
    }

    private long numero(Document documento, String campo) {
        return documento.get(campo, Number.class).longValue();
    }
}
