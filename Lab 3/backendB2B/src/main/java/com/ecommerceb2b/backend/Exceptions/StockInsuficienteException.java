package com.ecommerceb2b.backend.Exceptions;

/**
 * Se lanza cuando el checkout documental (Lab 3) intenta descontar stock de
 * un producto y el {@code updateOne} condicional
 * ({@code {_id: productoId, stock: {$gte: cantidad}}}) no matchea ningún
 * documento: o el producto no existe en la copia Mongo de {@code productos}
 * (ver {@code mongo/seeders/productos-seed.js}), o no tiene stock
 * suficiente para la cantidad pedida.
 *
 * {@code productoId} es {@code Long} — mismo valor que {@code producto_ID}
 * de Postgres, igual que {@code clienteId} (ver nota en
 * {@code mongo/schema-validation.js}, colección {@code carritos}).
 *
 * Lanzarla dentro del lambda de {@code ClientSession.withTransaction(...)}
 * es lo que dispara el abort de la transacción completa (ver
 * {@link com.ecommerceb2b.backend.Services.CheckoutServicio}): el driver
 * aborta y relanza esta misma excepción sin necesidad de un
 * {@code abortTransaction()} manual.
 */
public class StockInsuficienteException extends RuntimeException {

    private final long productoId;
    private final long cantidadSolicitada;

    public StockInsuficienteException(long productoId, long cantidadSolicitada) {
        super("Stock insuficiente para el producto " + productoId
                + " (cantidad solicitada: " + cantidadSolicitada + ")");
        this.productoId = productoId;
        this.cantidadSolicitada = cantidadSolicitada;
    }

    public long getProductoId() {
        return productoId;
    }

    public long getCantidadSolicitada() {
        return cantidadSolicitada;
    }
}
