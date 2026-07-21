package com.ecommerceb2b.backend.Repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LogisticaMapaRepositorio {

    private final JdbcTemplate jdbc;

    public LogisticaMapaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // FeatureCollection GeoJSON con ventas por comuna (polígonos reales de comuna_entidad).
    public String comunasGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(ST_Simplify(geom, 0.001))::json,
                            'properties', json_build_object(
                                'comuna_id', comuna_id,
                                'nombre', nombre_comuna,
                                'distrito_postal', distrito_postal,
                                'total_pedidos', total_pedidos,
                                'monto_total_ventas', monto_total_ventas,
                                'tiene_ventas', tiene_ventas,
                                'nivel_semaforo', nivel_semaforo
                            )
                        )
                    ), '[]')
                )::text
                FROM ventas_por_comuna
                """;
        return jdbc.queryForObject(sql, String.class);
    }

    // FeatureCollection GeoJSON con ventas agregadas por distrito postal.
    public String distritosGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(ST_Simplify(geom_union, 0.001))::json,
                            'properties', json_build_object(
                                'distrito_postal', distrito_postal,
                                'total_pedidos', total_pedidos,
                                'monto_total_ventas', monto_total_ventas,
                                'tiene_ventas', tiene_ventas,
                                'nivel_semaforo', nivel_semaforo
                            )
                        )
                    ), '[]')
                )::text
                FROM ventas_por_distrito
                """;
        return jdbc.queryForObject(sql, String.class);
    }

    // FeatureCollection GeoJSON con la fila activa de zona_cobertura_entidad
    // (contorno fijo de la RM real, ver ST_Union en Parte 1 de la tarea
    // anterior). 0 o 1 feature — nunca más de una fila activa a la vez.
    public String coberturaGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(ST_Simplify(geom, 0.001))::json,
                            'properties', json_build_object('nombre', nombre)
                        )
                    ), '[]')
                )::text
                FROM zona_cobertura_entidad
                WHERE activa = true
                """;
        return jdbc.queryForObject(sql, String.class);
    }

    // El orden importa: ventas_por_distrito se calcula a partir de ventas_por_comuna.
    public void refrescar() {
        jdbc.execute("SELECT refrescar_ventas_por_comuna_y_distrito()");
    }
}
