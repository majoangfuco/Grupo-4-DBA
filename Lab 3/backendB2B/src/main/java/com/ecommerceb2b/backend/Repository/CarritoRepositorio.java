package com.ecommerceb2b.backend.Repository;

import org.springframework.stereotype.Repository;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

@Repository
public class CarritoRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public CarritoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CarritoEntidad> rowMapper = (rs, rowNum) -> {
        CarritoEntidad carrito = new CarritoEntidad();
        carrito.setCarrito_ID(rs.getLong("carrito_id"));
        UsuarioEntidad usuario = new UsuarioEntidad();
        usuario.setUsuario_ID(rs.getLong("carrito_usuario_id"));
        carrito.setUsuario(usuario);
        carrito.setEstado(rs.getString("estado"));
        carrito.setUltima_Actualizacion(rs.getTimestamp("ultima_actualizacion"));
        carrito.setCosto_Carrito(rs.getLong("costo_carrito"));
        return carrito;
    };

    public List<CarritoEntidad> encontrarActivoOAbandonadoPorUsuario(Long idCliente) {
        String sql = """
            SELECT * FROM carrito_entidad
            WHERE carrito_usuario_id = ? AND estado IN ('ACTIVO', 'ABANDONADO')
            """;
        return jdbcTemplate.query(sql, rowMapper, idCliente);
    }

    public Optional<CarritoEntidad> encontrarPorId(Long carritoId) {
        String sql = "SELECT * FROM carrito_entidad WHERE carrito_id = ?";
        List<CarritoEntidad> result = jdbcTemplate.query(sql, rowMapper, carritoId);
        return result.stream().findFirst();
    }

    public List<CarritoEntidad> listarPorUsuario(Long idCliente) {
        String sql = "SELECT * FROM carrito_entidad WHERE carrito_usuario_id = ? ORDER BY carrito_id DESC";
        return jdbcTemplate.query(sql, rowMapper, idCliente);
    }

    public int reactivarCarrito(Long carritoId) {
        String sql = """
            UPDATE carrito_entidad
            SET estado = 'ACTIVO', ultima_actualizacion = NOW()
            WHERE carrito_id = ?
            """;
        return jdbcTemplate.update(sql, carritoId);
    }

    public CarritoEntidad crearCarrito(Long idCliente) {
        String sql = """
            INSERT INTO carrito_entidad
                (carrito_usuario_id, estado, costo_carrito, ultima_actualizacion)
            VALUES (?, 'ACTIVO', 0, NOW())
            RETURNING *
            """;
        return jdbcTemplate.queryForObject(sql, rowMapper, idCliente);
    }

    public int actualizarEstado(Long carritoId, String estado) {
        String sql = """
            UPDATE carrito_entidad
            SET estado = ?, ultima_actualizacion = NOW()
            WHERE carrito_id = ?
            """;
        return jdbcTemplate.update(sql, estado, carritoId);
    }
    
    public CarritoEntidad obtenerOCrearCarrito(Long idCliente) {
        List<CarritoEntidad> existentes = encontrarActivoOAbandonadoPorUsuario(idCliente);
        if (!existentes.isEmpty()) {
            CarritoEntidad carrito = existentes.get(0);
            if ("ABANDONADO".equalsIgnoreCase(carrito.getEstado())) {
                reactivarCarrito(carrito.getCarrito_ID());
                carrito.setEstado("ACTIVO");
            }
            return carrito;
        }

        return crearCarrito(idCliente);
    }       
}