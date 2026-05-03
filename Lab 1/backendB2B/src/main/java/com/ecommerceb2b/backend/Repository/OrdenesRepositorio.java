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
        o.setFecha_Orden(rs.getDate("fecha_orden"));
        o.setEstado(rs.getString("estado"));
        return o;
    };

    private String getStringOrNull(java.sql.ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    // Crear
    public int crear(OrdenesEntidad o) {
        String sql = """
                INSERT INTO ordenes_entidad
                (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado)
                VALUES (?, ?, ?, ?)
                """;
        return jdbcTemplate.update(sql,
                o.getCarrito_ID(),
                o.getInfo_Entrega_ID(),
                o.getFecha_Orden(),
                o.getEstado());
    }

    // todos
    public List<OrdenesEntidad> encontrarTodos() {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    // encontrar por ID
    public Optional<OrdenesEntidad> encontrarPorId(Long id) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                WHERE o.orden_id = ?
                """;
        List<OrdenesEntidad> result = jdbcTemplate.query(sql, rowMapper, id);
        return result.stream().findFirst();
    }

    // actualizar
    public int actualizar(OrdenesEntidad o) {
        String sql = """
                UPDATE ordenes_entidad SET
                carrito_carrito_id = ?, informacion_info_entrega_id = ?, fecha_orden = ?, estado = ?
                WHERE orden_id = ?
                """;
        return jdbcTemplate.update(sql,
                o.getCarrito_ID(),
                o.getInfo_Entrega_ID(),
                o.getFecha_Orden(),
                o.getEstado(),
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
                SELECT o.*, c.carrito_usuario_id AS usuario_id
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                WHERE o.estado = ?
                """;
        return jdbcTemplate.query(sql, rowMapper, estado);
    }

    // buscar por usuario
    // Como la tabla ordenes_entidad no tiene usuario_id, necesitamos hacer un JOIN con carrito_entidad
    public List<OrdenesEntidad> encontrarPorUsuarioId(Long usuarioId){
       String sql = """
               SELECT o.*, c.carrito_usuario_id AS usuario_id
               FROM ordenes_entidad o
               JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
               WHERE c.carrito_usuario_id = ?
               """;
       return jdbcTemplate.query(sql, rowMapper, usuarioId);

    }

    // buscar por fecha 
    public List<OrdenesEntidad> encontrarPorFechaOrden(java.util.Date fecha) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                WHERE o.fecha_orden > ?
                """;
        return jdbcTemplate.query(sql, rowMapper, fecha);
    }

    public List<OrdenesEntidad> encontrarTodosConRut() {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }
}
