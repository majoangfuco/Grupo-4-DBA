package com.ecommerceb2b.backend.Repository;

import org.springframework.stereotype.Repository;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class CarritoProductoRepositorio {


    private final JdbcTemplate jdbcTemplate;

    public CarritoProductoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CarritoProductoEntidad> rowMapper = (rs, rowNum) -> {
        CarritoProductoEntidad cp = new CarritoProductoEntidad();
        cp.setCarrito_Producto_ID(rs.getLong("carrito_producto_id"));

        CarritoEntidad carrito = new CarritoEntidad();
        carrito.setCarrito_ID(rs.getLong("carrito_carrito_id"));
        cp.setCarrito(carrito);

        ProductoEntidad producto = new ProductoEntidad();
        producto.setProducto_ID(rs.getLong("producto_producto_id"));
        cp.setProducto(producto);

        cp.setUnidad_producto(rs.getLong("unidad_producto"));
        return cp;
    };

    private final RowMapper<CarritoProductoEntidad> rowMapperDetalle = (rs, rowNum) -> {
        CarritoProductoEntidad cp = new CarritoProductoEntidad();
        cp.setCarrito_Producto_ID(rs.getLong("carrito_producto_id"));

        CarritoEntidad carrito = new CarritoEntidad();
        carrito.setCarrito_ID(rs.getLong("carrito_carrito_id"));
        cp.setCarrito(carrito);

        ProductoEntidad producto = new ProductoEntidad();
        producto.setProducto_ID(rs.getLong("producto_id"));
        producto.setNombre_producto(rs.getString("nombre_producto"));
        producto.setDescripcion(rs.getString("descripcion"));
        producto.setPrecio(rs.getFloat("precio"));
        producto.setStock(rs.getInt("stock"));
        producto.setSku(rs.getString("sku"));
        producto.setActivo(rs.getBoolean("activo"));
        cp.setProducto(producto);

        cp.setUnidad_producto(rs.getLong("unidad_producto"));
        return cp;
    };

    public Optional<CarritoProductoEntidad> encontrarPorId(Long id) {
        String sql = """
            SELECT cp.carrito_producto_id,
                   cp.carrito_carrito_id,
                   cp.producto_producto_id,
                   cp.unidad_producto,
                   p.producto_id,
                   p.nombre_producto,
                   p.descripcion,
                   p.precio,
                   p.stock,
                   p.sku,
                   p.activo
            FROM carrito_producto_entidad cp
            JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
            WHERE cp.carrito_producto_id = ?
            """;
        List<CarritoProductoEntidad> result = jdbcTemplate.query(sql, rowMapperDetalle, id);
        return result.stream().findFirst();
    }

    public Optional<CarritoProductoEntidad> encontrarPorCarritoYProducto(Long carritoId, Long productoId) {
        String sql = """
            SELECT * FROM carrito_producto_entidad
            WHERE carrito_carrito_id = ? AND producto_producto_id = ?
            """;
        List<CarritoProductoEntidad> result = jdbcTemplate.query(sql, rowMapper, carritoId, productoId);
        return result.stream().findFirst();
    }

    public List<CarritoProductoEntidad> listarPorCarrito(Long carritoId) {
        String sql = """
            SELECT cp.carrito_producto_id,
                   cp.carrito_carrito_id,
                   cp.producto_producto_id,
                   cp.unidad_producto,
                   p.nombre_producto,
                   p.precio
            FROM carrito_producto_entidad cp
            JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
            WHERE cp.carrito_carrito_id = ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            CarritoProductoEntidad cp = new CarritoProductoEntidad();
            cp.setCarrito_Producto_ID(rs.getLong("carrito_producto_id"));

            CarritoEntidad carrito = new CarritoEntidad();
            carrito.setCarrito_ID(rs.getLong("carrito_carrito_id"));
            cp.setCarrito(carrito);

            ProductoEntidad producto = new ProductoEntidad();
            producto.setProducto_ID(rs.getLong("producto_producto_id"));
            producto.setNombre_producto(rs.getString("nombre_producto"));
            producto.setPrecio(rs.getFloat("precio"));
            cp.setProducto(producto);

            cp.setUnidad_producto(rs.getLong("unidad_producto"));
            return cp;
        }, carritoId);
    }

    public int crear(Long carritoId, Long productoId, Long cantidad) {
        String sql = """
            INSERT INTO carrito_producto_entidad
                (carrito_carrito_id, producto_producto_id, unidad_producto)
            VALUES (?, ?, ?)
            """;
        return jdbcTemplate.update(sql, carritoId, productoId, cantidad);
    }

    public int actualizarCantidad(Long carritoProductoId, Long cantidad) {
        String sql = """
            UPDATE carrito_producto_entidad
            SET unidad_producto = ?
            WHERE carrito_producto_id = ?
            """;
        return jdbcTemplate.update(sql, cantidad, carritoProductoId);
    }

    public int eliminarPorId(Long carritoProductoId) {
        String sql = "DELETE FROM carrito_producto_entidad WHERE carrito_producto_id = ?";
        return jdbcTemplate.update(sql, carritoProductoId);
    }

    public void reservarStock(Long productoId, int cantidad) {
        String sql = "CALL reservar_stock(?, ?)";
        jdbcTemplate.update(sql, productoId, cantidad);
    }

    public void liberarStock(Long productoId, int cantidad) {
        String sql = "CALL liberar_stock(?, ?)";
        jdbcTemplate.update(sql, productoId, cantidad);
    }

    public BigDecimal calcularSubtotal(Long carritoId) {
        String sql = """
            SELECT COALESCE(SUM(cp.unidad_producto * p.precio)::numeric, 0)
            FROM carrito_producto_entidad cp
            JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
            WHERE cp.carrito_carrito_id = ?
            """;
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, carritoId);
    }
   
    
}


