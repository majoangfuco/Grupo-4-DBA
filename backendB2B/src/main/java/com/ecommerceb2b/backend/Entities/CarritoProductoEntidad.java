package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CarritoProductoEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long carrito_Producto_ID;

    @ManyToOne
    @JoinColumn(name = "carrito_carrito_id")
    private CarritoEntidad carrito;

    @ManyToOne
    @JoinColumn(name = "producto_producto_id")
    private ProductoEntidad producto;

    private Long unidad_producto;

}
