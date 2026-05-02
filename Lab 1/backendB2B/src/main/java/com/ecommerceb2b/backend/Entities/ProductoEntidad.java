package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoEntidad {
    private Long producto_ID;
    private Long categoria_ID;      // Solo el ID, no el objeto
    private String nombre_producto;
    private String descripcion;
    private Float precio;
    private Integer stock;
    private String sku;
    private boolean activo; // Nuevo campo para indicar si el producto está activo o no
}