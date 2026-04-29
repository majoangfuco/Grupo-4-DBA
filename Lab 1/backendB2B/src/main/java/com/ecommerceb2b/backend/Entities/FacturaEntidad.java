package com.ecommerceb2b.backend.Entities;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaEntidad {

    private Long factura_ID;

    private UsuarioEntidad usuario;


    private OrdenesEntidad orden;

    private Float precio_Total;

    private Date fecha_Emision;

    private Float total_Neto;

    private Float iva;



}
