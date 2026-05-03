package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutPedidoDto {

    private Long infoEntregaId;
    private Long datosPagoId;
    private DatosDePagoEntidad datosPago;
}
