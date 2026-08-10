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
 * Lee el catálogo real de Postgres (ProductoServicio) para armar el
 * snapshot del ítem. Este service arma el documento con datos reales
 * dispara la escritura y si Mongo la rechaza (error 121, DocumentValidationFailure) traduce eso a un
 * mensaje de negocio legible.
 */
@Service
public class CarritoMongoServicio {

    private static final int MINIMO_TECNICO_SIN_CONFIGURACION = 1;

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

    /**
     * cantidadTotal es la cantidad FINAL que debe quedar para ese
     * producto en el carrito
     */
    public CarritoMongoEntidad establecerCantidadItem(Long clienteId, Long productoId, Long cantidadTotal) {
        if (clienteId == null || clienteId <= 0) {
            throw new CarritoMongoValidationException("El cliente es obligatorio");
        }
        if (productoId == null) {
            throw new CarritoMongoValidationException("El producto es obligatorio");
        }
        if (cantidadTotal == null || cantidadTotal <= 0) {
            throw new CarritoMongoValidationException("La cantidad debe ser mayor a 0");
        }
        /** 
        * Única lectura cruzada hacia Postgres: trae el producto REAL
        * (nombre, sku, precio, stock) para armar el snapshot. Si no
        * existe o está inactivo, ProductoServicio ya tira
        * NoSuchElementException — se traduce abajo a un 400 de negocio.
        */
        ProductoEntidad producto;
        try {
            producto = productoServicio.obtenerProductoPorId(productoId);
        } catch (NoSuchElementException e) {
            throw new CarritoMongoValidationException("Producto no encontrado: " + productoId);
        }

        int stockReservado = producto.getStock_reservado() != null ? producto.getStock_reservado() : 0;
        int stockDisponibleAntesDeReserva = producto.getStock() - stockReservado + cantidadTotal.intValue();
        Integer cantidadMinimaB2B = obtenerCantidadMinima(productoId);
        int minimoParaDocumento = cantidadMinimaB2B != null
                ? cantidadMinimaB2B
                : MINIMO_TECNICO_SIN_CONFIGURACION;

        /**   minimoB2B y stockDisponible se validan en el validador de colección de Mongo.
            * Si falla, lanza MongoWriteException con error 121 (DocumentValidationFailure).
            *Se traduce a CarritoMongoValidationException para que el controller devuelva 400 con ese mensaje.
        */
        if (cantidadTotal > stockDisponibleAntesDeReserva) {
            throw new CarritoMongoValidationException(
                    "No hay stock suficiente de \"" + producto.getNombre_producto() + "\": disponible "
                            + stockDisponibleAntesDeReserva + ", solicitado " + cantidadTotal + ".");
        }
        if (cantidadMinimaB2B != null && cantidadTotal < cantidadMinimaB2B) {
            throw new CarritoMongoValidationException(
                    "El pedido mínimo B2B para \"" + producto.getNombre_producto() + "\" es de "
                            + cantidadMinimaB2B + " unidades (pediste " + cantidadTotal + ").");
        }

        ItemCarritoMongoEntidad item = new ItemCarritoMongoEntidad();
        item.setProductoId(producto.getProducto_ID());
        item.setSku(producto.getSku());
        item.setNombreProducto(producto.getNombre_producto());
        item.setCantidad(cantidadTotal.intValue());
        item.setPrecioUnitario(producto.getPrecio().doubleValue());
        item.setCantidadMinimaB2B(minimoParaDocumento);
        item.setStockDisponibleAlAgregar(stockDisponibleAntesDeReserva);

        try {
            Optional<CarritoMongoEntidad> existente = carritoMongoRepositorio.buscarActivoPorCliente(clienteId);

            if (existente.isPresent()) {
                CarritoMongoEntidad carrito = existente.get();
                carritoMongoRepositorio.establecerItem(carrito.getId(), item);
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
    
    private String traducirError(MongoException e) {
        return "El carrito no cumple las reglas de negocio: revisá que la cantidad no supere "
                + "el stock disponible y que no sea menor al pedido mínimo B2B.";
    }

    // ─── Configuración de cantidad mínima por producto ────────

    public void establecerCantidadMinima(Long productoId, Integer cantidadMinimaB2B) {
        if (cantidadMinimaB2B == null || cantidadMinimaB2B < 1) {
            throw new CarritoMongoValidationException("La cantidad mínima B2B debe ser mayor o igual a 1");
        }
        if (cantidadMinimaB2B == 1) {
            configuracionMinimaColeccion.deleteOne(Filters.eq("productoId", productoId));
            return;
        }
        Document documento = new Document("productoId", productoId)
                .append("cantidadMinimaB2B", cantidadMinimaB2B);
        configuracionMinimaColeccion.replaceOne(
                Filters.eq("productoId", productoId), documento, new ReplaceOptions().upsert(true));
    }

    /**
     * Si el Admin todavía no configuró un mínimo para este producto, se
     * devuelve null. El documento de carrito usa 1 como mínimo técnico,
     * pero la regla B2B solo se aplica cuando existe configuración.
     */
    public Integer obtenerCantidadMinima(Long productoId) {
        Document encontrado = configuracionMinimaColeccion.find(Filters.eq("productoId", productoId)).first();
        if (encontrado == null) {
            return null;
        }
        Integer cantidadMinimaB2B = encontrado.getInteger("cantidadMinimaB2B");
        return cantidadMinimaB2B != null && cantidadMinimaB2B > 1 ? cantidadMinimaB2B : null;
    }
}
