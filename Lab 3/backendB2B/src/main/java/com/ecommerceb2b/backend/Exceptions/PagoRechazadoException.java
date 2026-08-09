package com.ecommerceb2b.backend.Exceptions;

/**
 * Se lanza cuando el mock de validación de pago del checkout documental
 * (Lab 3) responde {@code aprobado: false}. Igual que
 * {@link StockInsuficienteException}, lanzarla dentro de la transacción
 * dispara el abort automático de {@code ClientSession.withTransaction(...)}:
 * el stock ya descontado en esa misma transacción (todavía sin commit) se
 * revierte solo, sin necesidad de una compensación manual.
 */
public class PagoRechazadoException extends RuntimeException {

    private final String referencia;

    public PagoRechazadoException(String referencia) {
        super("El pago fue rechazado (referencia: " + referencia + ")");
        this.referencia = referencia;
    }

    public String getReferencia() {
        return referencia;
    }
}
