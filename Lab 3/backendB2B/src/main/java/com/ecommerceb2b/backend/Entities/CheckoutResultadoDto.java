package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Respuesta de {@code POST /api/checkout}.
 *
 * Se arma a mano a partir de los documentos Mongo insertados en vez de
 * devolver {@code org.bson.Document} tal cual: tipos BSON como
 * {@code ObjectId}/{@code Decimal128} no serializan a JSON limpio con el
 * Jackson por defecto de Spring (intentaría reflejar sus campos internos),
 * así que este DTO los normaliza a {@code String}/{@code BigDecimal} antes
 * de salir por el controller.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResultadoDto {

    private String ordenId;
    private String numeroOrden;
    private String facturaId;
    private String numeroFactura;
    private String estadoFactura;
    private BigDecimal totalNeto;
    private BigDecimal iva;
    private BigDecimal total;
    private Date fechaOrden;
    private Date fechaEmision;
}
