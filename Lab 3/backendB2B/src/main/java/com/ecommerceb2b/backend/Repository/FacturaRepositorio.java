package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@Repository
public class FacturaRepositorio {

    private final JdbcTemplate jdbc;

    public FacturaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Se une con usuario_entidad para exponer el RUT de la empresa en la factura.
    private static final String SELECT_BASE = """
            SELECT f.*, u.rut_empresa
            FROM factura_entidad f
            JOIN usuario_entidad u ON u.usuario_id = f.usuario_usuario
            """;

    private final RowMapper<FacturaEntidad> rowMapper = (rs, rowNum) -> {
        FacturaEntidad f = new FacturaEntidad();
        f.setFactura_ID(rs.getLong("factura_id"));
        f.setUsuarioId(rs.getLong("usuario_usuario"));
        f.setOrdenId(rs.getLong("orden_orden_id"));
        f.setPrecio_Total(rs.getFloat("precio_total"));
        f.setFecha_Emision(rs.getTimestamp("fecha_emision"));
        f.setTotal_Neto(rs.getFloat("total_neto"));
        f.setIva(rs.getFloat("iva"));
        f.setCosto_Envio(rs.getFloat("costo_envio"));
        f.setRut_Empresa(rs.getString("rut_empresa"));
        long datosPagoId = rs.getLong("datos_pago_id");
        if (!rs.wasNull()) {
            f.setDatos_Pago_ID(datosPagoId);
        }
        return f;
    };

    public List<FacturaEntidad> findAll() {
        return jdbc.query(SELECT_BASE, rowMapper);
    }

    public Optional<FacturaEntidad> findById(Long id) {
        List<FacturaEntidad> result = jdbc.query(
                SELECT_BASE + " WHERE f.factura_id = ?",
                rowMapper, id
        );
        return result.stream().findFirst();
    }

    public List<FacturaEntidad> findByUsuarioId(Long usuarioId) {
        return jdbc.query(
                SELECT_BASE + " WHERE f.usuario_usuario = ? ORDER BY f.fecha_emision DESC",
                rowMapper, usuarioId
        );
    }

    public Optional<FacturaEntidad> findByOrdenId(Long ordenId) {
        List<FacturaEntidad> result = jdbc.query(
                SELECT_BASE + " WHERE f.orden_orden_id = ?",
                rowMapper, ordenId
        );
        return result.stream().findFirst();
    }

    public Long crear(FacturaEntidad factura) {
        String sql = "INSERT INTO factura_entidad (usuario_usuario, datos_pago_id, orden_orden_id, precio_total, fecha_emision, total_neto, iva, costo_envio) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator psc = connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"factura_id"});
            ps.setLong(1, factura.getUsuarioId());
            if (factura.getDatos_Pago_ID() != null) {
                ps.setLong(2, factura.getDatos_Pago_ID());
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setLong(3, factura.getOrdenId());
            ps.setFloat(4, factura.getPrecio_Total());
            ps.setTimestamp(5, new java.sql.Timestamp(factura.getFecha_Emision().getTime()));
            ps.setFloat(6, factura.getTotal_Neto());
            ps.setFloat(7, factura.getIva());
            ps.setFloat(8, factura.getCosto_Envio() != null ? factura.getCosto_Envio() : 0f);
            return ps;
        };
        jdbc.update(psc, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }
}