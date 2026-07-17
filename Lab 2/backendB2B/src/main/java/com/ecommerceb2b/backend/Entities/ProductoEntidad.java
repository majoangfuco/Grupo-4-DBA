package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoEntidad {
    private Long producto_ID;
    private int categoria_ID;      // Solo el ID, no el objeto (int para coincidir con PostgreSQL)
    private String nombre_producto;
    private String descripcion;
    private Float precio;
    private Integer stock;
    private Integer stock_reservado;
    private String sku;
    private boolean activo; // indica si el producto está activo o no
}