package com.ecommerceb2b.backend.Entities;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class CarritoProductoEntidad {

    private Long carrito_Producto_ID;


    private CarritoEntidad carrito;


    private ProductoEntidad producto;

    private Long unidad_producto;

}
