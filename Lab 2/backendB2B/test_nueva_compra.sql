-- ============================================================
-- test_nueva_compra.sql
-- Inserta una compra completa de prueba para probar el botón
-- de Refrescar en la página de Reportes.
--
-- Usa usuario_id = 5 (Luis Fernandez, sin carritos activos).
-- Productos: Servidor Rack 1U (id=11) x2  +  Disco NAS (id=12) x3
-- ============================================================

DO $$
DECLARE
    v_carrito_id     BIGINT;
    v_info_id        BIGINT;
    v_orden_id       INT;
    v_precio_total   NUMERIC;
BEGIN

    -- ── 1. Crear carrito PAGADO para usuario 5 ─────────────
    INSERT INTO carrito_entidad (carrito_usuario_id, estado, costo_carrito)
    VALUES (5, 'PAGADO', 0)
    RETURNING carrito_id INTO v_carrito_id;

    RAISE NOTICE '✅ Carrito creado: id=%', v_carrito_id;

    -- ── 2. Agregar productos al carrito ────────────────────
    --   Servidor Rack 1U (producto_id=11, precio=2,500,000) x2
    INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto)
    VALUES (v_carrito_id, 11, 2);

    --   Disco Duro NAS 8TB (producto_id=12, precio=280,000) x3
    INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto)
    VALUES (v_carrito_id, 12, 3);

    -- Calcular precio total real
    SELECT SUM(cp.unidad_producto * p.precio)
    INTO v_precio_total
    FROM carrito_producto_entidad cp
    JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
    WHERE cp.carrito_carrito_id = v_carrito_id;

    -- Actualizar costo del carrito
    UPDATE carrito_entidad SET costo_carrito = v_precio_total WHERE carrito_id = v_carrito_id;

    RAISE NOTICE '✅ Productos agregados. Total carrito: $%', v_precio_total;

    -- ── 3. Crear información de entrega (sin orden aún) ────
    INSERT INTO informacion_entrega_entidad
        (usuario_usuario, direccion, numero, rut_recibe_entrega, rut_empresa, estado_entrega, activa)
    VALUES
        (5, 'Av. Apoquindo', '3600', '12.345.678-9', '90.222.333-1', 'ENTREGADO', TRUE)
    RETURNING info_entrega_id INTO v_info_id;

    RAISE NOTICE '✅ Info entrega creada: id=%', v_info_id;

    -- ── 4. Crear la orden ──────────────────────────────────
    INSERT INTO ordenes_entidad (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado)
    VALUES (v_carrito_id, v_info_id, NOW(), 'ENTREGADO')
    RETURNING orden_id INTO v_orden_id;

    RAISE NOTICE '✅ Orden creada: id=%', v_orden_id;

    -- ── 5. Vincular orden a info_entrega ───────────────────
    UPDATE informacion_entrega_entidad
    SET orden_orden_id = v_orden_id
    WHERE info_entrega_id = v_info_id;

    -- ── 6. Crear factura con IVA 19% ───────────────────────
    INSERT INTO factura_entidad
        (usuario_usuario, orden_orden_id, precio_total, fecha_emision, total_neto, iva)
    VALUES (
        5,
        v_orden_id,
        v_precio_total,
        NOW(),
        ROUND(v_precio_total / 1.19, 0),
        ROUND(v_precio_total - (v_precio_total / 1.19), 0)
    );

    RAISE NOTICE '✅ Factura creada. Total: $%  Neto: $%  IVA: $%',
        v_precio_total,
        ROUND(v_precio_total / 1.19, 0),
        ROUND(v_precio_total - (v_precio_total / 1.19), 0);

    RAISE NOTICE '=== Compra insertada correctamente ===';
    RAISE NOTICE 'Ahora presiona "Refrescar" en el dashboard de Reportes.';

END $$;

-- ── 7. Verificar que el dato está listo para el REFRESH ───
SELECT
    o.orden_id,
    o.estado,
    o.fecha_orden,
    c.nombre_categoria,
    cp.unidad_producto,
    p.nombre_producto
FROM ordenes_entidad o
JOIN carrito_entidad          cart ON o.carrito_carrito_id    = cart.carrito_id
JOIN carrito_producto_entidad cp   ON cart.carrito_id         = cp.carrito_carrito_id
JOIN producto_entidad         p    ON cp.producto_producto_id = p.producto_id
JOIN categoria_entidad        c    ON p.categoria_categoria_id = c.categoria_id
WHERE o.estado = 'ENTREGADO'
ORDER BY o.orden_id DESC
LIMIT 10;
