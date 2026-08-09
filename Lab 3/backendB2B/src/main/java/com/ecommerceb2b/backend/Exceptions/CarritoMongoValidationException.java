package com.ecommerceb2b.backend.Exceptions;

/**
 * Se lanza cuando Mongo rechaza una escritura sobre la colección
 * "carritos" por el validador de colección (o por un
 * ObjectId con formato inválido). El controller la atrapa y responde
 * 400 con un mensaje legible.
 */
public class CarritoMongoValidationException extends RuntimeException {
    public CarritoMongoValidationException(String message) {
        super(message);
    }
}
