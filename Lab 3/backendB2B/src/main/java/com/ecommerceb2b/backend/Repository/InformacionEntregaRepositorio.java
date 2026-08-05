package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InformacionEntregaRepositorio {

    private final JdbcTemplate jdbc;

    public InformacionEntregaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_COLUMNAS = """
            SELECT info_entrega_id, usuario_usuario, orden_orden_id,
                   direccion, numero, rut_recibe_entrega, rut_empresa,
                   estado_entrega, activa, comuna,
                   ST_X(ubicacion) AS longitud,
                   ST_Y(ubicacion) AS latitud
            FROM informacion_entrega_entidad
            """;

    private final RowMapper<InformacionEntregaEntidad> rowMapper = (rs, rowNum) -> {
        InformacionEntregaEntidad entrega = new InformacionEntregaEntidad();

        entrega.setInfo_Entrega_ID(rs.getLong("info_entrega_id"));
        entrega.setUsuarioId(rs.getLong("usuario_usuario"));

        long ordenId = rs.getLong("orden_orden_id");
        entrega.setOrdenId(rs.wasNull() ? null : ordenId);

        entrega.setDireccion(rs.getString("direccion"));
        entrega.setNumero(rs.getString("numero"));
        entrega.setRut_Recibe_Entrega(rs.getString("rut_recibe_entrega"));
        entrega.setRut_Empresa(rs.getString("rut_empresa"));
        entrega.setEstado_Entrega(rs.getString("estado_entrega"));

        boolean activa = rs.getBoolean("activa");
        entrega.setActiva(rs.wasNull() ? null : activa);

        entrega.setComuna(rs.getString("comuna"));

        double longitud = rs.getDouble("longitud");
        entrega.setLongitud(rs.wasNull() ? null : longitud);

        double latitud = rs.getDouble("latitud");
        entrega.setLatitud(rs.wasNull() ? null : latitud);

        return entrega;
    };

    public List<InformacionEntregaEntidad> findAllActivas() {
        return jdbc.query(
                SELECT_COLUMNAS + " WHERE activa = TRUE ORDER BY info_entrega_id",
                rowMapper
        );
    }

    public Optional<InformacionEntregaEntidad> findById(Long id) {
        List<InformacionEntregaEntidad> result = jdbc.query(
                SELECT_COLUMNAS + " WHERE info_entrega_id = ?",
                rowMapper,
                id
        );
        return result.stream().findFirst();
    }

    public List<InformacionEntregaEntidad> findByUsuarioId(Long usuarioId) {
        return jdbc.query(
                SELECT_COLUMNAS
                        + " WHERE usuario_usuario = ? AND activa = TRUE "
                        + "ORDER BY info_entrega_id",
                rowMapper,
                usuarioId
        );
    }

    public List<String> findComunasDisponibles() {
        return jdbc.query(
                "SELECT nombre FROM comuna_entidad ORDER BY nombre",
                (rs, rowNum) -> rs.getString("nombre")
        );
    }

    public boolean estaDentroCobertura(Double longitud, Double latitud) {
        Boolean resultado = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM zona_cobertura_entidad z
                    WHERE z.activa = TRUE
                      AND ST_Covers(
                          z.geom,
                          ST_SetSRID(ST_MakePoint(?, ?), 4326)
                      )
                )
                """,
                Boolean.class,
                longitud,
                latitud
        );

        return Boolean.TRUE.equals(resultado);
    }

    public int save(InformacionEntregaEntidad entrega) {
        return jdbc.update(
                """
                INSERT INTO informacion_entrega_entidad (
                    usuario_usuario,
                    orden_orden_id,
                    direccion,
                    numero,
                    rut_recibe_entrega,
                    rut_empresa,
                    estado_entrega,
                    activa,
                    comuna,
                    ubicacion
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)
                )
                """,
                entrega.getUsuarioId(),
                entrega.getOrdenId(),
                entrega.getDireccion(),
                entrega.getNumero(),
                entrega.getRut_Recibe_Entrega(),
                entrega.getRut_Empresa(),
                entrega.getEstado_Entrega(),
                entrega.getActiva(),
                entrega.getComuna(),
                entrega.getLongitud(),
                entrega.getLatitud()
        );
    }

    public int update(InformacionEntregaEntidad entrega) {
        return jdbc.update(
                """
                UPDATE informacion_entrega_entidad
                SET direccion = ?,
                    numero = ?,
                    rut_recibe_entrega = ?,
                    rut_empresa = ?,
                    estado_entrega = ?,
                    activa = ?,
                    comuna = ?,
                    ubicacion = ST_SetSRID(ST_MakePoint(?, ?), 4326)
                WHERE info_entrega_id = ?
                """,
                entrega.getDireccion(),
                entrega.getNumero(),
                entrega.getRut_Recibe_Entrega(),
                entrega.getRut_Empresa(),
                entrega.getEstado_Entrega(),
                entrega.getActiva(),
                entrega.getComuna(),
                entrega.getLongitud(),
                entrega.getLatitud(),
                entrega.getInfo_Entrega_ID()
        );
    }

    public int softDelete(Long id) {
        return jdbc.update(
                """
                UPDATE informacion_entrega_entidad
                SET activa = FALSE
                WHERE info_entrega_id = ?
                """,
                id
        );
    }

    public Optional<String> findUbicacionGeoJson(Long id) {
        List<String> result = jdbc.query(
                """
                SELECT ST_AsGeoJSON(ubicacion) AS geojson
                FROM informacion_entrega_entidad
                WHERE info_entrega_id = ?
                """,
                (rs, rowNum) -> rs.getString("geojson"),
                id
        );
        return result.stream().findFirst();
    }

    public String findAllAsGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(
                        json_agg(
                            json_build_object(
                                'type', 'Feature',
                                'geometry', ST_AsGeoJSON(ubicacion)::json,
                                'properties', json_build_object(
                                    'info_entrega_id', info_entrega_id,
                                    'direccion', direccion,
                                    'numero', numero,
                                    'comuna', comuna,
                                    'usuario_id', usuario_usuario
                                )
                            )
                        ),
                        '[]'::json
                    )
                )::text AS geojson
                FROM informacion_entrega_entidad
                WHERE activa = TRUE
                  AND ubicacion IS NOT NULL
                """;

        return jdbc.queryForObject(sql, String.class);
    }
}
