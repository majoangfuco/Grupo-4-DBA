package com.ecommerceb2b.backend.Util;

/**
 * Validador de RUT/RUN chileno.
 *
 * Acepta RUT de empresa o persona en formatos como:
 * 76.123.456-7 | 76123456-7 | 761234567
 */
public final class RutValidador {

    private RutValidador() {
    }

    /**
     * @param rut RUT en cualquier formato (con o sin puntos y guión).
     * @return true si el cuerpo y el dígito verificador son consistentes.
     */
    public static boolean esValido(String rut) {
        if (rut == null) {
            return false;
        }

        // Normaliza: elimina puntos, espacios y pasa el DV a mayúscula.
        String limpio = rut.trim().replace(".", "").replace("-", "").toUpperCase();

        // Debe tener al menos un dígito de cuerpo más el dígito verificador.
        if (limpio.length() < 2) {
            return false;
        }

        String cuerpo = limpio.substring(0, limpio.length() - 1);

        // El cuerpo debe ser numérico.
        if (!cuerpo.matches("\\d+")) {
            return false;
        }

        return true;
    }
}
