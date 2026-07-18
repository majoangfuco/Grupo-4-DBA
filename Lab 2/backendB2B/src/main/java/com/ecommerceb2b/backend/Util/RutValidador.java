package com.ecommerceb2b.backend.Util;

/**
 * Validador de RUT/RUN chileno.
 *
 * Acepta RUT de empresa o persona en formatos como:
 *   76.123.456-7   |   76123456-7   |   761234567
 * Verifica el dígito verificador mediante el algoritmo Módulo 11.
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
        char digitoVerificador = limpio.charAt(limpio.length() - 1);

        // El cuerpo debe ser numérico.
        if (!cuerpo.matches("\\d+")) {
            return false;
        }

        return calcularDigitoVerificador(cuerpo) == digitoVerificador;
    }

    /**
     * Calcula el dígito verificador (0-9 o K) para un cuerpo numérico.
     */
    private static char calcularDigitoVerificador(String cuerpo) {
        int suma = 0;
        int multiplicador = 2;

        // Recorre el cuerpo de derecha a izquierda con multiplicadores 2..7 cíclicos.
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * multiplicador;
            multiplicador = (multiplicador == 7) ? 2 : multiplicador + 1;
        }

        int resto = 11 - (suma % 11);

        if (resto == 11) {
            return '0';
        } else if (resto == 10) {
            return 'K';
        } else {
            return Character.forDigit(resto, 10);
        }
    }
}
