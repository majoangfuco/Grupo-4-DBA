package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

/**
 * Campos exigido por el $jsonSchema de
 * la colección "carritos" de Mongo. Exige como "required" en cada ítem:
 * productoId, cantidad, precioUnitario, cantidadMinimaB2B y
 * stockDisponibleAlAgregar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemCarritoMongoEntidad {
    private Long productoId;
    private String sku;
    private String nombreProducto;
    private Integer cantidad;
    private Double precioUnitario;
    private Integer cantidadMinimaB2B;
    private Integer stockDisponibleAlAgregar;
}
