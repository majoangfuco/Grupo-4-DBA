package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de {@code POST /api/checkout} (checkout documental, Lab 3).
 *
 * {@code clienteId} y {@code carritoId} son {@code Long}: mismos valores
 * que {@code usuario_ID} de Postgres y que el {@code _id} real del
 * documento de carrito (colección {@code carritos}, poblado por
 * {@code CarritoRepositorio.crearCarrito} vía el contador
 * {@code contadores["carritos"]} — ver {@code mongo/indexes.js}). Ninguno
 * de los dos es {@code ObjectId} de Mongo: no existe una colección
 * {@code clientes} separada, y desde el commit que reescribió
 * {@code CarritoRepositorio}/{@code CarritoProductoRepositorio} el
 * carrito tampoco se identifica con {@code ObjectId}.
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
    private Long carritoId;
    private String razonSocial;
    private String rutEmpresa;
    private String direccionEnvio;
    private DatosPagoMockDto datosPago;
}
