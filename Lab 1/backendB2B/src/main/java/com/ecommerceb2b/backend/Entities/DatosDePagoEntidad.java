package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatosDePagoEntidad {

    Long datos_Pago_ID;

    String metodo_Pago;
    String numero_Tarjeta;
    String fecha_Expiracion;
}
