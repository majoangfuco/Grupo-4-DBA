package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.OrdenesEntidad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class OrdenesRepositorio {
    private final JdbcTemplate jdbcTemplate;

    public OrdenesRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper para OrdenesEntidad
    private final RowMapper<OrdenesEntidad> rowMapper = (rs, rowNum) -> {
        OrdenesEntidad o = new OrdenesEntidad();
        o.setOrden_ID(rs.getLong("orden_id"));
        o.setCarrito_ID(rs.getLong("carrito_carrito_id"));
        long usuarioId = rs.getLong("usuario_id");
        o.setUsuario_ID(rs.wasNull() ? null : usuarioId);
        o.setRut_Empresa(getStringOrNull(rs, "rut_empresa"));
        o.setInfo_Entrega_ID(rs.getLong("informacion_info_entrega_id"));
        java.sql.Timestamp ts = rs.getTimestamp("fecha_orden");
        o.setFecha_Orden(ts != null ? new java.util.Date(ts.getTime()) : null);
        o.setEstado(rs.getString("estado"));
        long almacenId = rs.getLong("almacen_asignado_id");
        o.setAlmacen_Asignado_ID(rs.wasNull() ? null : almacenId);
        o.setAlmacen_Nombre(getStringOrNull(rs, "almacen_nombre"));
        double distanciaKm = rs.getDouble("distancia_envio_km");
        o.setDistancia_envio_km(rs.wasNull() ? null : distanciaKm);

        return o;
    };

    private String getStringOrNull(java.sql.ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    // Si el trigger validar_cobertura_entrega rechaza
    // el INSERT (dirección fuera de cobertura, o sin coordenadas), Postgres
    // lanza una excepción que JDBC envuelve en una DataAccessException. Acá
    // la traducimos a IllegalStateException con el mensaje original y limpio,
    // para que el Controller la capture y devuelva un 400 en vez de un 500.
    public Long crear(OrdenesEntidad o) {
        String sql = """
                INSERT INTO ordenes_entidad
                (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado, almacen_asignado_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING orden_id
                """;
        try {
            Long ordenId = jdbcTemplate.queryForObject(sql, Long.class,
                    o.getCarrito_ID(),
                    o.getInfo_Entrega_ID(),
                    new java.sql.Timestamp(o.getFecha_Orden().getTime()),
                    o.getEstado(),
                    o.getAlmacen_Asignado_ID());

            if (ordenId == null) {
                throw new IllegalStateException("No se pudo crear la orden");
            }
            return ordenId;
        } catch (org.springframework.dao.DataAccessException e) {
            throw new IllegalStateException(extraerMensajeAmigable(e));
        }
    }

    // Extrae el mensaje de la excepción de PostgreSQL (RAISE EXCEPTION del
    // trigger), sin el prefijo técnico "ERROR:" ni detalles internos del driver.
    private String extraerMensajeAmigable(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage();
        if (mensaje == null) {
            return "No se pudo crear la orden";
        }
        int idx = mensaje.indexOf("ERROR:");
        if (idx >= 0) {
            mensaje = mensaje.substring(idx + "ERROR:".length()).split("\n")[0].trim();
        }
        return mensaje;
    }

    // todos
    public List<OrdenesEntidad> encontrarTodos() {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    // encontrar por ID
    public Optional<OrdenesEntidad> encontrarPorId(Long id) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                WHERE o.orden_id = ?
                """;
        List<OrdenesEntidad> result = jdbcTemplate.query(sql, rowMapper, id);
        return result.stream().findFirst();
    }

    // actualizar
    public int actualizar(OrdenesEntidad o) {
        String sql = """
                UPDATE ordenes_entidad SET
                carrito_carrito_id = ?, informacion_info_entrega_id = ?, fecha_orden = ?, estado = ?,
                almacen_asignado_id = ?
                WHERE orden_id = ?
                """;
        return jdbcTemplate.update(sql,
                o.getCarrito_ID(),
                o.getInfo_Entrega_ID(),
                new java.sql.Timestamp(o.getFecha_Orden().getTime()),
                o.getEstado(),
                o.getAlmacen_Asignado_ID(),
                o.getOrden_ID());
    }

    // eliminar
    public int borrarPorId(Long id) {
        String sql = "DELETE FROM ordenes_entidad WHERE orden_id = ?";
        return jdbcTemplate.update(sql, id);
    }

    // buscar por estado
    public List<OrdenesEntidad> encontrarPorEstado(String estado) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                WHERE o.estado = ?
                """;
        return jdbcTemplate.query(sql, rowMapper, estado);
    }

    // buscar por usuario
    // Como la tabla ordenes_entidad no tiene usuario_id, necesitamos hacer un JOIN con carrito_entidad
    public List<OrdenesEntidad> encontrarPorUsuarioId(Long usuarioId){
        String sql = """
               SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                      alm.nombre AS almacen_nombre,
                      ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
               FROM ordenes_entidad o
               JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
               JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
               LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
               LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
               WHERE c.carrito_usuario_id = ?
               """;
        return jdbcTemplate.query(sql, rowMapper, usuarioId);

    }

    // buscar por fecha 
    public List<OrdenesEntidad> encontrarPorFechaOrden(java.util.Date fecha) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                WHERE o.fecha_orden > ?
                """;
        return jdbcTemplate.query(sql, rowMapper, fecha);
    }

    public List<OrdenesEntidad> encontrarTodosConRut() {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    public int actualizarEstado(Long ordenId, String estado) {
        String sql = """
            UPDATE ordenes_entidad
            SET estado = ?
            WHERE orden_id = ?
            """;

        return jdbcTemplate.update(
                sql,
                estado,
                ordenId
        );
    }


    /**
     * Ejecuta el checkout geoespacial completo en PostgreSQL.
     * El procedimiento valida cobertura y restricciones, selecciona mediante
     * ST_Distance el almacén más cercano con stock, crea orden/factura y
     * descuenta el inventario dentro de la misma transacción.
     */
    public Long procesarCheckout(
            Long carritoId,
            Long infoEntregaId,
            Long datosPagoId
    ) {
        try {
            jdbcTemplate.update(
                    "CALL procesar_checkout(?, ?, ?)",
                    carritoId,
                    infoEntregaId,
                    datosPagoId
            );

            Long ordenId = jdbcTemplate.queryForObject(
                    """
                    SELECT o.orden_id
                    FROM ordenes_entidad o
                    JOIN factura_entidad f
                      ON f.orden_orden_id = o.orden_id
                    WHERE o.carrito_carrito_id = ?
                      AND o.informacion_info_entrega_id = ?
                      AND f.datos_pago_id = ?
                    ORDER BY o.orden_id DESC
                    LIMIT 1
                    """,
                    Long.class,
                    carritoId,
                    infoEntregaId,
                    datosPagoId
            );

            if (ordenId == null) {
                throw new IllegalStateException(
                        "El procedimiento terminó sin generar una orden"
                );
            }

            return ordenId;

        } catch (org.springframework.dao.DataAccessException e) {
            throw new IllegalStateException(extraerMensajeAmigable(e));
        }
    }

}