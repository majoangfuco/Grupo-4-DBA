package com.ecommerceb2b.backend.Util;

import java.util.List;

/**
 * Normaliza coordenadas geográficas recibidas en el body de una petición,
 * aceptando tanto el formato plano ya usado por el frontend como un objeto
 * GeoJSON Point estándar (RFC 7946).
 *
 * Formatos aceptados:
 * - Plano:   { "latitud": -33.45, "longitud": -70.65 }
 * - GeoJSON: { "type": "Point", "coordinates": [-70.65, -33.45] }
 *            (GeoJSON declara [longitud, latitud], en ese orden)
 */
public final class CoordenadasNormalizador {

    private CoordenadasNormalizador() {
    }

    public record Coordenadas(Double latitud, Double longitud) {
    }

    /**
     * @param latitud     campo "latitud" plano, si vino en ese formato.
     * @param longitud    campo "longitud" plano, si vino en ese formato.
     * @param type        campo "type" de un GeoJSON Point (ej. "Point").
     * @param coordinates campo "coordinates" de un GeoJSON Point: [lon, lat].
     * @return coordenadas normalizadas como { latitud, longitud }.
     * @throws IllegalArgumentException si el body no calza con ninguno de los
     *                                   dos formatos soportados.
     */
    public static Coordenadas normalizar(
            Double latitud,
            Double longitud,
            String type,
            List<Double> coordinates
    ) {
        if (latitud != null && longitud != null) {
            return new Coordenadas(latitud, longitud);
        }

        if ("Point".equalsIgnoreCase(type)
                && coordinates != null
                && coordinates.size() == 2
                && coordinates.get(0) != null
                && coordinates.get(1) != null) {
            return new Coordenadas(coordinates.get(1), coordinates.get(0));
        }

        throw new IllegalArgumentException(
                "Formato de coordenadas inválido, use {latitud,longitud} o GeoJSON Point"
        );
    }
}
