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

    // Columnas base + coordenadas extraídas de la geometría.
    private static final String SELECT_COLUMNAS = """
            SELECT info_entrega_id, usuario_usuario, orden_orden_id, direccion, numero,
                   rut_recibe_entrega, rut_empresa, comuna,
                   ST_X(ubicacion) AS longitud, ST_Y(ubicacion) AS latitud
            FROM informacion_entrega_entidad
            """;

    private final RowMapper<InformacionEntregaEntidad> rowMapper = (rs, rowNum) -> {
        InformacionEntregaEntidad e = new InformacionEntregaEntidad();
        e.setInfo_Entrega_ID(rs.getLong("info_entrega_id"));
        e.setUsuarioId(rs.getLong("usuario_usuario"));
        e.setOrdenId(rs.getLong("orden_orden_id"));
        e.setDireccion(rs.getString("direccion"));
        e.setNumero(rs.getString("numero"));
        e.setRut_Recibe_Entrega(rs.getString("rut_recibe_entrega"));
        e.setRut_Empresa(rs.getString("rut_empresa"));
        e.setEstado_Entrega(null);
        e.setActiva(true);
        e.setComuna(rs.getString("comuna"));

        double longitud = rs.getDouble("longitud");
        e.setLongitud(rs.wasNull() ? null : longitud);
        double latitud = rs.getDouble("latitud");
        e.setLatitud(rs.wasNull() ? null : latitud);

        return e;
    };

    public List<InformacionEntregaEntidad> findAllActivas() {
        return jdbc.query(SELECT_COLUMNAS, rowMapper);
    }

    public Optional<InformacionEntregaEntidad> findById(Long id) {
        List<InformacionEntregaEntidad> result = jdbc.query(
                SELECT_COLUMNAS + " WHERE info_entrega_id = ?",
                rowMapper, id
        );
        return result.stream().findFirst();
    }

    public List<InformacionEntregaEntidad> findByUsuarioId(Long usuarioId) {
        return jdbc.query(
                SELECT_COLUMNAS + " WHERE usuario_usuario = ?",
                rowMapper, usuarioId
        );
    }

    public int save(InformacionEntregaEntidad e) {
        // ST_MakePoint es STRICT: si longitud o latitud vienen null,
        // el resultado es null y "ubicacion" queda sin coordenadas.
        return jdbc.update(
                """
                INSERT INTO informacion_entrega_entidad
                    (usuario_usuario, orden_orden_id, direccion, numero,
                     rut_recibe_entrega, rut_empresa, comuna, ubicacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                """,
                e.getUsuarioId(), e.getOrdenId(), e.getDireccion(), e.getNumero(),
                e.getRut_Recibe_Entrega(), e.getRut_Empresa(), e.getComuna(),
                e.getLongitud(), e.getLatitud()
        );
    }

    public int update(InformacionEntregaEntidad e) {
        return jdbc.update(
                """
                UPDATE informacion_entrega_entidad
                SET direccion = ?, numero = ?, rut_recibe_entrega = ?,
                    rut_empresa = ?, comuna = ?,
                    ubicacion = ST_SetSRID(ST_MakePoint(?, ?), 4326)
                WHERE info_entrega_id = ?
                """,
                e.getDireccion(), e.getNumero(), e.getRut_Recibe_Entrega(),
                e.getRut_Empresa(), e.getComuna(), e.getLongitud(), e.getLatitud(),
                e.getInfo_Entrega_ID()
        );
    }

    public int softDelete(Long id) {
        return jdbc.update(
                "DELETE FROM informacion_entrega_entidad WHERE info_entrega_id = ?",
                id
        );
    }

    // Devuelve la ubicación como formatos geoespaciales como GeoJSON.
    public Optional<String> findUbicacionGeoJson(Long id) {
        List<String> result = jdbc.query(
                "SELECT ST_AsGeoJSON(ubicacion) AS geojson FROM informacion_entrega_entidad WHERE info_entrega_id = ?",
                (rs, rowNum) -> rs.getString("geojson"),
                id
        );
        return result.stream().findFirst();
    }
}