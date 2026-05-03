package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class FacturaEntidad {

    private Long factura_ID;
    private Long usuarioId;
    private Long datos_Pago_ID;
    private Long ordenId;
    private Float precio_Total;
    private Date fecha_Emision;
    private Float total_Neto;
    private Float iva;
    private List<CarritoProductoEntidad> items;
}