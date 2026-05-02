package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.ProductoEntidad;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductoRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public ProductoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper reutilizable
    private final RowMapper<ProductoEntidad> rowMapper = (rs, rowNum) -> {
        ProductoEntidad p = new ProductoEntidad();
        p.setProducto_ID(rs.getLong("producto_id"));
        p.setCategoria_ID(rs.getLong("categoria_categoria_id"));
        p.setNombre_producto(rs.getString("nombre_producto"));
        p.setDescripcion(rs.getString("descripcion"));
        p.setPrecio(rs.getFloat("precio"));
        p.setStock(rs.getInt("stock"));
        p.setStock_reservado(rs.getInt("stock_reservado"));
        p.setSku(rs.getString("sku"));
        p.setActivo(rs.getBoolean("activo"));
        return p;
    };

    // Crear
    public int crear(ProductoEntidad p) {
        String sql = """
                INSERT INTO producto_entidad
            (categoria_categoria_id, nombre_producto, descripcion, precio, stock, sku, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(sql,
                p.getCategoria_ID(),
                p.getNombre_producto(),
                p.getDescripcion(),
                p.getPrecio(),
                p.getStock(),
            p.getSku(),
            p.isActivo());
    }

    // todos
    public List<ProductoEntidad> encontrarTodos() {
        String sql = "SELECT * FROM producto_entidad WHERE activo = true";
        return jdbcTemplate.query(sql, rowMapper);
    }

    // encontrar por ID
    public Optional<ProductoEntidad> encontrarPorId(Long id) {
        String sql = "SELECT * FROM producto_entidad WHERE producto_id = ? AND activo = true";
        List<ProductoEntidad> result = jdbcTemplate.query(sql, rowMapper, id);
        return result.stream().findFirst();
    }

    // buscar por nombre o descripción 
    public List<ProductoEntidad> encontrarPorNombreODescripcion(String termino) {
        String sql = """
                SELECT * FROM producto_entidad
                WHERE activo = true
                AND (nombre_producto ILIKE ?
                OR descripcion ILIKE ?)
                """;
        String patron = "%" + termino + "%";
        return jdbcTemplate.query(sql, rowMapper, patron, patron);
    }

    // actualizar
    public int actualizar(ProductoEntidad p) {
        String sql = """
                UPDATE producto_entidad SET
                    categoria_categoria_id = ?,
                    nombre_producto = ?,
                    descripcion = ?,
                    precio = ?,
                    stock = ?,
                    sku = ?,
                    activo = ?
                WHERE producto_id = ?
                """;
        return jdbcTemplate.update(sql,
                p.getCategoria_ID(),
                p.getNombre_producto(),
                p.getDescripcion(),
                p.getPrecio(),
                p.getStock(),
                p.getSku(),
                p.isActivo(),
                p.getProducto_ID());
    }

    // DELETE
    public int borrarPorId(Long id) {
        String sqlDeleteFromCart = "DELETE FROM carrito_producto_entidad WHERE producto_producto_id = ?";
        jdbcTemplate.update(sqlDeleteFromCart, id);
        String sql = "UPDATE producto_entidad SET activo = false WHERE producto_id = ? AND activo = true";
        return jdbcTemplate.update(sql, id);
    }

    // buscar stock 
    public int encontrarStockPorId(Long productoId) {
        String sql = "SELECT stock FROM producto_entidad WHERE producto_id = ? AND activo = true";
        Integer stock = jdbcTemplate.queryForObject(sql, Integer.class, productoId);
        return stock != null ? stock : 0;
    }

    // Buscar por SKU 
    public Optional<ProductoEntidad> encontrarPorSku(String sku) {
        String sql = "SELECT * FROM producto_entidad WHERE sku = ? AND activo = true";
        List<ProductoEntidad> result = jdbcTemplate.query(sql, rowMapper, sku);
        return result.stream().findFirst();
    }

    public Optional<ProductoEntidad> encontrarPorSkuCualquierEstado(String sku) {
        String sql = "SELECT * FROM producto_entidad WHERE sku = ?";
        List<ProductoEntidad> result = jdbcTemplate.query(sql, rowMapper, sku);
        return result.stream().findFirst();
    }

    // Buscar por categoría 
    public List<ProductoEntidad> encontrarPorCategoria(Long categoriaId) {
        String sql = "SELECT * FROM producto_entidad WHERE categoria_categoria_id = ? AND activo = true";
        return jdbcTemplate.query(sql, rowMapper, categoriaId);
    }

    public void aplicarDescuentoPorCategoria(Long categoriaId, Float porcentaje) {
        String sql = "CALL aplicar_descuento_categoria(?, ?)";
        jdbcTemplate.update(sql, categoriaId, porcentaje);
    }



}