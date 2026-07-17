package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.FacturaItemEntidad;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

@Repository
public class FacturaItemRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public FacturaItemRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<FacturaItemEntidad> rowMapper = (rs, rowNum) -> {
        FacturaItemEntidad item = new FacturaItemEntidad();
        item.setFactura_item_id(rs.getLong("factura_item_id"));
        item.setFactura_id(rs.getLong("factura_id"));
        item.setProducto_id(rs.getLong("producto_id"));
        item.setNombre_producto(rs.getString("nombre_producto"));
        item.setPrecio_unitario(rs.getFloat("precio_unitario"));
        item.setCantidad(rs.getInt("cantidad"));
        return item;
    };

    public void crearItem(Long facturaId, Long productoId, String nombreProducto, float precioUnitario, int cantidad) {
        String sql = """
            INSERT INTO factura_item_entidad
                (factura_id, producto_id, cantidad, precio_unitario)
            VALUES (?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, facturaId, productoId, cantidad, precioUnitario);
    }

    public List<FacturaItemEntidad> obtenerPorFacturaId(Long facturaId) {
        String sql = """
            SELECT fi.factura_item_id,
                   fi.factura_id,
                   fi.producto_id,
                   p.nombre_producto,
                   fi.precio_unitario,
                   fi.cantidad
            FROM factura_item_entidad fi
            JOIN producto_entidad p ON p.producto_id = fi.producto_id
            WHERE fi.factura_id = ?
            """;
        return jdbcTemplate.query(sql, rowMapper, facturaId);
    }

    public int eliminarPorFacturaId(Long facturaId) {
        String sql = "DELETE FROM factura_item_entidad WHERE factura_id = ?";
        return jdbcTemplate.update(sql, facturaId);
    }
}
