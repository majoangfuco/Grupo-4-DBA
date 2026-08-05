package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.AlmacenEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AlmacenRepositorio {

    private final JdbcTemplate jdbc;

    public AlmacenRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_COLUMNAS = """
            SELECT almacen_id, nombre, direccion,
                   ST_X(ubicacion) AS longitud, ST_Y(ubicacion) AS latitud
            FROM almacen_entidad
            """;

    private final RowMapper<AlmacenEntidad> rowMapper = (rs, rowNum) -> {
        AlmacenEntidad a = new AlmacenEntidad();
        a.setAlmacenId(rs.getLong("almacen_id"));
        a.setNombre(rs.getString("nombre"));
        a.setDireccion(rs.getString("direccion"));
        a.setLongitud(rs.getDouble("longitud"));
        a.setLatitud(rs.getDouble("latitud"));
        return a;
    };

    public List<AlmacenEntidad> findAll() {
        return jdbc.query(SELECT_COLUMNAS, rowMapper);
    }

    public Optional<AlmacenEntidad> findById(Long id) {
        List<AlmacenEntidad> result = jdbc.query(
                SELECT_COLUMNAS + " WHERE almacen_id = ?", rowMapper, id
        );
        return result.stream().findFirst();
    }

    public Long crear(AlmacenEntidad a) {
        String sql = """
                INSERT INTO almacen_entidad (nombre, direccion, ubicacion)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                RETURNING almacen_id
                """;
        return jdbc.queryForObject(sql, Long.class,
                a.getNombre(), a.getDireccion(), a.getLongitud(), a.getLatitud());
    }

    public int actualizar(AlmacenEntidad a) {
        return jdbc.update(
                """
                UPDATE almacen_entidad
                SET nombre = ?, direccion = ?, ubicacion = ST_SetSRID(ST_MakePoint(?, ?), 4326)
                WHERE almacen_id = ?
                """,
                a.getNombre(), a.getDireccion(), a.getLongitud(), a.getLatitud(), a.getAlmacenId()
        );
    }

    public int borrarPorId(Long id) {
        return jdbc.update("DELETE FROM almacen_entidad WHERE almacen_id = ?", id);
    }

    // Encuentra el almacén más cercano a un punto dado,
    // usando el operador KNN (<->) para aprovechar el índice GIST.
    // Devuelve Optional.empty() si no hay coordenadas o no hay almacenes.
    public Optional<Long> findMasCercano(Double longitud, Double latitud) {
        if (longitud == null || latitud == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT almacen_id
                FROM almacen_entidad
                ORDER BY ubicacion <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)
                LIMIT 1
                """;
        List<Long> result = jdbc.query(sql, (rs, rowNum) -> rs.getLong("almacen_id"), longitud, latitud);
        return result.stream().findFirst();
    }

    // Devuelve todos los almacenes como una FeatureCollection GeoJSON,
    // lista para pintar en un mapa en el frontend.
    public String findAllAsGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(ubicacion)::json,
                            'properties', json_build_object(
                                'almacen_id', almacen_id,
                                'nombre', nombre,
                                'direccion', direccion
                            )
                        )
                    ), '[]')
                )::text AS geojson
                FROM almacen_entidad
                """;
        return jdbc.queryForObject(sql, String.class);
    }
}