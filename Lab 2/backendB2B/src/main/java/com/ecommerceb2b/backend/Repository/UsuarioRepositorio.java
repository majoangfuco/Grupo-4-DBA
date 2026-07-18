package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private RowMapper<UsuarioEntidad> rowMapper = new RowMapper<UsuarioEntidad>() {
        @Override
        public UsuarioEntidad mapRow(ResultSet rs, int rowNum) throws SQLException {
            UsuarioEntidad usuario = new UsuarioEntidad();
            usuario.setUsuario_ID(rs.getLong("usuario_id"));
            usuario.setNombre_Usuario(rs.getString("nombre_usuario"));
            usuario.setCorreo(rs.getString("correo"));
            usuario.setContrasena(rs.getString("contrasena"));
            usuario.setUltima_Compra(rs.getTimestamp("ultima_compra"));
            usuario.setRut_Empresa(rs.getString("rut_empresa"));
            usuario.setRol(rs.getString("rol"));
            return usuario;
        }
    };

    public Optional<UsuarioEntidad> findByCorreo(String correo) {
        String sql = "SELECT * FROM usuario_entidad WHERE LOWER(correo) = LOWER(?)";
        try {
            UsuarioEntidad usuario = jdbcTemplate.queryForObject(sql, rowMapper, correo);
            return Optional.of(usuario);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<UsuarioEntidad> findById(Long id) {
        String sql = "SELECT * FROM usuario_entidad WHERE usuario_id = ?";
        try {
            UsuarioEntidad usuario = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.of(usuario);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void save(UsuarioEntidad usuario) {
        String sql = "INSERT INTO usuario_entidad (nombre_usuario, correo, contrasena, rut_empresa, rol) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, 
            usuario.getNombre_Usuario(),
            usuario.getCorreo(),
            usuario.getContrasena(),
            usuario.getRut_Empresa(),
            usuario.getRol() != null ? usuario.getRol() : "CLIENTE"
        );
    }

    public List<UsuarioEntidad> findByRol(String rol) {
        String sql = "SELECT * FROM usuario_entidad WHERE rol = ?";
        return jdbcTemplate.query(sql, rowMapper, rol);
    }

    public List<UsuarioEntidad> findAll() {
        String sql = "SELECT * FROM usuario_entidad ORDER BY usuario_id";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /** Verifica si otro usuario (distinto de excluirId) ya usa este correo. */
    public boolean existeCorreoEnOtroUsuario(String correo, Long excluirId) {
        String sql = "SELECT COUNT(*) FROM usuario_entidad WHERE LOWER(correo) = LOWER(?) AND usuario_id <> ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, correo, excluirId);
        return count != null && count > 0;
    }

    /**
     * Actualiza los datos editables del usuario. La contraseña solo se cambia
     * cuando se entrega una nueva (ya codificada); si es null, se conserva.
     */
    public int actualizar(Long usuarioId, String nombre, String correo,
                          String rutEmpresa, String contrasenaCodificada) {
        if (contrasenaCodificada != null) {
            String sql = "UPDATE usuario_entidad SET nombre_usuario = ?, correo = ?, "
                    + "rut_empresa = ?, contrasena = ? WHERE usuario_id = ?";
            return jdbcTemplate.update(sql, nombre, correo, rutEmpresa, contrasenaCodificada, usuarioId);
        }
        String sql = "UPDATE usuario_entidad SET nombre_usuario = ?, correo = ?, "
                + "rut_empresa = ? WHERE usuario_id = ?";
        return jdbcTemplate.update(sql, nombre, correo, rutEmpresa, usuarioId);
    }

    /**
     * Elimina al usuario y todo su historial dependiente dentro de una
     * transacción, respetando el orden de las llaves foráneas:
     * facturas -> órdenes -> entregas -> carritos -> datos de pago -> usuario.
     */
    public int eliminarEnCascada(Long usuarioId) {
        // 1. Facturas del usuario (los ítems se borran por ON DELETE CASCADE).
        jdbcTemplate.update("DELETE FROM factura_entidad WHERE usuario_usuario = ?", usuarioId);

        // 2. Rompe el ciclo órdenes <-> información de entrega.
        jdbcTemplate.update(
                "UPDATE informacion_entrega_entidad SET orden_orden_id = NULL WHERE usuario_usuario = ?",
                usuarioId);

        // 3. Órdenes generadas desde los carritos o entregas del usuario.
        jdbcTemplate.update(
                "DELETE FROM ordenes_entidad WHERE carrito_carrito_id IN "
                        + "(SELECT carrito_id FROM carrito_entidad WHERE carrito_usuario_id = ?) "
                        + "OR informacion_info_entrega_id IN "
                        + "(SELECT info_entrega_id FROM informacion_entrega_entidad WHERE usuario_usuario = ?)",
                usuarioId, usuarioId);

        // 4. Direcciones de entrega del usuario.
        jdbcTemplate.update(
                "DELETE FROM informacion_entrega_entidad WHERE usuario_usuario = ?", usuarioId);

        // 5. Productos de los carritos y luego los carritos.
        jdbcTemplate.update(
                "DELETE FROM carrito_producto_entidad WHERE carrito_carrito_id IN "
                        + "(SELECT carrito_id FROM carrito_entidad WHERE carrito_usuario_id = ?)",
                usuarioId);
        jdbcTemplate.update("DELETE FROM carrito_entidad WHERE carrito_usuario_id = ?", usuarioId);

        // 6. Datos de pago del usuario.
        jdbcTemplate.update("DELETE FROM datos_pago_entidad WHERE usuario_usuario = ?", usuarioId);

        // 7. Finalmente, el usuario.
        return jdbcTemplate.update("DELETE FROM usuario_entidad WHERE usuario_id = ?", usuarioId);
    }

    public void actualizarUltimaCompra(Long usuarioId) {
        String sql = "UPDATE usuario_entidad SET ultima_compra = NOW() WHERE usuario_id = ?";
        jdbcTemplate.update(sql, usuarioId);
    }
}
