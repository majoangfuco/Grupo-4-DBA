package com.ecommerceb2b.backend.Exceptions;

/**
 * Se lanza cuando Mongo rechaza una escritura sobre la colección
 * "carritos" por el validador de colección Se traduce a un mensaje de error legible.
 */
public class CarritoMongoValidationException extends RuntimeException {
    public CarritoMongoValidationException(String message) {
        super(message);
    }
}
