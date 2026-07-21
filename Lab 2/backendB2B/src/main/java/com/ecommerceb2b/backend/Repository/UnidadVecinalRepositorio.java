package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.UnidadVecinalEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UnidadVecinalRepositorio {

    private final JdbcTemplate jdbc;

    public UnidadVecinalRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ST_AsText: la geometría se expone como WKT, nunca como tipo JTS
    // (ver comentario en UnidadVecinalEntidad.geom).
    private static final String SELECT_COLUMNAS = """
            SELECT id, comuna_id, codigo_uv, nombre_uv,
                   ST_AsText(geom) AS geom_wkt, es_zona_protegida
            FROM unidad_vecinal_entidad
            """;

    private final RowMapper<UnidadVecinalEntidad> rowMapper = (rs, rowNum) -> {
        UnidadVecinalEntidad uv = new UnidadVecinalEntidad();
        uv.setId(rs.getLong("id"));
        uv.setComunaId(rs.getLong("comuna_id"));
        uv.setCodigoUv(rs.getString("codigo_uv"));
        uv.setNombreUv(rs.getString("nombre_uv"));
        uv.setGeom(rs.getString("geom_wkt"));
        uv.setEsZonaProtegida(rs.getBoolean("es_zona_protegida"));
        return uv;
    };

    /**
     * UPSERT idempotente por (comuna_id, codigo_uv), usado por el loader.
     * Deliberadamente NO toca es_zona_protegida en el UPDATE: si el admin
     * ya marcó una UV como protegida, volver a correr el loader no debe
     * resetear esa marca (se conserva el valor ya persistido / el default
     * de la columna solo aplica en el INSERT inicial).
     */
    public int insertarOActualizar(UnidadVecinalEntidad uv) {
        String sql = """
                INSERT INTO unidad_vecinal_entidad (comuna_id, codigo_uv, nombre_uv, geom)
                VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromText(?), 4326))
                ON CONFLICT (comuna_id, codigo_uv) DO UPDATE
                    SET nombre_uv = EXCLUDED.nombre_uv,
                        geom = EXCLUDED.geom
                """;
        return jdbc.update(sql, uv.getComunaId(), uv.getCodigoUv(), uv.getNombreUv(), uv.getGeom());
    }

    public List<UnidadVecinalEntidad> listarPorComuna(Long comunaId) {
        return jdbc.query(SELECT_COLUMNAS + " WHERE comuna_id = ? ORDER BY nombre_uv", rowMapper, comunaId);
    }

    // Usado por UnidadVecinalStartupLoader para decidir si la carga inicial
    // desde el GeoJSON ya corrió en esta base de datos.
    public long contar() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM unidad_vecinal_entidad", Long.class);
        return total != null ? total : 0L;
    }

    public int actualizarZonaProtegida(Long id, boolean esZonaProtegida) {
        return jdbc.update(
                "UPDATE unidad_vecinal_entidad SET es_zona_protegida = ? WHERE id = ?",
                esZonaProtegida, id
        );
    }

    // FeatureCollection GeoJSON de las UVs de una comuna, para pintar en Leaflet.
    // Mismo patrón que InformacionEntregaRepositorio.findAllAsGeoJson().
    public String listarPorComunaGeoJson(Long comunaId) {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(geom)::json,
                            'properties', json_build_object(
                                'id', id,
                                'nombreUv', nombre_uv,
                                'codigoUv', codigo_uv,
                                'esZonaProtegida', es_zona_protegida
                            )
                        )
                    ), '[]')
                )::text
                FROM unidad_vecinal_entidad
                WHERE comuna_id = ?
                """;
        return jdbc.queryForObject(sql, String.class, comunaId);
    }

    // FeatureCollection GeoJSON de TODAS las UVs marcadas como zona protegida,
    // sin filtrar por comuna — para pintarlas todas juntas en el mapa con un
    // toggle independiente. Mismo patrón que listarPorComunaGeoJson().
    public String listarProtegidasGeoJson() {
        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(
                        json_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(geom)::json,
                            'properties', json_build_object(
                                'id', id,
                                'nombreUv', nombre_uv,
                                'codigoUv', codigo_uv,
                                'comunaId', comuna_id,
                                'esZonaProtegida', es_zona_protegida
                            )
                        )
                    ), '[]')
                )::text
                FROM unidad_vecinal_entidad
                WHERE es_zona_protegida = true
                """;
        return jdbc.queryForObject(sql, String.class);
    }

    /**
     * Conteo agregado de UVs totales/protegidas por comuna — una sola query
     * GROUP BY, sin geometría. Pensado para poblar de una vez (al montar la
     * pantalla de gestión de zonas protegidas) el contexto "X de Y UV
     * protegidas" por comuna en un combobox filtrado en memoria, sin pagar
     * el costo de traer el GeoJSON completo de las 52 comunas solo para contar.
     */
    public List<Map<String, Object>> conteoPorComuna() {
        // Alias entrecomillado: Postgres pliega a minúsculas los identificadores
        // sin comillas (comunaId -> comunaid), y queryForList() usa el label de
        // columna tal cual como key del Map — sin comillas, el frontend recibe
        // "comunaid" en vez de "comunaId".
        String sql = """
                SELECT comuna_id AS "comunaId",
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE es_zona_protegida) AS protegidas
                FROM unidad_vecinal_entidad
                GROUP BY comuna_id
                """;
        return jdbc.queryForList(sql);
    }

    /**
     * Determina a qué comuna (de las 52 en comuna_entidad) pertenece un
     * polígono de UV, vía ST_Within del centroide — nunca por nombre de
     * texto (t_com_nom viene en mayúsculas sin tildes/ñ, no confiable).
     * Vacío si el centroide no cae dentro de ninguna comuna de cobertura.
     */
    public Optional<Long> encontrarComunaIdPorCentroide(String wktPoligono) {
        String sql = """
                SELECT id
                FROM comuna_entidad
                WHERE ST_Within(
                    ST_Centroid(ST_SetSRID(ST_GeomFromText(?), 4326)),
                    geom
                )
                LIMIT 1
                """;
        List<Long> result = jdbc.query(sql, (rs, rowNum) -> rs.getLong("id"), wktPoligono);
        return result.stream().findFirst();
    }
}
