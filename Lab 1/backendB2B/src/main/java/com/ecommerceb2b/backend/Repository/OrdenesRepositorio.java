package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.OrdenesEntidad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrdenesRepositorio {
private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_BASE = """
         SELECT o.orden_id,
             o.carrito_carrito_id,
             o.informacion_info_entrega_id,
             o.fecha_orden,
             o.estado,
             c.carrito_usuario_id
         FROM ordenes_entidad o
         JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
         """;

    public OrdenesRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper reutilizable
    private final RowMapper<OrdenesEntidad> rowMapper = (rs, rowNum) -> {
        OrdenesEntidad o = new OrdenesEntidad();
        o.setOrden_ID(rs.getLong("orden_id"));
        o.setCarrito_ID(rs.getLong("carrito_carrito_id"));
        o.setUsuario_ID(rs.getLong("carrito_usuario_id"));
        o.setInfo_Entrega_ID(rs.getLong("informacion_info_entrega_id"));
        o.setFecha_Orden(rs.getDate("fecha_orden"));
        o.setEstado(rs.getString("estado"));
        return o;
    };

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
        String sql = SELECT_BASE;
        return jdbcTemplate.query(sql, rowMapper);
    }

    // encontrar por ID
    public Optional<OrdenesEntidad> encontrarPorId(Long id) {
        String sql = SELECT_BASE + " WHERE o.orden_id = ?";
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
        String sql = SELECT_BASE + " WHERE o.estado = ?";
        return jdbcTemplate.query(sql, rowMapper, estado);
    }

    // buscar por usuario
    public List<OrdenesEntidad> encontrarPorUsuarioId(Long usuarioId){
         String sql = SELECT_BASE + " WHERE c.carrito_usuario_id = ?";
       return jdbcTemplate.query(sql, rowMapper, usuarioId);

    }

    // buscar por fecha 
    public List<OrdenesEntidad> encontrarPorFechaOrden(java.util.Date fecha) {
        String sql = SELECT_BASE + " WHERE o.fecha_orden > ?";
        return jdbcTemplate.query(sql, rowMapper, fecha);
    }
}




