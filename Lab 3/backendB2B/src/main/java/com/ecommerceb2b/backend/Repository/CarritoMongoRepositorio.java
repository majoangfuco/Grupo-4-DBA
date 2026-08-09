package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.CarritoMongoEntidad;
import com.ecommerceb2b.backend.Entities.ItemCarritoMongoEntidad;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CarritoMongoRepositorio {

    private final MongoCollection<CarritoMongoEntidad> coleccion;

    public CarritoMongoRepositorio(MongoDatabase mongoDatabase) {
        this.coleccion = mongoDatabase.getCollection("carritos", CarritoMongoEntidad.class);
    }

    public Optional<CarritoMongoEntidad> buscarActivoPorCliente(Long clienteId) {
        Bson filtro = Filters.and(
                Filters.eq("clienteId", clienteId),
                Filters.eq("estado", "ACTIVO"));
        return Optional.ofNullable(coleccion.find(filtro).first());
    }

    public CarritoMongoEntidad crear(CarritoMongoEntidad carrito) {
        coleccion.insertOne(carrito);
        return carrito;
    }

    /**
     * Si el producto YA está en items[], actualiza ese elemento in-place
     * (operador posicional "$"). Si no está, lo agrega con $push. Así el
     * array de Mongo no se llena de líneas duplicadas del mismo producto
     * cuando el cliente sube/baja la cantidad desde "Mi carrito".
     */
    public void establecerItem(org.bson.types.ObjectId carritoId, ItemCarritoMongoEntidad item) {
        Bson filtroConItem = Filters.and(
                Filters.eq("_id", carritoId),
                Filters.eq("items.productoId", item.getProductoId()));

        Bson actualizarExistente = Updates.combine(
                Updates.set("items.$.cantidad", item.getCantidad()),
                Updates.set("items.$.precioUnitario", item.getPrecioUnitario()),
                Updates.set("items.$.sku", item.getSku()),
                Updates.set("items.$.nombreProducto", item.getNombreProducto()),
                Updates.set("items.$.cantidadMinimaB2B", item.getCantidadMinimaB2B()),
                Updates.set("items.$.stockDisponibleAlAgregar", item.getStockDisponibleAlAgregar()),
                Updates.currentDate("ultimaActividad"));

        UpdateResult resultado = coleccion.updateOne(filtroConItem, actualizarExistente);

        if (resultado.getMatchedCount() == 0) {
            // El producto todavía no estaba en este carrito: se agrega.
            Bson agregarNuevo = Updates.combine(
                    Updates.push("items", item),
                    Updates.currentDate("ultimaActividad"));
            coleccion.updateOne(Filters.eq("_id", carritoId), agregarNuevo);
        }
    }

    public List<CarritoMongoEntidad> listarPorCliente(Long clienteId) {
        return coleccion.find(Filters.eq("clienteId", clienteId)).into(new ArrayList<>());
    }
}