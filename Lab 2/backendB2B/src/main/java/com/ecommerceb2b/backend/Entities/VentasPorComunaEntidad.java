package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentasPorComunaEntidad {
    private String comuna;
    private Integer cantidadOrdenes;
    private BigDecimal volumenVentas;
    // Geometría combinada de las direcciones de entrega de la comuna
    private String geomEntregasGeoJson;
}
