package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.CarritoMongoEntidad;
import com.ecommerceb2b.backend.Entities.ItemCarritoMongoEntidad;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
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
     * $push del ítem nuevo + $currentDate de ultimaActividad en la MISMA
     * operación (ver nota del punto 5 en 01-modelado-documental.md: el
     * TTL depende de que este campo se refresque en cada actividad).
     */
    public void agregarItem(ObjectId carritoId, ItemCarritoMongoEntidad item) {
        Bson filtro = Filters.eq("_id", carritoId);
        Bson update = Updates.combine(
                Updates.push("items", item),
                Updates.currentDate("ultimaActividad"));
        coleccion.updateOne(filtro, update);
    }
 
    public List<CarritoMongoEntidad> listarPorCliente(Long clienteId) {
        return coleccion.find(Filters.eq("clienteId", clienteId)).into(new ArrayList<>());
    }
}
