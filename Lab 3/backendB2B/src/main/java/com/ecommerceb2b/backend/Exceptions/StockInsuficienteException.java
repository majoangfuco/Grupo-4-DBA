package com.ecommerceb2b.backend.Exceptions;

import org.bson.types.ObjectId;

/**
 * Se lanza cuando el checkout documental (Lab 3) intenta descontar stock de
 * un producto y el {@code updateOne} condicional
 * ({@code {_id: productoId, stock: {$gte: cantidad}}}) no matchea ningún
 * documento: o el producto no existe, o no tiene stock suficiente para la
 * cantidad pedida.
 *
 * Lanzarla dentro del lambda de {@code ClientSession.withTransaction(...)}
 * es lo que dispara el abort de la transacción completa (ver
 * {@link com.ecommerceb2b.backend.Services.CheckoutServicio}): el driver
 * aborta y relanza esta misma excepción sin necesidad de un
 * {@code abortTransaction()} manual.
 */
public class StockInsuficienteException extends RuntimeException {

    private final String productoId;
    private final long cantidadSolicitada;

    public StockInsuficienteException(ObjectId productoId, long cantidadSolicitada) {
        super("Stock insuficiente para el producto " + productoId.toHexString()
                + " (cantidad solicitada: " + cantidadSolicitada + ")");
        this.productoId = productoId.toHexString();
        this.cantidadSolicitada = cantidadSolicitada;
    }

    public String getProductoId() {
        return productoId;
    }

    public long getCantidadSolicitada() {
        return cantidadSolicitada;
    }
}
