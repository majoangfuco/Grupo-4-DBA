package com.ecommerceb2b.backend.Repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio dedicado a la capacidad "verificar dirección" (dry-run, sin
 * persistir nada). Toca 3 tablas de dominios distintos (zona_cobertura,
 * comuna, unidad_vecinal) que no son "dueñas" naturales de esta lógica —
 * mismo criterio que LogisticaMapaRepositorio: un repositorio por
 * capacidad, no forzado dentro de un repositorio de entidad ajeno.
 */
@Repository
public class VerificacionDireccionRepositorio {

    private final JdbcTemplate jdbc;

    public VerificacionDireccionRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Misma semántica que trg_validar_cobertura_entrega(): ST_Covers (no
     * ST_Contains), para que un punto justo en el borde del polígono de
     * cobertura se comporte igual en este simulador que en el trigger real.
     */
    public boolean estaDentroCobertura(double lat, double lng) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM zona_cobertura_entidad z
                    WHERE z.activa = TRUE
                      AND ST_Covers(z.geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                )
                """;
        Boolean resultado = jdbc.queryForObject(sql, Boolean.class, lng, lat);
        return Boolean.TRUE.equals(resultado);
    }

    /**
     * Comuna real (de las 52) que contiene el punto, vía ST_Within — mismo
     * criterio espacial que trg_asignar_comuna_id(), pero para un punto
     * (lat,lng) suelto en vez de una ubicación ya persistida.
     */
    public Optional<Map<String, Object>> encontrarComunaPorPunto(double lat, double lng) {
        String sql = """
                SELECT id, nombre, distrito_postal
                FROM comuna_entidad
                WHERE ST_Within(ST_SetSRID(ST_MakePoint(?, ?), 4326), geom)
                LIMIT 1
                """;
        List<Map<String, Object>> resultado = jdbc.queryForList(sql, lng, lat);
        return resultado.stream().findFirst();
    }

    /** UV marcada como zona protegida que contiene el punto (si alguna). */
    public Optional<Map<String, Object>> encontrarZonaProtegidaPorPunto(double lat, double lng) {
        String sql = """
                SELECT id, nombre_uv
                FROM unidad_vecinal_entidad
                WHERE es_zona_protegida = true
                  AND ST_Within(ST_SetSRID(ST_MakePoint(?, ?), 4326), geom)
                LIMIT 1
                """;
        List<Map<String, Object>> resultado = jdbc.queryForList(sql, lng, lat);
        return resultado.stream().findFirst();
    }
}
