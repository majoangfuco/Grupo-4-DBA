package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.ReporteVentasEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class ReporteVentasRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public ReporteVentasRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper para mapear resultados de la vista materializada
    private final RowMapper<ReporteVentasEntidad> rowMapper = (rs, rowNum) -> {
        ReporteVentasEntidad reporte = new ReporteVentasEntidad();
        reporte.setMesAno(rs.getString("mes_ano"));
        reporte.setAnio(rs.getInt("anio"));
        reporte.setMes(rs.getInt("mes"));
        reporte.setNombreCategoria(rs.getString("nombre_categoria"));
        reporte.setCantidadOrdenes(rs.getInt("cantidad_ordenes"));
        reporte.setCantidadProductos(rs.getInt("cantidad_productos"));
        
        // Manejo de valores nulos para BigDecimal
        BigDecimal totalVendido = rs.getBigDecimal("total_vendido");
        reporte.setTotalVendido(totalVendido != null ? totalVendido : BigDecimal.ZERO);
        
        BigDecimal precioPromedio = rs.getBigDecimal("precio_promedio");
        reporte.setPrecioPromedio(precioPromedio != null ? precioPromedio : BigDecimal.ZERO);
        
        return reporte;
    };

    /**
     * Obtiene todos los registros de la vista materializada
     * ordenados por mes y año descendente, luego por categoría
     */
    public List<ReporteVentasEntidad> obtenerTodosLosReportes() {
        String sql = """
                SELECT mes_ano, anio, mes, nombre_categoria, cantidad_ordenes, 
                       cantidad_productos, total_vendido, precio_promedio
                FROM vw_ventas_mensuales_por_categoria
                ORDER BY anio DESC, mes DESC, nombre_categoria ASC
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * Obtiene reportes filtrados por mes y año específico
     * @param mesAno Formato: YYYY-MM
     */
    public List<ReporteVentasEntidad> obtenerPorMesAno(String mesAno) {
        String sql = """
                SELECT mes_ano, anio, mes, nombre_categoria, cantidad_ordenes,
                       cantidad_productos, total_vendido, precio_promedio
                FROM vw_ventas_mensuales_por_categoria
                WHERE mes_ano = ?
                ORDER BY nombre_categoria ASC
                """;
        return jdbcTemplate.query(sql, rowMapper, mesAno);
    }

    /**
     * Obtiene reportes filtrados por categoría
     */
    public List<ReporteVentasEntidad> obtenerPorCategoria(String nombreCategoria) {
        String sql = """
                SELECT mes_ano, anio, mes, nombre_categoria, cantidad_ordenes,
                       cantidad_productos, total_vendido, precio_promedio
                FROM vw_ventas_mensuales_por_categoria
                WHERE nombre_categoria = ?
                ORDER BY anio DESC, mes DESC
                """;
        return jdbcTemplate.query(sql, rowMapper, nombreCategoria);
    }

    /**
     * Obtiene reportes de un año específico
     */
    public List<ReporteVentasEntidad> obtenerPorAnio(Integer anio) {
        String sql = """
                SELECT mes_ano, anio, mes, nombre_categoria, cantidad_ordenes,
                       cantidad_productos, total_vendido, precio_promedio
                FROM vw_ventas_mensuales_por_categoria
                WHERE anio = ?
                ORDER BY mes DESC, nombre_categoria ASC
                """;
        return jdbcTemplate.query(sql, rowMapper, anio);
    }

    /**
     * Obtiene el total consolidado de ventas (sum de todos los meses y categorías)
     */
    public ReporteVentasEntidad obtenerTotalConsolidado() {
        String sql = """
                SELECT 
                    'TOTAL' AS mes_ano,
                    0 AS anio,
                    0 AS mes,
                    'TODAS LAS CATEGORÍAS' AS nombre_categoria,
                    SUM(cantidad_ordenes)::INT AS cantidad_ordenes,
                    SUM(cantidad_productos)::INT AS cantidad_productos,
                    SUM(total_vendido) AS total_vendido,
                    AVG(precio_promedio) AS precio_promedio
                FROM vw_ventas_mensuales_por_categoria
                """;
        List<ReporteVentasEntidad> result = jdbcTemplate.query(sql, rowMapper);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Refresca la vista materializada (útil después de cambios en datos)
     */
    public void refrescarVistaMatrializada() {
        String sql = "REFRESH MATERIALIZED VIEW CONCURRENTLY vw_ventas_mensuales_por_categoria";
        jdbcTemplate.execute(sql);
    }
}
