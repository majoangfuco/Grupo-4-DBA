package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.CarritoMongoEntidad;
import com.ecommerceb2b.backend.Entities.ItemCarritoMongoEntidad;
import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import com.ecommerceb2b.backend.Exceptions.CarritoMongoValidationException;
import com.ecommerceb2b.backend.Repository.CarritoMongoRepositorio;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * La regla de negocio (cantidad ≤ stock, cantidad ≥ mínimo B2B) 
 * Este service arma el documento con datos reales, dispara la escritura
 * y si Mongo la rechaza (error 121, DocumentValidationFailure) lo traduce a un
 * mensaje legible.
 */
@Service
public class CarritoMongoServicio {

    private static final int MINIMO_POR_DEFECTO = 1;

    private final CarritoMongoRepositorio carritoMongoRepositorio;
    private final ProductoServicio productoServicio;
    private final MongoCollection<Document> configuracionMinimaColeccion;

    public CarritoMongoServicio(CarritoMongoRepositorio carritoMongoRepositorio,
            ProductoServicio productoServicio,
            MongoDatabase mongoDatabase) {
        this.carritoMongoRepositorio = carritoMongoRepositorio;
        this.productoServicio = productoServicio;
        this.configuracionMinimaColeccion = mongoDatabase.getCollection("configuracion_b2b_productos");
    }

    // ─── Carrito ──────────────────────────────────────────────────

    public CarritoMongoEntidad agregarItem(Long clienteId, Long productoId, Integer cantidad) {
        if (clienteId == null || clienteId <= 0) {
            throw new CarritoMongoValidationException("El cliente es obligatorio");
        }
        if (productoId == null) {
            throw new CarritoMongoValidationException("El producto es obligatorio");
        }
        if (cantidad == null || cantidad <= 0) {
            throw new CarritoMongoValidationException("La cantidad debe ser mayor a 0");
        }

        // Única lectura cruzada hacia Postgres: trae el producto REAL
        // (nombre, sku, precio, stock) para armar el snapshot. Si no
        // existe o está inactivo, ProductoServicio ya tira
        // NoSuchElementException — se traduce abajo a un 400 de negocio.
        ProductoEntidad producto;
        try {
            producto = productoServicio.obtenerProductoPorId(productoId);
        } catch (NoSuchElementException e) {
            throw new CarritoMongoValidationException("Producto no encontrado: " + productoId);
        }

        int stockDisponible = producto.getStock() - producto.getStock_reservado();
        Integer cantidadMinimaB2B = obtenerCantidadMinima(productoId);

        ItemCarritoMongoEntidad item = new ItemCarritoMongoEntidad();
        item.setProductoId(producto.getProducto_ID());
        item.setSku(producto.getSku());
        item.setNombreProducto(producto.getNombre_producto());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(producto.getPrecio().doubleValue());
        item.setCantidadMinimaB2B(cantidadMinimaB2B);
        item.setStockDisponibleAlAgregar(stockDisponible);

        try {
            Optional<CarritoMongoEntidad> existente = carritoMongoRepositorio.buscarActivoPorCliente(clienteId);

            if (existente.isPresent()) {
                CarritoMongoEntidad carrito = existente.get();
                carritoMongoRepositorio.agregarItem(carrito.getId(), item);
                return carrito;
            }

            CarritoMongoEntidad nuevo = new CarritoMongoEntidad();
            nuevo.setClienteId(clienteId);
            nuevo.setEstado("ACTIVO");
            nuevo.setItems(List.of(item));
            nuevo.setCreadoEn(new Date());
            nuevo.setUltimaActividad(new Date());
            return carritoMongoRepositorio.crear(nuevo);

        } catch (MongoWriteException | MongoCommandException e) {
            throw new CarritoMongoValidationException(traducirError(e));
        } catch (MongoException e) {
            throw new CarritoMongoValidationException(
                    "No se pudo guardar el carrito por un problema de conexión con la base. Intentá de nuevo.");
        }
    }

    public List<CarritoMongoEntidad> listar(Long clienteId) {
        if (clienteId == null || clienteId <= 0) {
            throw new CarritoMongoValidationException("El cliente es obligatorio");
        }
        return carritoMongoRepositorio.listarPorCliente(clienteId);
    }

    /**
     * TODO (equipo): si más adelante hace falta distinguir cuál regla
     * falló (stock vs mínimo B2B) para mostrar un mensaje distinto en el
     * front, hay que parsear "errInfo.details" dentro de
     * e.getMessage()/e.getError() (Mongo devuelve ahí el detalle
     * estructurado de qué cláusula del $jsonSchema/$expr no se cumplió).
     * Por ahora se da un mensaje genérico que cubre ambos casos.
     */
    private String traducirError(MongoException e) {
        return "El carrito no cumple las reglas de negocio: revisá que la cantidad no supere "
                + "el stock disponible y que no sea menor al pedido mínimo B2B.";
    }

    // ─── Configuración de cantidad mínima B2B por producto ────────
    // Colección Mongo "configuracion_b2b_productos" — 100% paralela a
    // Postgres, a propósito: no se agregó ninguna columna a
    // producto_entidad para esto. Usa Document crudo (no entidad POJO
    // ni repositorio aparte): es un solo par clave/valor por producto.

    public void establecerCantidadMinima(Long productoId, Integer cantidadMinimaB2B) {
        if (cantidadMinimaB2B == null || cantidadMinimaB2B < 1) {
            throw new CarritoMongoValidationException("La cantidad mínima B2B debe ser mayor o igual a 1");
        }
        Document documento = new Document("productoId", productoId)
                .append("cantidadMinimaB2B", cantidadMinimaB2B);
        configuracionMinimaColeccion.replaceOne(
                Filters.eq("productoId", productoId), documento, new ReplaceOptions().upsert(true));
    }

    /**
     * Si el Admin todavía no configuró un mínimo para este producto, se
     * asume 1 (equivale a "sin mínimo especial").
     */
    public Integer obtenerCantidadMinima(Long productoId) {
        Document encontrado = configuracionMinimaColeccion.find(Filters.eq("productoId", productoId)).first();
        if (encontrado == null) {
            return MINIMO_POR_DEFECTO;
        }
        return encontrado.getInteger("cantidadMinimaB2B", MINIMO_POR_DEFECTO);
    }
}