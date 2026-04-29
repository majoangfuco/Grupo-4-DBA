package com.ecommerceb2b.backend.Entities;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarritoEntidad {

    private Long carrito_ID;


    private UsuarioEntidad usuario;

    private String estado;

    private Long costo_Carrito;
}
