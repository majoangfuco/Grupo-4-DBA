package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long producto_ID;

    @ManyToOne
    @JoinColumn(name = "categoria_categoria_id")
    private CategoriaEntidad categoria;

    private String nombre_producto;

    private String descripcion;

    private Float precio;

    private int stock;

    private String sku;

}
