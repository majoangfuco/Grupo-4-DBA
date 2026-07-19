package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.StockAlmacenProductoDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StockAlmacenRepositorio {

    private final JdbcTemplate jdbc;

    public StockAlmacenRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<StockAlmacenProductoDto> rowMapper = (rs, rowNum) -> {
        StockAlmacenProductoDto dto = new StockAlmacenProductoDto();
        dto.setAlmacenId(rs.getLong("almacen_id"));
        dto.setProductoId(rs.getLong("producto_id"));
        dto.setNombreProducto(rs.getString("nombre_producto"));
        dto.setStockDisponible(rs.getInt("stock_disponible"));
        return dto;
    };

    // Lista todos los productos con el stock que tienen en el almacén indicado.
    // Los productos sin registro en el almacén aparecen con stock 0.
    public List<StockAlmacenProductoDto> listarPorAlmacen(Long almacenId) {
        String sql = """
                SELECT ? AS almacen_id,
                       p.producto_id,
                       p.nombre_producto,
                       COALESCE(sap.stock_disponible, 0) AS stock_disponible
                FROM producto_entidad p
                LEFT JOIN stock_almacen_producto_entidad sap
                       ON sap.producto_id = p.producto_id
                      AND sap.almacen_id = ?
                WHERE p.activo = TRUE
                ORDER BY p.producto_id
                """;
        return jdbc.query(sql, rowMapper, almacenId, almacenId);
    }

    // Inserta o actualiza el stock de un producto en un almacén.
    public void upsertStock(Long almacenId, Long productoId, int stockDisponible) {
        String sql = """
                INSERT INTO stock_almacen_producto_entidad (almacen_id, producto_id, stock_disponible)
                VALUES (?, ?, ?)
                ON CONFLICT (almacen_id, producto_id)
                DO UPDATE SET stock_disponible = EXCLUDED.stock_disponible
                """;
        jdbc.update(sql, almacenId, productoId, stockDisponible);
    }

    // Recalcula el stock global del producto como la suma de todos los almacenes,
    // manteniendo la invariante stock_global = SUMA(stock por almacén).
    public void recalcularStockGlobal(Long productoId) {
        String sql = """
                UPDATE producto_entidad
                SET stock = (
                    SELECT COALESCE(SUM(stock_disponible), 0)
                    FROM stock_almacen_producto_entidad
                    WHERE producto_id = ?
                )
                WHERE producto_id = ?
                """;
        jdbc.update(sql, productoId, productoId);
    }

    // Stock reservado actual del producto (para no dejar el stock global por debajo).
    public int obtenerStockReservado(Long productoId) {
        Integer reservado = jdbc.queryForObject(
                "SELECT stock_reservado FROM producto_entidad WHERE producto_id = ?",
                Integer.class, productoId
        );
        return reservado != null ? reservado : 0;
    }

    // Suma del stock del producto en todos los almacenes distintos al indicado.
    public int sumarStockOtrosAlmacenes(Long almacenId, Long productoId) {
        Integer suma = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(stock_disponible), 0)
                FROM stock_almacen_producto_entidad
                WHERE producto_id = ? AND almacen_id <> ?
                """,
                Integer.class, productoId, almacenId
        );
        return suma != null ? suma : 0;
    }

    public boolean existeAlmacen(Long almacenId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM almacen_entidad WHERE almacen_id = ?",
                Integer.class, almacenId
        );
        return count != null && count > 0;
    }

    public boolean existeProducto(Long productoId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_entidad WHERE producto_id = ?",
                Integer.class, productoId
        );
        return count != null && count > 0;
    }
}
