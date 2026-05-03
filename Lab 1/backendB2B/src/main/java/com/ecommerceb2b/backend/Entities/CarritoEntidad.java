package com.ecommerceb2b.backend.Entities;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarritoEntidad {

    private Long carrito_ID;


    private UsuarioEntidad usuario;

    // "PAGADO", "ACTIVO", "ABANDO"
    private String estado;

    private Timestamp ultima_Actualizacion;

    private Long costo_Carrito;
}
