package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.*;

/** Persistencia operativa de carritos en MongoDB. */
@Repository
public class CarritoRepositorio {

    private final MongoCollection<Document> carritos;
    private final MongoCollection<Document> contadores;

    public CarritoRepositorio(MongoDatabase database) {
        this.carritos = database.getCollection("carritos");
        this.contadores = database.getCollection("contadores");
    }

    public List<CarritoEntidad> encontrarActivoOAbandonadoPorUsuario(Long clienteId) {
        List<CarritoEntidad> resultado = new ArrayList<>();
        carritos.find(and(eq("clienteId", clienteId), in("estado", "ACTIVO", "ABANDONADO")))
                .sort(descending("ultimaActividad"))
                .map(this::mapear)
                .into(resultado);
        return resultado;
    }

    public Optional<CarritoEntidad> encontrarPorId(Long carritoId) {
        Document documento = carritos.find(eq("_id", carritoId)).first();
        return Optional.ofNullable(documento).map(this::mapear);
    }

    public List<CarritoEntidad> listarPorUsuario(Long clienteId) {
        List<CarritoEntidad> resultado = new ArrayList<>();
        carritos.find(eq("clienteId", clienteId)).sort(descending("_id"))
                .map(this::mapear).into(resultado);
        return resultado;
    }

    public int reactivarCarrito(Long carritoId) {
        return carritos.updateOne(eq("_id", carritoId), combine(
                set("estado", "ACTIVO"),
                currentDate("ultimaActividad")
        )).getModifiedCount() > 0 ? 1 : 0;
    }

    public void refrescarActividad(Long carritoId) {
        carritos.updateOne(and(eq("_id", carritoId), eq("estado", "ACTIVO")),
                currentDate("ultimaActividad"));
    }

    public CarritoEntidad crearCarrito(Long clienteId) {
        long id = siguienteId("carritos");
        Document documento = new Document("_id", id)
                .append("clienteId", clienteId)
                .append("estado", "ACTIVO")
                .append("items", new ArrayList<>())
                .append("costoCarrito", 0L)
                .append("creadoEn", new java.util.Date())
                .append("ultimaActividad", new java.util.Date());
        carritos.insertOne(documento);
        return mapear(documento);
    }

    public int actualizarEstado(Long carritoId, String estado) {
        String estadoMongo = "PAGADO".equalsIgnoreCase(estado) ? "CONVERTIDO" : estado.toUpperCase();
        return carritos.updateOne(eq("_id", carritoId), combine(
                set("estado", estadoMongo),
                currentDate("ultimaActividad")
        )).getModifiedCount() > 0 ? 1 : 0;
    }

    public CarritoEntidad obtenerOCrearCarrito(Long clienteId) {
        List<CarritoEntidad> existentes = encontrarActivoOAbandonadoPorUsuario(clienteId);
        if (!existentes.isEmpty()) {
            CarritoEntidad carrito = existentes.get(0);
            if ("ABANDONADO".equalsIgnoreCase(carrito.getEstado())) {
                reactivarCarrito(carrito.getCarrito_ID());
                carrito.setEstado("ACTIVO");
                carrito.setUltima_Actualizacion(new Timestamp(System.currentTimeMillis()));
            } else refrescarActividad(carrito.getCarrito_ID());
            return carrito;
        }
        return crearCarrito(clienteId);
    }

    private long siguienteId(String secuencia) {
        Document contador = contadores.findOneAndUpdate(
                eq("_id", secuencia), inc("valor", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return contador.get("valor", Number.class).longValue();
    }

    private CarritoEntidad mapear(Document documento) {
        CarritoEntidad carrito = new CarritoEntidad();
        carrito.setCarrito_ID(documento.get("_id", Number.class).longValue());
        UsuarioEntidad usuario = new UsuarioEntidad();
        usuario.setUsuario_ID(documento.get("clienteId", Number.class).longValue());
        carrito.setUsuario(usuario);
        carrito.setEstado(documento.getString("estado"));
        java.util.Date actividad = documento.getDate("ultimaActividad");
        carrito.setUltima_Actualizacion(actividad == null ? null : new Timestamp(actividad.getTime()));
        Number costo = documento.get("costoCarrito", Number.class);
        carrito.setCosto_Carrito(costo == null ? 0L : costo.longValue());
        return carrito;
    }
}
