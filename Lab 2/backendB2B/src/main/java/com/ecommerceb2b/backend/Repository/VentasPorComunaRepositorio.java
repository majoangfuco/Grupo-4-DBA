package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.VentasPorComunaEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class VentasPorComunaRepositorio {

    private final JdbcTemplate jdbc;

    public VentasPorComunaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<VentasPorComunaEntidad> rowMapper = (rs, rowNum) -> new VentasPorComunaEntidad(
            rs.getString("comuna"),
            rs.getInt("cantidad_ordenes"),
            rs.getBigDecimal("volumen_ventas"),
            rs.getString("geom_geojson")
    );

    public List<VentasPorComunaEntidad> findAll() {
        String sql = """
                SELECT comuna, cantidad_ordenes, volumen_ventas,
                       ST_AsGeoJSON(geom_entregas) AS geom_geojson
                FROM vw_ventas_por_comuna
                ORDER BY volumen_ventas DESC
                """;
        return jdbc.query(sql, rowMapper);
    }

    // FeatureCollection GeoJSON lista para un mapa de "Análisis de Mercado"
    public String findAllAsGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(geom_entregas)::json,
                            'properties', json_build_object(
                                'comuna', comuna,
                                'cantidad_ordenes', cantidad_ordenes,
                                'volumen_ventas', volumen_ventas
                            )
                        )
                    ), '[]')
                )::text AS geojson
                FROM vw_ventas_por_comuna
                """;
        return jdbc.queryForObject(sql, String.class);
    }

    // Dispara el refresco de la vista materializada (Req. A: lógica de refresco)
    public void refrescar() {
        jdbc.execute("SELECT refrescar_vw_ventas_por_comuna()");
    }
}
