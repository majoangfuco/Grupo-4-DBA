package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de {@code POST /api/checkout} (checkout documental, Lab 3).
 *
 * {@code clienteId} y {@code carritoId} son el hex string del
 * {@code ObjectId} de Mongo (colecciones {@code carritos}/{@code productos}
 * definidas en {@code docs/03-checkout-transaccion.md}) — no son los
 * {@code Long} de las entidades relacionales del Lab 2.
 *
 * {@code razonSocial}/{@code rutEmpresa}/{@code direccionEnvio} los envía
 * el frontend con los datos del cliente ya logueado: no hay colección
 * {@code clientes} en Mongo de donde resolverlos server-side, así que el
 * checkout confía en lo que llega acá para armar el snapshot embebido en
 * {@code ordenes.cliente} / {@code facturas.cliente}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequestDto {

    private String clienteId;
    private String carritoId;
    private String razonSocial;
    private String rutEmpresa;
    private String direccionEnvio;
    private DatosPagoMockDto datosPago;
}
