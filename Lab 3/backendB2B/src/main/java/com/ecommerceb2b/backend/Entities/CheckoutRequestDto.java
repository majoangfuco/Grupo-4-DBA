package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de {@code POST /api/checkout} (checkout documental, Lab 3).
 *
 * {@code clienteId} es {@code Long}: mismo valor que {@code usuario_ID} de
 * Postgres, tal como lo definió el validador de {@code carritos} en
 * {@code mongo/schema-validation.js} (no un {@code ObjectId} de Mongo
 * generado aparte — no existe una colección {@code clientes} separada).
 * {@code carritoId} sí es el hex string del {@code ObjectId} propio del
 * documento en la colección {@code carritos} (ese campo no cambió de
 * tipo).
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

    private Long clienteId;
    private String carritoId;
    private String razonSocial;
    private String rutEmpresa;
    private String direccionEnvio;
    private DatosPagoMockDto datosPago;
}
