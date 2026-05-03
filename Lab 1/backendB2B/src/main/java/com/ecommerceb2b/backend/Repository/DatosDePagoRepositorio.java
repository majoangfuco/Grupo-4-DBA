package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.DatosDePagoEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@Repository
public class DatosDePagoRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public DatosDePagoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DatosDePagoEntidad> rowMapper = new RowMapper<>() {
        @Override
        public DatosDePagoEntidad mapRow(ResultSet rs, int rowNum) throws SQLException {
            DatosDePagoEntidad datos = new DatosDePagoEntidad();
            datos.setDatos_Pago_ID(rs.getLong("datos_pago_id"));
            datos.setUsuario_ID(rs.getLong("usuario_usuario"));
            datos.setMetodo_Pago(rs.getString("metodo_pago"));
            datos.setNumero_Tarjeta(rs.getString("numero_tarjeta"));
            datos.setFecha_Expiracion(rs.getString("fecha_expiracion"));
            return datos;
        }
    };

    public Optional<DatosDePagoEntidad> findById(Long id) {
        String sql = "SELECT * FROM datos_pago_entidad WHERE datos_pago_id = ?";
        try {
            DatosDePagoEntidad datos = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.of(datos);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<DatosDePagoEntidad> findByUsuarioId(Long usuarioId) {
        String sql = "SELECT * FROM datos_pago_entidad WHERE usuario_usuario = ? ORDER BY datos_pago_id DESC";
        return jdbcTemplate.query(sql, rowMapper, usuarioId);
    }

    public Long save(DatosDePagoEntidad datos) {
        String sql = "INSERT INTO datos_pago_entidad (usuario_usuario, metodo_pago, numero_tarjeta, fecha_expiracion) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator psc = connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"datos_pago_id"});
            ps.setLong(1, datos.getUsuario_ID());
            ps.setString(2, datos.getMetodo_Pago());
            ps.setString(3, datos.getNumero_Tarjeta());
            ps.setString(4, datos.getFecha_Expiracion());
            return ps;
        };
        jdbcTemplate.update(psc, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(DatosDePagoEntidad datos) {
        String sql = "UPDATE datos_pago_entidad SET usuario_usuario = ?, metodo_pago = ?, numero_tarjeta = ?, fecha_expiracion = ? WHERE datos_pago_id = ?";
        jdbcTemplate.update(sql,
                datos.getUsuario_ID(),
                datos.getMetodo_Pago(),
                datos.getNumero_Tarjeta(),
                datos.getFecha_Expiracion(),
                datos.getDatos_Pago_ID()
        );
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM datos_pago_entidad WHERE datos_pago_id = ?";
        jdbcTemplate.update(sql, id);
    }
}
