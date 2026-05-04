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

    public void actualizarUltimaCompra(Long usuarioId) {
        String sql = "UPDATE usuario_entidad SET ultima_compra = NOW() WHERE usuario_id = ?";
        jdbcTemplate.update(sql, usuarioId);
    }
}
