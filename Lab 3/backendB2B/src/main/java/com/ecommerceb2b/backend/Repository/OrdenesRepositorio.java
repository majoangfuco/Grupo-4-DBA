package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.OrdenesEntidad;
import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Entities.ProductoEntidad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class OrdenesRepositorio {
    private final JdbcTemplate jdbcTemplate;

    public OrdenesRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper para OrdenesEntidad
    private final RowMapper<OrdenesEntidad> rowMapper = (rs, rowNum) -> {
        OrdenesEntidad o = new OrdenesEntidad();
        o.setOrden_ID(rs.getLong("orden_id"));
        o.setCarrito_ID(rs.getLong("carrito_carrito_id"));
        long usuarioId = rs.getLong("usuario_id");
        o.setUsuario_ID(rs.wasNull() ? null : usuarioId);
        o.setRut_Empresa(getStringOrNull(rs, "rut_empresa"));
        o.setInfo_Entrega_ID(rs.getLong("informacion_info_entrega_id"));
        java.sql.Timestamp ts = rs.getTimestamp("fecha_orden");
        o.setFecha_Orden(ts != null ? new java.util.Date(ts.getTime()) : null);
        o.setEstado(rs.getString("estado"));
        long almacenId = rs.getLong("almacen_asignado_id");
        o.setAlmacen_Asignado_ID(rs.wasNull() ? null : almacenId);
        o.setAlmacen_Nombre(getStringOrNull(rs, "almacen_nombre"));
        double distanciaKm = rs.getDouble("distancia_envio_km");
        o.setDistancia_envio_km(rs.wasNull() ? null : distanciaKm);

        return o;
    };

    private String getStringOrNull(java.sql.ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    // Si el trigger validar_cobertura_entrega rechaza
    // el INSERT (dirección fuera de cobertura, o sin coordenadas), Postgres
    // lanza una excepción que JDBC envuelve en una DataAccessException. Acá
    // la traducimos a IllegalStateException con el mensaje original y limpio,
    // para que el Controller la capture y devuelva un 400 en vez de un 500.
    public Long crear(OrdenesEntidad o) {
        String sql = """
                INSERT INTO ordenes_entidad
                (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado, almacen_asignado_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING orden_id
                """;
        try {
            Long ordenId = jdbcTemplate.queryForObject(sql, Long.class,
                    o.getCarrito_ID(),
                    o.getInfo_Entrega_ID(),
                    new java.sql.Timestamp(o.getFecha_Orden().getTime()),
                    o.getEstado(),
                    o.getAlmacen_Asignado_ID());

            if (ordenId == null) {
                throw new IllegalStateException("No se pudo crear la orden");
            }
            return ordenId;
        } catch (org.springframework.dao.DataAccessException e) {
            throw new IllegalStateException(extraerMensajeAmigable(e));
        }
    }

    // Extrae el mensaje de la excepción de PostgreSQL (RAISE EXCEPTION del
    // trigger), sin el prefijo técnico "ERROR:" ni detalles internos del driver.
    private String extraerMensajeAmigable(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage();
        if (mensaje == null) {
            return "No se pudo crear la orden";
        }
        int idx = mensaje.indexOf("ERROR:");
        if (idx >= 0) {
            mensaje = mensaje.substring(idx + "ERROR:".length()).split("\n")[0].trim();
        }
        return mensaje;
    }

    // todos
    public List<OrdenesEntidad> encontrarTodos() {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    // encontrar por ID
    public Optional<OrdenesEntidad> encontrarPorId(Long id) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                WHERE o.orden_id = ?
                """;
        List<OrdenesEntidad> result = jdbcTemplate.query(sql, rowMapper, id);
        return result.stream().findFirst();
    }

    // actualizar
    public int actualizar(OrdenesEntidad o) {
        String sql = """
                UPDATE ordenes_entidad SET
                carrito_carrito_id = ?, informacion_info_entrega_id = ?, fecha_orden = ?, estado = ?,
                almacen_asignado_id = ?
                WHERE orden_id = ?
                """;
        return jdbcTemplate.update(sql,
                o.getCarrito_ID(),
                o.getInfo_Entrega_ID(),
                new java.sql.Timestamp(o.getFecha_Orden().getTime()),
                o.getEstado(),
                o.getAlmacen_Asignado_ID(),
                o.getOrden_ID());
    }

    // eliminar
    public int borrarPorId(Long id) {
        String sql = "DELETE FROM ordenes_entidad WHERE orden_id = ?";
        return jdbcTemplate.update(sql, id);
    }

    // buscar por estado
    public List<OrdenesEntidad> encontrarPorEstado(String estado) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                WHERE o.estado = ?
                """;
        return jdbcTemplate.query(sql, rowMapper, estado);
    }

    // buscar por usuario
    // Como la tabla ordenes_entidad no tiene usuario_id, necesitamos hacer un JOIN con carrito_entidad
    public List<OrdenesEntidad> encontrarPorUsuarioId(Long usuarioId){
        String sql = """
               SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                      alm.nombre AS almacen_nombre,
                      ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
               FROM ordenes_entidad o
               JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
               JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
               LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
               LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
               WHERE c.carrito_usuario_id = ?
               """;
        return jdbcTemplate.query(sql, rowMapper, usuarioId);

    }

    // buscar por fecha 
    public List<OrdenesEntidad> encontrarPorFechaOrden(java.util.Date fecha) {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                WHERE o.fecha_orden > ?
                """;
        return jdbcTemplate.query(sql, rowMapper, fecha);
    }

    public List<OrdenesEntidad> encontrarTodosConRut() {
        String sql = """
                SELECT o.*, c.carrito_usuario_id AS usuario_id, u.rut_empresa,
                       alm.nombre AS almacen_nombre,
                       ST_Distance(alm.ubicacion::geography, ie.ubicacion::geography) / 1000.0 AS distancia_envio_km
                FROM ordenes_entidad o
                JOIN carrito_entidad c ON o.carrito_carrito_id = c.carrito_id
                JOIN usuario_entidad u ON c.carrito_usuario_id = u.usuario_id
                LEFT JOIN almacen_entidad alm ON o.almacen_asignado_id = alm.almacen_id
                LEFT JOIN informacion_entrega_entidad ie ON o.informacion_info_entrega_id = ie.info_entrega_id
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    public int actualizarEstado(Long ordenId, String estado) {
        String sql = """
            UPDATE ordenes_entidad
            SET estado = ?
            WHERE orden_id = ?
            """;

        return jdbcTemplate.update(
                sql,
                estado,
                ordenId
        );
    }


    /**
     * Ejecuta el checkout geoespacial completo en PostgreSQL.
     * El procedimiento valida cobertura y restricciones, selecciona mediante
     * ST_Distance el almacén más cercano con stock, crea orden/factura y
     * descuenta el inventario dentro de la misma transacción.
     */
    public Long procesarCheckout(
            Long carritoId,
            Long infoEntregaId,
            Long datosPagoId
    ) {
        try {
            jdbcTemplate.update(
                    "CALL procesar_checkout(?, ?, ?)",
                    carritoId,
                    infoEntregaId,
                    datosPagoId
            );

            Long ordenId = jdbcTemplate.queryForObject(
                    """
                    SELECT o.orden_id
                    FROM ordenes_entidad o
                    JOIN factura_entidad f
                      ON f.orden_orden_id = o.orden_id
                    WHERE o.carrito_carrito_id = ?
                      AND o.informacion_info_entrega_id = ?
                      AND f.datos_pago_id = ?
                    ORDER BY o.orden_id DESC
                    LIMIT 1
                    """,
                    Long.class,
                    carritoId,
                    infoEntregaId,
                    datosPagoId
            );

            if (ordenId == null) {
                throw new IllegalStateException(
                        "El procedimiento terminó sin generar una orden"
                );
            }

            return ordenId;

        } catch (org.springframework.dao.DataAccessException e) {
            throw new IllegalStateException(extraerMensajeAmigable(e));
        }
    }

    /**
     * Proyección técnica del carrito Mongo requerida por el procedimiento
     * geoespacial heredado. MongoDB sigue siendo la fuente operativa; estas
     * filas solo permiten reutilizar, dentro del checkout, las validaciones y
     * bloqueos de stock ya implementados en PostgreSQL.
     */
    public void proyectarCarritoParaCheckout(CarritoEntidad carrito,
                                             List<CarritoProductoEntidad> items) {
        jdbcTemplate.update("""
                INSERT INTO carrito_entidad
                    (carrito_id, carrito_usuario_id, estado, costo_carrito, ultima_actualizacion)
                VALUES (?, ?, 'ACTIVO', ?, NOW())
                ON CONFLICT (carrito_id) DO UPDATE SET
                    carrito_usuario_id = EXCLUDED.carrito_usuario_id,
                    estado = 'ACTIVO',
                    costo_carrito = EXCLUDED.costo_carrito,
                    ultima_actualizacion = NOW()
                """, carrito.getCarrito_ID(), carrito.getUsuario().getUsuario_ID(),
                carrito.getCosto_Carrito());

        jdbcTemplate.update("DELETE FROM carrito_producto_entidad WHERE carrito_carrito_id = ?",
                carrito.getCarrito_ID());
        for (CarritoProductoEntidad item : items) {
            jdbcTemplate.update("""
                    INSERT INTO carrito_producto_entidad
                        (carrito_producto_id, carrito_carrito_id, producto_producto_id, unidad_producto)
                    VALUES (?, ?, ?, ?)
                    """, item.getCarrito_Producto_ID(), carrito.getCarrito_ID(),
                    item.getProducto().getProducto_ID(), item.getUnidad_producto());
        }
    }

    /**
     * Snapshot de cliente que exige el $jsonSchema de `ordenes`
     * (cliente.razonSocial / rutEmpresa / direccionEnvio), armado desde
     * Postgres para poder espejar en Mongo una orden del checkout relacional.
     *
     * <p>A diferencia del checkout documental —donde el frontend manda estos
     * datos en el body— acá no viajan en la request, así que se leen de
     * usuario_entidad y de la dirección de entrega elegida. Los tres campos
     * son `minLength: 1` en el validador: se completan con un placeholder si
     * la fila de Postgres los tiene en NULL, para no perder la venta entera
     * por un dato de contacto incompleto.</p>
     */
    public org.bson.Document obtenerSnapshotCliente(Long usuarioId, Long infoEntregaId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(NULLIF(TRIM(u.nombre_usuario), ''), 'Cliente ' || u.usuario_id) AS razon_social,
                       COALESCE(NULLIF(TRIM(u.rut_empresa), ''), 'SIN RUT')                     AS rut_empresa,
                       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', ie.direccion, ie.numero)), ''),
                                'Sin dirección registrada')                                     AS direccion_envio
                FROM usuario_entidad u
                LEFT JOIN informacion_entrega_entidad ie ON ie.info_entrega_id = ?
                WHERE u.usuario_id = ?
                """, (rs, row) -> new org.bson.Document("razonSocial", rs.getString("razon_social"))
                        .append("rutEmpresa", rs.getString("rut_empresa"))
                        .append("direccionEnvio", rs.getString("direccion_envio")),
                infoEntregaId, usuarioId);
    }

    /** Lee la proyección SQL creada por procesar_checkout para copiarla a Mongo. */
    public FacturaEntidad obtenerFacturaProyectada(Long ordenId) {
        FacturaEntidad factura = jdbcTemplate.queryForObject("""
                SELECT factura_id, usuario_usuario, datos_pago_id, orden_orden_id,
                       precio_total, fecha_emision, total_neto, iva, costo_envio
                FROM factura_entidad WHERE orden_orden_id = ?
                """, (rs, row) -> {
            FacturaEntidad f = new FacturaEntidad();
            f.setUsuarioId(rs.getLong("usuario_usuario"));
            long pago = rs.getLong("datos_pago_id");
            f.setDatos_Pago_ID(rs.wasNull() ? null : pago);
            f.setOrdenId(rs.getLong("orden_orden_id"));
            f.setPrecio_Total(rs.getFloat("precio_total"));
            f.setFecha_Emision(rs.getTimestamp("fecha_emision"));
            f.setTotal_Neto(rs.getFloat("total_neto"));
            f.setIva(rs.getFloat("iva"));
            f.setCosto_Envio(rs.getFloat("costo_envio"));
            return f;
        }, ordenId);

        List<CarritoProductoEntidad> items = jdbcTemplate.query("""
                SELECT fi.producto_id, fi.cantidad, fi.precio_unitario,
                       p.nombre_producto, p.sku
                FROM factura_item_entidad fi
                JOIN factura_entidad f ON f.factura_id = fi.factura_id
                LEFT JOIN producto_entidad p ON p.producto_id = fi.producto_id
                WHERE f.orden_orden_id = ?
                """, (rs, row) -> {
            ProductoEntidad producto = new ProductoEntidad();
            producto.setProducto_ID(rs.getLong("producto_id"));
            producto.setNombre_producto(rs.getString("nombre_producto"));
            producto.setSku(rs.getString("sku"));
            producto.setPrecio(rs.getFloat("precio_unitario"));
            CarritoProductoEntidad item = new CarritoProductoEntidad();
            item.setProducto(producto);
            item.setUnidad_producto(rs.getLong("cantidad"));
            return item;
        }, ordenId);
        factura.setItems(items);
        return factura;
    }

}
