package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FacturaRepositorio {

    private final JdbcTemplate jdbc;

    public FacturaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<FacturaEntidad> rowMapper = (rs, rowNum) -> {
        FacturaEntidad f = new FacturaEntidad();
        f.setFactura_ID(rs.getLong("factura_id"));
        f.setUsuarioId(rs.getLong("usuario_usuario"));
        f.setOrdenId(rs.getLong("orden_orden_id"));
        f.setPrecio_Total(rs.getFloat("precio_total"));
        f.setFecha_Emision(rs.getTimestamp("fecha_emision"));
        f.setTotal_Neto(rs.getFloat("total_neto"));
        f.setIva(rs.getFloat("iva"));
        return f;
    };

    public List<FacturaEntidad> findAll() {
        return jdbc.query("SELECT * FROM factura_entidad", rowMapper);
    }

    public Optional<FacturaEntidad> findById(Long id) {
        List<FacturaEntidad> result = jdbc.query(
                "SELECT * FROM factura_entidad WHERE factura_id = ?",
                rowMapper, id
        );
        return result.stream().findFirst();
    }

    public List<FacturaEntidad> findByUsuarioId(Long usuarioId) {
        return jdbc.query(
                "SELECT * FROM factura_entidad WHERE usuario_usuario = ? ORDER BY fecha_emision DESC",
                rowMapper, usuarioId
        );
    }

    public Optional<FacturaEntidad> findByOrdenId(Long ordenId) {
        List<FacturaEntidad> result = jdbc.query(
                "SELECT * FROM factura_entidad WHERE orden_orden_id = ?",
                rowMapper, ordenId
        );
        return result.stream().findFirst();
    }
}