-- ───  EXTENSIÓN POSTGIS ──────────────────────────
CREATE EXTENSION IF NOT EXISTS postgis;

-- ─── 1. LIMPIEZA DEL ESQUEMA ANTERIOR ─────────────────────
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_geo_por_distrito CASCADE;
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_por_comuna CASCADE;
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_mensuales_por_categoria CASCADE;

DROP TABLE IF EXISTS stock_almacen_producto_entidad CASCADE;
DROP TABLE IF EXISTS zona_residencial_protegida_entidad CASCADE;
DROP TABLE IF EXISTS distrito_postal_entidad CASCADE;
DROP TABLE IF EXISTS cobertura_empresa_entidad CASCADE;
DROP TABLE IF EXISTS almacen_entidad CASCADE;
DROP TABLE IF EXISTS zona_cobertura_entidad CASCADE;
DROP TABLE IF EXISTS audit_ordenes CASCADE;
DROP TABLE IF EXISTS factura_item_entidad CASCADE;
DROP TABLE IF EXISTS factura_entidad CASCADE;
DROP TABLE IF EXISTS carrito_producto_entidad CASCADE;
DROP TABLE IF EXISTS informacion_entrega_entidad CASCADE;
DROP TABLE IF EXISTS ordenes_entidad CASCADE;
DROP TABLE IF EXISTS producto_entidad CASCADE;
DROP TABLE IF EXISTS carrito_entidad CASCADE;
DROP TABLE IF EXISTS categoria_entidad CASCADE;
DROP TABLE IF EXISTS datos_pago_entidad CASCADE;
DROP TABLE IF EXISTS usuario_entidad CASCADE;

-- ─── 2. TABLAS ─────────────────────────────────────────────

CREATE TABLE usuario_entidad (
    usuario_id    BIGSERIAL PRIMARY KEY,
    nombre_usuario VARCHAR(255),
    correo        VARCHAR(255),
    contrasena    VARCHAR(255),
    ultima_compra TIMESTAMP,
    rut_empresa   VARCHAR(255),
    rol           VARCHAR(255)
);

CREATE TABLE datos_pago_entidad (
    datos_pago_id   BIGSERIAL PRIMARY KEY,
    usuario_usuario BIGINT REFERENCES usuario_entidad(usuario_id),
    metodo_pago     VARCHAR(255),
    numero_tarjeta  VARCHAR(255),
    fecha_expiracion VARCHAR(255)
);

CREATE TABLE categoria_entidad (
    categoria_id      SERIAL PRIMARY KEY,
    nombre_categoria  VARCHAR(255),
    estado_categoria  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE producto_entidad (
    producto_id           BIGSERIAL PRIMARY KEY,
    categoria_categoria_id INT REFERENCES categoria_entidad(categoria_id),
    nombre_producto       VARCHAR(255),
    descripcion           TEXT,
    precio                REAL,
    stock                 INT NOT NULL DEFAULT 0,
    stock_reservado       INT NOT NULL DEFAULT 0,
    sku                   VARCHAR(255),
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_stock_reservado CHECK (stock_reservado >= 0 AND stock_reservado <= stock)
);

CREATE TABLE carrito_entidad (
    carrito_id           BIGSERIAL PRIMARY KEY,
    carrito_usuario_id   BIGINT REFERENCES usuario_entidad(usuario_id),
    estado               VARCHAR(255),
    costo_carrito        BIGINT,
    ultima_actualizacion TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Solo un carrito ACTIVO o ABANDONADO por usuario
CREATE UNIQUE INDEX ux_carrito_activo_abandonado
    ON carrito_entidad (carrito_usuario_id)
    WHERE estado IN ('ACTIVO', 'ABANDONADO');

CREATE TABLE carrito_producto_entidad (
    carrito_producto_id  BIGSERIAL PRIMARY KEY,
    carrito_carrito_id   BIGINT REFERENCES carrito_entidad(carrito_id),
    producto_producto_id BIGINT REFERENCES producto_entidad(producto_id),
    unidad_producto      BIGINT
);

CREATE TABLE informacion_entrega_entidad (
    info_entrega_id       BIGSERIAL PRIMARY KEY,
    usuario_usuario       BIGINT REFERENCES usuario_entidad(usuario_id),
    orden_orden_id        INT,
    direccion             VARCHAR(255),
    numero                VARCHAR(255),
    rut_recibe_entrega    VARCHAR(255),
    rut_empresa           VARCHAR(255),
    estado_entrega        VARCHAR(255),
    activa                BOOLEAN NOT NULL DEFAULT TRUE
);

-- ─── 2.1 EXTENSIÓN GEOESPACIAL ───────────

-- Ubicación geográfica de la dirección de entrega del cliente
ALTER TABLE informacion_entrega_entidad
    ADD COLUMN ubicacion GEOMETRY(Point, 4326);

-- Comuna de la dirección de entrega (para agrupar ventas por zona geográfica)
ALTER TABLE informacion_entrega_entidad
    ADD COLUMN comuna VARCHAR(100);

-- Almacenes (bodegas) de la empresa, cada uno con su ubicación
CREATE TABLE almacen_entidad (
    almacen_id  SERIAL PRIMARY KEY,
    nombre      VARCHAR(255) NOT NULL,
    direccion   VARCHAR(255),
    ubicacion   GEOMETRY(Point, 4326) NOT NULL
);

-- Zonas de cobertura de servicio (polígono de área que la empresa cubre)
CREATE TABLE zona_cobertura_entidad (
    zona_id   SERIAL PRIMARY KEY,
    nombre    VARCHAR(255) NOT NULL,
    geom      GEOMETRY(Polygon, 4326) NOT NULL,
    activa    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE ordenes_entidad (
    orden_id                   SERIAL PRIMARY KEY,
    carrito_carrito_id         BIGINT REFERENCES carrito_entidad(carrito_id),
    informacion_info_entrega_id BIGINT REFERENCES informacion_entrega_entidad(info_entrega_id),
    fecha_orden                TIMESTAMP,
    estado                     VARCHAR(255),
    -- Almacén asignado por logística de última milla
    almacen_asignado_id        BIGINT REFERENCES almacen_entidad(almacen_id)
);

ALTER TABLE informacion_entrega_entidad
    ADD CONSTRAINT fk_info_entrega_orden
    FOREIGN KEY (orden_orden_id) REFERENCES ordenes_entidad(orden_id);

CREATE TABLE factura_entidad (
    factura_id      BIGSERIAL PRIMARY KEY,
    usuario_usuario BIGINT REFERENCES usuario_entidad(usuario_id),
    datos_pago_id   BIGINT REFERENCES datos_pago_entidad(datos_pago_id),
    orden_orden_id  INT REFERENCES ordenes_entidad(orden_id),
    precio_total    REAL,
    fecha_emision   TIMESTAMP,
    total_neto      REAL,
    iva             REAL
);

CREATE TABLE factura_item_entidad (
    factura_item_id   BIGSERIAL PRIMARY KEY,
    factura_id        BIGINT NOT NULL REFERENCES factura_entidad(factura_id) ON DELETE CASCADE,
    producto_id       BIGINT NOT NULL REFERENCES producto_entidad(producto_id),
    cantidad          INT NOT NULL,
    precio_unitario   REAL NOT NULL,
    CONSTRAINT ck_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_precio CHECK (precio_unitario > 0)
);

-- ─── 3. PROCEDIMIENTOS ALMACENADOS ────────────────────────

-- Procedimiento: Reservar stock de un producto
CREATE OR REPLACE PROCEDURE reservar_stock(p_producto_id BIGINT, p_cantidad INT)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_cantidad <= 0 THEN
        RAISE EXCEPTION 'cantidad debe ser mayor a 0';
    END IF;

    UPDATE producto_entidad
    SET stock_reservado = stock_reservado + p_cantidad
    WHERE producto_id = p_producto_id
      AND activo = true
      AND stock_reservado + p_cantidad <= stock;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Stock insuficiente o producto inactivo';
    END IF;
END;
$$;

-- Procedimiento: Liberar stock reservado
CREATE OR REPLACE PROCEDURE liberar_stock(p_producto_id BIGINT, p_cantidad INT)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_cantidad <= 0 THEN
        RAISE EXCEPTION 'cantidad debe ser mayor a 0';
    END IF;

    UPDATE producto_entidad
    SET stock_reservado = stock_reservado - p_cantidad
    WHERE producto_id = p_producto_id
      AND stock_reservado - p_cantidad >= 0;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Stock reservado insuficiente';
    END IF;
END;
$$;

-- Función auxiliar: Ajustar reserva por estado de carrito
CREATE OR REPLACE FUNCTION ajustar_reserva_por_carrito(p_carrito_id BIGINT, p_accion TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_accion = 'RESERVAR' THEN
        IF EXISTS (
            SELECT 1
            FROM carrito_producto_entidad cp
            JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
            WHERE cp.carrito_carrito_id = p_carrito_id
              AND p.stock_reservado + cp.unidad_producto > p.stock
        ) THEN
            RAISE EXCEPTION 'Stock insuficiente para reactivar carrito %', p_carrito_id;
        END IF;

        UPDATE producto_entidad p
        SET stock_reservado = p.stock_reservado + cp.unidad_producto
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
          AND p.producto_id = cp.producto_producto_id;

    ELSIF p_accion = 'LIBERAR' THEN
        UPDATE producto_entidad p
        SET stock_reservado = p.stock_reservado - cp.unidad_producto
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
          AND p.producto_id = cp.producto_producto_id;

    ELSIF p_accion = 'CONSUMIR' THEN
        IF EXISTS (
            SELECT 1
            FROM carrito_producto_entidad cp
            JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
            WHERE cp.carrito_carrito_id = p_carrito_id
              AND p.stock_reservado - cp.unidad_producto < 0
        ) THEN
            RAISE EXCEPTION 'Stock reservado insuficiente en carrito %', p_carrito_id;
        END IF;

        UPDATE producto_entidad p
        SET stock         = p.stock - cp.unidad_producto,
            stock_reservado = p.stock_reservado - cp.unidad_producto
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
          AND p.producto_id = cp.producto_producto_id;
    END IF;
END;
$$;

-- Procedimiento: Aplicar descuento masivo a una categoría
CREATE OR REPLACE PROCEDURE aplicar_descuento_categoria(
    p_categoria_id INT,
    p_porcentaje   REAL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_productos_afectados INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM categoria_entidad WHERE categoria_id = p_categoria_id) THEN
        RAISE EXCEPTION 'La categoría % no existe', p_categoria_id;
    END IF;

    IF p_porcentaje < 0 OR p_porcentaje > 100 THEN
        RAISE EXCEPTION 'El porcentaje debe estar entre 0 y 100, se recibió: %', p_porcentaje;
    END IF;

    UPDATE producto_entidad
    SET precio = precio * (1 - (p_porcentaje / 100.0))
    WHERE categoria_categoria_id = p_categoria_id
      AND activo = true;

    GET DIAGNOSTICS v_productos_afectados = ROW_COUNT;

    RAISE NOTICE 'Descuento del % aplicado a % productos de la categoría %',
        p_porcentaje, v_productos_afectados, p_categoria_id;
END;
$$;

-- Procedimiento: Procesar checkout (Req. 3)
-- Transacción atómica que valida carrito, reserva stock, crea orden y factura
CREATE OR REPLACE PROCEDURE procesar_checkout(
    p_carrito_id BIGINT,
    p_info_entrega_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_usuario_id        BIGINT;
    v_total_neto        REAL;
    v_iva               REAL;
    v_precio_total      REAL;
    v_orden_id          INT;
    v_factura_id        BIGINT;
    v_datos_pago_id     BIGINT;
    v_producto_id       BIGINT;
    v_cantidad          INT;
    v_precio            REAL;
    v_stock_disponible  INT;
    v_ubicacion_entrega GEOMETRY;
    v_almacen_id        BIGINT;
    v_almacen_nombre    VARCHAR;
    v_distancia_km      REAL;
BEGIN
    -- 1. Validar que el carrito existe y está ACTIVO
    SELECT carrito_usuario_id INTO v_usuario_id
    FROM carrito_entidad
    WHERE carrito_id = p_carrito_id AND estado = 'ACTIVO';

    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'Carrito % no existe o no está activo', p_carrito_id;
    END IF;

    -- 2. Verificar que el usuario existe
    IF NOT EXISTS (SELECT 1 FROM usuario_entidad WHERE usuario_id = v_usuario_id) THEN
        RAISE EXCEPTION 'Usuario % no existe', v_usuario_id;
    END IF;

    -- 3. Validar que el carrito tiene productos
    IF NOT EXISTS (SELECT 1 FROM carrito_producto_entidad WHERE carrito_carrito_id = p_carrito_id) THEN
        RAISE EXCEPTION 'Carrito % vacío', p_carrito_id;
    END IF;

    -- 4. Verificar stock suficiente para todos los productos
    FOR v_producto_id, v_cantidad IN
        SELECT producto_producto_id, unidad_producto
        FROM carrito_producto_entidad
        WHERE carrito_carrito_id = p_carrito_id
    LOOP
        SELECT (stock - stock_reservado) INTO v_stock_disponible
        FROM producto_entidad
        WHERE producto_id = v_producto_id;

        IF v_stock_disponible < v_cantidad THEN
            RAISE EXCEPTION 'Stock insuficiente para producto %. Disponible: %, Solicitado: %',
                v_producto_id, v_stock_disponible, v_cantidad;
        END IF;
    END LOOP;

    -- 4.1 Obtener ubicación de la dirección de entrega
    SELECT ubicacion INTO v_ubicacion_entrega
    FROM informacion_entrega_entidad
    WHERE info_entrega_id = p_info_entrega_id;

    IF v_ubicacion_entrega IS NULL THEN
        RAISE EXCEPTION 'La dirección de entrega % no tiene coordenadas geográficas registradas',
            p_info_entrega_id;
    END IF;

    -- 4.2 Logística de última milla: encontrar el almacén más cercano
    -- (por ahora sin filtrar por stock propio del almacén, ya que el
    -- stock aún es global — ver nota en README sobre trabajo futuro)
    SELECT almacen_id, nombre,
           ST_Distance(v_ubicacion_entrega::geography, ubicacion::geography) / 1000.0
    INTO v_almacen_id, v_almacen_nombre, v_distancia_km
    FROM almacen_entidad
    ORDER BY v_ubicacion_entrega::geography <-> ubicacion::geography
    LIMIT 1;

    IF v_almacen_id IS NULL THEN
        RAISE EXCEPTION 'No hay almacenes registrados para asignar la orden';
    END IF;

    RAISE NOTICE 'Almacén asignado: % (id %), distancia aprox. % km',
        v_almacen_nombre, v_almacen_id, round(v_distancia_km::numeric, 2);

    -- 5. Calcular total del carrito (sin IVA)
    SELECT COALESCE(SUM(cp.unidad_producto * p.precio), 0)
    INTO v_total_neto
    FROM carrito_producto_entidad cp
    JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
    WHERE cp.carrito_carrito_id = p_carrito_id;

    -- 6. Descontar stock y stock_reservado
    UPDATE producto_entidad p
    SET stock         = stock - cp.unidad_producto,
        stock_reservado = stock_reservado - cp.unidad_producto
    FROM carrito_producto_entidad cp
    WHERE cp.carrito_carrito_id = p_carrito_id
      AND p.producto_id = cp.producto_producto_id;

    -- 7. Crear orden en estado PAGADO, con el almacén asignado
    INSERT INTO ordenes_entidad (
        carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado, almacen_asignado_id
    )
    VALUES (p_carrito_id, p_info_entrega_id, NOW(), 'PAGADO', v_almacen_id)
    RETURNING orden_id INTO v_orden_id;

    -- 8. Calcular IVA (19%)
    v_iva := v_total_neto * 0.19;
    v_precio_total := v_total_neto + v_iva;

    -- 9. Obtener datos de pago del usuario (primer registro disponible)
    SELECT datos_pago_id INTO v_datos_pago_id
    FROM datos_pago_entidad
    WHERE usuario_usuario = v_usuario_id
    LIMIT 1;

    -- 10. Crear factura
    INSERT INTO factura_entidad (
        usuario_usuario,
        datos_pago_id,
        orden_orden_id,
        precio_total,
        fecha_emision,
        total_neto,
        iva
    )
    VALUES (
        v_usuario_id,
        v_datos_pago_id,
        v_orden_id,
        v_precio_total,
        NOW(),
        v_total_neto,
        v_iva
    )
    RETURNING factura_id INTO v_factura_id;

    -- 11. Actualizar estado del carrito a PAGADO
    UPDATE carrito_entidad
    SET estado = 'PAGADO'
    WHERE carrito_id = p_carrito_id;

    -- 12. Actualizar última_compra del usuario
    UPDATE usuario_entidad
    SET ultima_compra = NOW()
    WHERE usuario_id = v_usuario_id;

    RAISE NOTICE 'Checkout exitoso: Orden % Factura % Total: % Almacén: %',
        v_orden_id, v_factura_id, v_precio_total, v_almacen_nombre;
END;
$$;

-- ─── 4. TRIGGERS ──────────────────────────────────────────

-- Trigger function: Gestionar stock al cambiar estado del carrito
CREATE OR REPLACE FUNCTION trg_carrito_estado_cambio()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.estado = OLD.estado THEN
        RETURN NEW;
    END IF;

    IF NEW.estado = 'ABANDONADO' THEN
        PERFORM ajustar_reserva_por_carrito(NEW.carrito_id, 'LIBERAR');
    ELSIF NEW.estado IN ('PAGADO', 'ORDENADO') THEN
        PERFORM ajustar_reserva_por_carrito(NEW.carrito_id, 'CONSUMIR');
    ELSIF NEW.estado = 'ACTIVO' AND OLD.estado = 'ABANDONADO' THEN
        PERFORM ajustar_reserva_por_carrito(NEW.carrito_id, 'RESERVAR');
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS carrito_estado_cambio ON carrito_entidad;
CREATE TRIGGER carrito_estado_cambio
    AFTER UPDATE OF estado ON carrito_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_carrito_estado_cambio();

-- Tabla de auditoría para rastrear cambios en órdenes
DROP TABLE IF EXISTS audit_ordenes;
CREATE TABLE audit_ordenes (
    audit_id        BIGSERIAL PRIMARY KEY,
    orden_id        INT NOT NULL,
    estado_anterior VARCHAR(255),
    estado_nuevo    VARCHAR(255),
    usuario_id      BIGINT,
    fecha_cambio    TIMESTAMP DEFAULT NOW(),
    descripcion     TEXT
);

-- Previene la sobreventa solamente al crear una orden pagada.
-- Los cambios administrativos posteriores, como PENDIENTE -> APROBADA,
-- no vuelven a validar ni descontar inventario.

CREATE OR REPLACE FUNCTION trg_prevenir_sobreventa()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_carrito VARCHAR(255);
BEGIN
    -- Este trigger está diseñado para validar nuevas órdenes.
    -- La aprobación administrativa no debe tocar inventario.
    IF TG_OP = 'UPDATE' THEN
        RETURN NEW;
    END IF;

    -- Las órdenes pendientes todavía no consumen inventario aquí.
    IF NEW.estado NOT IN ('PAGADO', 'ORDENADO', 'APROBADA') THEN
        RETURN NEW;
    END IF;

    SELECT c.estado
    INTO v_estado_carrito
    FROM carrito_entidad c
    WHERE c.carrito_id = NEW.carrito_carrito_id;

    IF v_estado_carrito IS NULL THEN
        RAISE EXCEPTION
            'No se puede crear la orden: el carrito no existe';
    END IF;

    -- Si el carrito ya estaba pagado, su stock ya fue consumido.
    -- Esto también permite cargar órdenes históricas.
    IF v_estado_carrito <> 'ACTIVO' THEN
        RETURN NEW;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM carrito_producto_entidad
        WHERE carrito_carrito_id = NEW.carrito_carrito_id
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: el carrito está vacío';
    END IF;

    -- Validar stock global y stock reservado.
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id = NEW.carrito_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        JOIN producto_entidad p
          ON p.producto_id = solicitado.producto_producto_id
        WHERE p.stock < solicitado.cantidad
           OR p.stock_reservado < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: stock global o reservado insuficiente';
    END IF;

    IF NEW.almacen_asignado_id IS NULL THEN
        RAISE EXCEPTION
            'No se puede crear la orden sin almacén asignado';
    END IF;

    -- Validar stock físico del almacén.
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id = NEW.carrito_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        LEFT JOIN stock_almacen_producto_entidad sap
          ON sap.almacen_id = NEW.almacen_asignado_id
         AND sap.producto_id = solicitado.producto_producto_id
        WHERE COALESCE(sap.stock_disponible, 0)
              < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: stock insuficiente en el almacén asignado';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS prevenir_sobreventa
ON ordenes_entidad;

CREATE TRIGGER prevenir_sobreventa
    BEFORE INSERT
    ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_prevenir_sobreventa();

-- Trigger function: Actualizar última compra (Req. 6)
CREATE OR REPLACE FUNCTION trg_actualizar_ultima_compra()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_usuario_id BIGINT;
BEGIN
    IF NEW.estado IN ('PAGADO', 'APROBADA', 'ENTREGADO', 'EN_RUTA', 'PREPARANDO') THEN
        SELECT c.carrito_usuario_id
        INTO v_usuario_id
        FROM carrito_entidad c
        WHERE c.carrito_id = NEW.carrito_carrito_id;

        IF v_usuario_id IS NOT NULL THEN
            UPDATE usuario_entidad
            SET ultima_compra = NOW()
            WHERE usuario_id = v_usuario_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS actualizar_ultima_compra ON ordenes_entidad;
CREATE TRIGGER actualizar_ultima_compra
    AFTER INSERT OR UPDATE ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_actualizar_ultima_compra();

-- Trigger function: Auditar cambios en órdenes (Bonus)
CREATE OR REPLACE FUNCTION trg_auditar_cambios_orden()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_usuario_id      BIGINT;
    v_estado_anterior VARCHAR(255);
    v_descripcion     TEXT;
BEGIN
    SELECT c.carrito_usuario_id
    INTO v_usuario_id
    FROM carrito_entidad c
    WHERE c.carrito_id = NEW.carrito_carrito_id;

    IF TG_OP = 'INSERT' THEN
        v_estado_anterior := NULL;
        v_descripcion := 'Orden creada - Estado inicial: ' || COALESCE(NEW.estado, 'NULL');
    ELSE
        v_estado_anterior := OLD.estado;
        v_descripcion := 'Cambio de estado: '
            || COALESCE(OLD.estado, 'NULL')
            || ' -> '
            || COALESCE(NEW.estado, 'NULL');
    END IF;

    INSERT INTO audit_ordenes (
        orden_id,
        estado_anterior,
        estado_nuevo,
        usuario_id,
        fecha_cambio,
        descripcion
    )
    VALUES (
        NEW.orden_id,
        v_estado_anterior,
        NEW.estado,
        v_usuario_id,
        NOW(),
        v_descripcion
    );

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS auditar_cambios_orden ON ordenes_entidad;
CREATE TRIGGER auditar_cambios_orden
    AFTER INSERT OR UPDATE ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_auditar_cambios_orden();

-- Trigger function: Validar cobertura geográfica 
CREATE OR REPLACE FUNCTION trg_validar_cobertura_entrega()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ubicacion         GEOMETRY;
    v_dentro_cobertura  BOOLEAN;
BEGIN
    SELECT ie.ubicacion INTO v_ubicacion
    FROM informacion_entrega_entidad ie
    WHERE ie.info_entrega_id = NEW.informacion_info_entrega_id;

    IF v_ubicacion IS NULL THEN
        RAISE EXCEPTION 'La dirección de entrega % no tiene coordenadas geográficas registradas',
            NEW.informacion_info_entrega_id;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM zona_cobertura_entidad z
        WHERE z.activa = TRUE
          AND ST_Contains(z.geom, v_ubicacion)
    ) INTO v_dentro_cobertura;

    IF NOT v_dentro_cobertura THEN
        RAISE EXCEPTION 'No se puede crear la orden: la direccion de entrega esta fuera del area de cobertura de la empresa';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS validar_cobertura_entrega ON ordenes_entidad;
CREATE TRIGGER validar_cobertura_entrega
    BEFORE INSERT ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_validar_cobertura_entrega();

-- ─── 5. DATOS DE PRUEBA ────────────────────────────────────
-- Zona de cobertura: Región Metropolitana (polígono aproximado)
INSERT INTO zona_cobertura_entidad (nombre, geom, activa) VALUES
('Región Metropolitana', ST_GeomFromText(
    'POLYGON((-71.60 -32.85, -69.70 -32.85, -69.70 -34.35, -71.60 -34.35, -71.60 -32.85))',
    4326
), TRUE);

-- Almacenes (bodegas) de la empresa
INSERT INTO almacen_entidad (nombre, direccion, ubicacion) VALUES
('Almacén Central Santiago', 'Av. Libertador Bernardo O''Higgins 1449, Santiago',
    ST_SetSRID(ST_MakePoint(-70.5750, -33.4430), 4326)),
('Almacén Sur Puente Alto', 'Av. Concha y Toro 3300, Puente Alto',
    ST_SetSRID(ST_MakePoint(-70.5757, -33.6118), 4326)),
('Almacén Poniente Maipú', 'Av. Pajaritos 3050, Maipú',
    ST_SetSRID(ST_MakePoint(-70.7580, -33.4990), 4326));

INSERT INTO usuario_entidad (nombre_usuario, correo, contrasena, ultima_compra, rut_empresa, rol) VALUES
('Juan Perez',    'jperez@techsolutions.cl',       '$2a$12$Ue51OzMXnpiqWLxMPaAKjOPSnfs1267HJakRsYNn1jUIlzKopwL/q', '2023-10-01 10:00:00', '76.123.456-7', 'CLIENTE'),
('Maria Lopez',   'mlopez@techsolutions.cl',       '$2a$12$D4WkfIGsvL1tXLgfxuDp0uNaKuhEremwCRTYsyHWHlHd/Ac/PYVcC', '2023-11-15 14:20:00', '76.123.456-7', 'CLIENTE'),
('Carlos Ruiz',   'admin@ecommerceb2b.cl',         '$2a$12$Z0rQJcFIeZcX6.XwosKuqOI.Y.kpfY/hdXxtA127heFc6.xyZXz4q', '2022-01-10 09:00:00', '77.000.111-K', 'ADMIN'),
('Ana Soto',      'aso@construccionesdelnorte.cl', '$2a$12$QPGq3X4zz.Fe8Sl1A/UkMOVESAEDlNcUnq2d9MyWHhQzRAFW1hrCm', '2024-01-20 16:45:00', '80.555.444-2', 'CLIENTE'),
('Luis Fernandez','lfernandez@logisticadelsur.cl', '$2a$12$IZ6UqglJaxjyqbiQXz4njuTvZPDz95.Nfs0L6AOg.5Kcj9hgGZWEO', NULL,                  '90.222.333-1', 'CLIENTE'),
('Pedro Morales', 'pmo@desarrolloweb.cl',          '$2a$12$jxYs5vcAixX9biOL/h91k.kIy0yfJn3PqlbSWHi3cnCUzfEUTRMBW', '2024-03-10 11:30:00', '78.333.444-5', 'CLIENTE'),
('Sofia Vergara', 'svergara@saludintegral.cl',     '$2a$12$Uh1qdcJFW5JhpNrxPFA60.TtPpWx6yVnRvEvvX5woy5mim7tlmlF6', '2024-02-28 09:15:00', '79.111.222-3', 'CLIENTE'),
('Diego Vargas',  'dvar@adminb2b.cl',              '$2a$12$NQUmXqv6moflf/rSszbz6up2GF9UpY0dLgKGDlquH0G6Dqek7dY5S', '2023-05-05 10:00:00', '77.000.111-K', 'ADMIN'),
('Camila Rojas',  'crojas@mineraandina.cl',        '$2a$12$67Wz0mNx8GYBYWUXwKLy3.2TXq8mQfjATolERMawVZYPXywzQmNi6', NULL,                  '88.999.000-4', 'CLIENTE'),
('Javier Tello',  'jtello@educacionfuturo.cl',     '$2a$12$vlx4hMEyKHW9nT8cHKQmmeV6HHlvB7iE1oJffk0aDpXDsg2oSrH.y', '2024-04-12 15:45:00', '70.888.777-6', 'CLIENTE');

INSERT INTO categoria_entidad (nombre_categoria) VALUES
('Equipos de Computación'),
('Mobiliario de Oficina'),
('Insumos Tecnológicos'),
('Redes y Conectividad'),
('Licencias de Software'),
('Almacenamiento y Servidores'),
('Audio y Videoconferencia'),
('Seguridad Electrónica');

INSERT INTO producto_entidad (categoria_categoria_id, nombre_producto, descripcion, precio, stock, sku, activo) VALUES
(1, 'Notebook Empresarial Pro 15"',     'Notebook Intel Core i7, 16GB RAM, 512GB SSD',         1200000.0, 150, 'SKU-COMP-001', TRUE),
(1, 'Monitor 27" 4K',                   'Monitor IPS orientable para trabajo prolongado',         350000.0, 200, 'SKU-COMP-002', TRUE),
(1, 'Mini PC Corporativo',              'Desktop compacto Intel Core i5, 8GB RAM',                600000.0,  80, 'SKU-COMP-003', TRUE),
(2, 'Silla Ergonómica Premium',         'Silla de oficina ergonómica avanzada, soporte lumbar',   180000.0, 300, 'SKU-OFI-001',  TRUE),
(2, 'Escritorio Eléctrico Ajustable',   'Escritorio con ajuste de altura motorizado',             450000.0,  50, 'SKU-OFI-002',  TRUE),
(3, 'Set Toners Impresora Láser',       'Pack de 4 tóners CMYK alto rendimiento',                  85000.0, 500, 'SKU-INS-001',  TRUE),
(4, 'Router Empresarial Wi-Fi 6',       'Router VPN Gigabit Dual-WAN',                            150000.0, 120, 'SKU-RED-001',  TRUE),
(4, 'Switch Administrable 24 Puertos',  'Switch Gigabit Ethernet con PoE+',                       250000.0,  60, 'SKU-RED-002',  TRUE),
(5, 'Antivirus Corporativo (Lic. Anual)','Protección de endpoints para 10 usuarios',              300000.0, 999, 'SKU-SOFT-001', TRUE),
(5, 'Suite Ofimática 365 (Lic. Anual)', 'Suscripción empresarial por usuario',                   120000.0, 999, 'SKU-SOFT-002', TRUE),
(6, 'Servidor Rack 1U',                 'Servidor Xeon, 32GB RAM, 2TB SSD NVMe',                2500000.0,  20, 'SKU-SRV-001',  TRUE),
(6, 'Disco Duro NAS 8TB',               'HDD optimizado para almacenamiento en red',               280000.0, 100, 'SKU-SRV-002',  TRUE),
(7, 'Cámara Videoconferencia 4K',       'Cámara PTZ para salas de reuniones',                     450000.0,  40, 'SKU-AUD-001',  TRUE),
(7, 'Auriculares con Cancelación Ruido','Headset profesional UC',                                  85000.0, 250, 'SKU-AUD-002',  TRUE),
(3, 'Resmas de Papel A4 (Caja)',        'Caja de 10 resmas 75g',                                   35000.0,1000, 'SKU-INS-002',  TRUE),
(8, 'Kit Cámaras de Seguridad CCTV',    '4 cámaras 1080p + DVR 1TB',                             380000.0,  30, 'SKU-SEG-001',  TRUE),
(8, 'Control de Acceso Biométrico',     'Lector de huella y tarjeta RFID',                        120000.0,  80, 'SKU-SEG-002',  TRUE),
(1, 'Tablet Corporativa 10"',           'Tablet Android empresarial 64GB',                        220000.0, 150, 'SKU-COMP-004', TRUE),
(2, 'Cajonera Metálica',                'Cajonera bajo escritorio de 3 gavetas',                   95000.0, 180, 'SKU-OFI-003',  TRUE),
(4, 'Access Point Techo Wi-Fi 6',       'Punto de acceso empresarial doble banda',                135000.0, 100, 'SKU-RED-003',  TRUE);

-- Carritos
INSERT INTO carrito_entidad (carrito_usuario_id, estado, costo_carrito) VALUES
(1,  'PAGADO',    3350000),  -- Carrito 1
(2,  'ACTIVO',     450000),  -- Carrito 2
(4,  'PAGADO',    1260000),  -- Carrito 3
(6,  'PAGADO',    3610000),  -- Carrito 4
(7,  'PAGADO',     255000),  -- Carrito 5
(10, 'ACTIVO',    2550000),  -- Carrito 6
(9,  'ABANDONADO', 180000),  -- Carrito 7
(1,  'ACTIVO',      35000),  -- Carrito 8
(2,  'PAGADO',     840000),  -- Carrito 9
(5,  'PAGADO',     520000),  -- Carrito 10
(8,  'PAGADO',    3310000),  -- Carrito 11
(1,  'PAGADO',     720000),  -- Carrito 12
(3,  'PAGADO',     950000),  -- Carrito 13
(4,  'PAGADO',    1420000),  -- Carrito 14
(7,  'PAGADO',     610000);  -- Carrito 15

INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto) VALUES
-- Carrito 1 (PAGADO) - User 1
(1, 1, 2),  -- 2 * 1,200,000 = 2,400,000
(1, 2, 2),  -- 2 * 350,000   =   700,000
(1, 8, 1),  -- 1 * 250,000   =   250,000
-- Carrito 2 (ACTIVO) - User 2
(2, 5, 1),  -- 1 * 450,000   =   450,000
-- Carrito 3 (PAGADO) - User 4
(3, 4, 5),  -- 5 * 180,000   =   900,000
(3, 10, 3), -- 3 * 120,000   =   360,000
-- Carrito 4 (PAGADO) - User 6
(4, 11, 1), -- 1 * 2,500,000 = 2,500,000
(4, 12, 3), -- 3 * 280,000   =   840,000
(4, 20, 2), -- 2 * 135,000   =   270,000
-- Carrito 5 (PAGADO) - User 7
(5, 14, 3), -- 3 * 85,000    =   255,000
-- Carrito 6 (ACTIVO) - User 10
(6, 8,  4), -- 4 * 250,000   = 1,000,000
(6, 18, 5), -- 5 * 220,000   = 1,100,000
(6, 13, 1), -- 1 * 450,000   =   450,000
-- Carrito 7 (ABANDONADO) - User 9
(7, 4, 1),  -- 1 * 180,000   =   180,000
-- Carrito 8 (ACTIVO) - User 1
(8, 15, 1), -- 1 * 35,000    =    35,000
-- Carrito 9 (PAGADO) - User 2
(9, 3, 1),  -- 1 * 600,000   =   600,000
(9, 6, 2),  -- 2 * 85,000    =   170,000
(9, 15, 2), -- 2 * 35,000    =    70,000
-- Carrito 10 (PAGADO) - User 5
(10, 2, 1), -- 1 * 350,000   =   350,000
(10, 14, 2),-- 2 * 85,000    =   170,000
-- Carrito 11 (PAGADO) - User 8
(11, 11, 1),-- 1 * 2,500,000 = 2,500,000
(11, 12, 2),-- 2 * 280,000   =   560,000
(11, 8, 1), -- 1 * 250,000   =   250,000
-- Carrito 12 (PAGADO) - User 1
(12, 2, 1), -- 1 * 350,000   =   350,000
(12, 4, 2), -- 2 * 180,000   =   360,000
-- Carrito 13 (PAGADO) - User 3
(13, 6, 3), -- 3 * 85,000    =   255,000
(13, 7, 2), -- 2 * 150,000   =   300,000
(13, 9, 1), -- 1 * 300,000   =   300,000
-- Carrito 14 (PAGADO) - User 4
(14, 1, 1), -- 1 * 1,200,000 = 1,200,000
(14, 15, 2),-- 2 * 35,000    =    70,000
(14, 20, 1),-- 1 * 135,000   =   135,000
-- Carrito 15 (PAGADO) - User 7
(15, 14, 2),-- 2 * 85,000    =   170,000
(15, 5, 1); -- 1 * 450,000   =   450,000

-- Recalcular stock_reservado según carritos ACTIVO
UPDATE producto_entidad SET stock_reservado = 0;

UPDATE producto_entidad p
SET stock_reservado = s.total
FROM (
    SELECT cp.producto_producto_id, SUM(cp.unidad_producto)::INT AS total
    FROM carrito_producto_entidad cp
    JOIN carrito_entidad c ON c.carrito_id = cp.carrito_carrito_id
    WHERE c.estado = 'ACTIVO'
    GROUP BY cp.producto_producto_id
) s
WHERE p.producto_id = s.producto_producto_id;

-- Datos de pago de clientes
INSERT INTO datos_pago_entidad (usuario_usuario, metodo_pago, numero_tarjeta, fecha_expiracion) VALUES
(1, 'Tarjeta Crédito', '1234567812345678', '10/26'),
(2, 'Transferencia Bancaria', 'N/A', 'N/A'),
(4, 'Tarjeta Débito', '8765432187654321', '08/25'),
(6, 'Tarjeta Crédito', '4444333322221111', '12/26'),
(7, 'Tarjeta Débito', '5555444433332222', '07/25'),
(5, 'Transferencia Bancaria', 'N/A', 'N/A'),
(8, 'Tarjeta Crédito', '9999888877776666', '09/26'),
(3, 'Tarjeta Crédito', '1111222233334444', '11/25'),
(1, 'Tarjeta Débito', '2222333344445555', '05/27'),
(4, 'Tarjeta Crédito', '3333444455556666', '03/27');

-- Información de entrega (carritos completados)
INSERT INTO informacion_entrega_entidad (usuario_usuario, direccion, numero, rut_recibe_entrega, rut_empresa, estado_entrega, activa) VALUES
(1, 'Av. Las Condes',    '5430', '15.555.666-K', '76.123.456-7', 'ENTREGADO',  TRUE),
(4, 'Calle Industrias',  '1020', '16.777.888-2', '80.555.444-2', 'EN CAMINO',  TRUE),
(6, 'Providencia',       '1100', '14.222.333-1', '78.333.444-5', 'PREPARANDO', TRUE),
(7, 'Alameda',            '440', '12.888.999-5', '79.111.222-3', 'ENTREGADO',  TRUE),
(2, 'Las Flores',        '120', '11.222.333-4', '76.123.456-7', 'ENTREGADO',  TRUE),
(5, 'Av. Norte',         '980', '18.111.222-3', '90.222.333-1', 'ENTREGADO',  TRUE),
(8, 'Los Alerces',       '321', '10.999.888-7', '77.000.111-K', 'ENTREGADO',  TRUE),
(1, 'Av. La Florida',    '777', '12.123.123-1', '76.123.456-7', 'ENTREGADO',  TRUE),
(3, 'Av. Central',       '455', '19.555.222-9', '77.000.111-K', 'ENTREGADO',  TRUE),
(4, 'Nueva Esperanza',   '990', '17.888.777-6', '80.555.444-2', 'ENTREGADO',  TRUE),
(7, 'Gran Avenida',      '333', '13.333.444-5', '79.111.222-3', 'ENTREGADO',  TRUE);

-- Geocodificación aproximada del lote anterior
-- Debe ir antes de crear las órdenes: el trigger de cobertura las valida.
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6019, -33.4172), 4326), comuna = 'Las Condes'      WHERE info_entrega_id = 1;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6395, -33.4700), 4326), comuna = 'Quilicura'      WHERE info_entrega_id = 2;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6100, -33.4260), 4326), comuna = 'Providencia'    WHERE info_entrega_id = 3;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6500, -33.4600), 4326), comuna = 'Santiago Centro' WHERE info_entrega_id = 4;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.5980, -33.4489), 4326), comuna = 'Renca'          WHERE info_entrega_id = 5;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6944, -33.3806), 4326), comuna = 'Huechuraba'     WHERE info_entrega_id = 6;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.7200, -33.5500), 4326), comuna = 'Maipú'          WHERE info_entrega_id = 7;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6070, -33.4050), 4326), comuna = 'La Florida'     WHERE info_entrega_id = 8;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6693, -33.4569), 4326), comuna = 'Santiago Centro' WHERE info_entrega_id = 9;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6300, -33.4900), 4326), comuna = 'Peñalolén'      WHERE info_entrega_id = 10;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6600, -33.4650), 4326), comuna = 'San Miguel'     WHERE info_entrega_id = 11;

-- Órdenes generadas desde los carritos completados
INSERT INTO ordenes_entidad (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado) VALUES
(1, 1, '2023-10-01 10:05:00', 'APROBADA'),
(3, 2, '2024-01-20 16:50:00', 'PENDIENTE'),
(4, 3, '2024-03-10 11:35:00', 'PENDIENTE'),
(5, 4, '2024-02-28 09:20:00', 'APROBADA'),
(9, 5, '2024-05-15 12:10:00', 'APROBADA'),
(10, 6, '2025-01-10 15:30:00', 'APROBADA'),
(11, 7, '2025-11-22 10:20:00', 'APROBADA'),
(12, 8, '2024-07-02 14:05:00', 'CANCELADA'),
(13, 9, '2024-09-18 11:45:00', 'APROBADA'),
(14, 10, '2025-03-07 10:15:00', 'PENDIENTE'),
(15, 11, '2025-06-21 18:25:00', 'CANCELADA');

-- Vincular orden_id a informacion_entrega
UPDATE informacion_entrega_entidad SET orden_orden_id = 1 WHERE info_entrega_id = 1;
UPDATE informacion_entrega_entidad SET orden_orden_id = 2 WHERE info_entrega_id = 2;
UPDATE informacion_entrega_entidad SET orden_orden_id = 3 WHERE info_entrega_id = 3;
UPDATE informacion_entrega_entidad SET orden_orden_id = 4 WHERE info_entrega_id = 4;
UPDATE informacion_entrega_entidad SET orden_orden_id = 5 WHERE info_entrega_id = 5;
UPDATE informacion_entrega_entidad SET orden_orden_id = 6 WHERE info_entrega_id = 6;
UPDATE informacion_entrega_entidad SET orden_orden_id = 7 WHERE info_entrega_id = 7;
UPDATE informacion_entrega_entidad SET orden_orden_id = 8 WHERE info_entrega_id = 8;
UPDATE informacion_entrega_entidad SET orden_orden_id = 9 WHERE info_entrega_id = 9;
UPDATE informacion_entrega_entidad SET orden_orden_id = 10 WHERE info_entrega_id = 10;
UPDATE informacion_entrega_entidad SET orden_orden_id = 11 WHERE info_entrega_id = 11;

-- Validacion: la entrega y el carrito deben pertenecer al mismo cliente
-- Si este SELECT devuelve filas, hay inconsistencia
SELECT o.orden_id, c.carrito_usuario_id, ie.usuario_usuario
FROM ordenes_entidad o
JOIN carrito_entidad c ON c.carrito_id = o.carrito_carrito_id
JOIN informacion_entrega_entidad ie ON ie.info_entrega_id = o.informacion_info_entrega_id
WHERE c.carrito_usuario_id <> ie.usuario_usuario;

-- Facturas con IVA del 19%
INSERT INTO factura_entidad (usuario_usuario, datos_pago_id, orden_orden_id, precio_total, fecha_emision, total_neto, iva) VALUES
(1, 1,  1, 3350000.0, '2023-10-01 10:30:00', 2815126.0,  534874.0),
(4, 3,  2, 1260000.0, '2024-01-20 17:15:00', 1058824.0,  201176.0),
(6, 4,  3, 3610000.0, '2024-03-10 12:00:00', 3033613.0,  576387.0),
(7, 5,  4,  255000.0, '2024-02-28 09:45:00',  214286.0,   40714.0),
(2, 2,  5,  840000.0, '2024-05-15 12:30:00',  705882.0,  134118.0),
(5, 7,  6,  520000.0, '2025-01-10 16:00:00',  436975.0,   83025.0),
(8, 8,  7, 3310000.0, '2025-11-22 10:45:00', 2781513.0,  528487.0),
(1, 9,  8,  710000.0, '2024-07-02 14:20:00',  596639.0,  113361.0),
(3, 10, 9,  855000.0, '2024-09-18 12:10:00',  718487.0,  136513.0),
(4, 10, 10, 1405000.0,'2025-03-07 10:40:00', 1180672.0,  224328.0), -- ← cambiado de 11 a 10
(7, 5,   5,  620000.0,'2025-06-21 18:40:00',  521008.0,   98992.0);


-- ─── 5.1 DATOS ADICIONALES: MÁS CLIENTES CON ÓRDENES Y FACTURAS ─────
-- Estos registros agregan compras pagadas para clientes que antes no tenían
-- orden ni factura asociada. Así, al consultar clientes se podrán visualizar
-- sus órdenes, entregas y facturas mediante JOIN.

INSERT INTO carrito_entidad (carrito_usuario_id, estado, costo_carrito) VALUES
(2,  'PAGADO',  735000),   -- Carrito 9  - Maria Lopez
(5,  'PAGADO',  850000),   -- Carrito 10 - Luis Fernandez
(9,  'PAGADO',  535000),   -- Carrito 11 - Camila Rojas
(10, 'PAGADO', 2500000);   -- Carrito 12 - Javier Tello

INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto) VALUES
-- Carrito 9 (PAGADO) - User 2 / Maria Lopez
(9,  7, 2),   -- 2 * 150,000 = 300,000
(9, 20, 1),   -- 1 * 135,000 = 135,000
(9,  9, 1),   -- 1 * 300,000 = 300,000
-- Total carrito 9 = 735,000

-- Carrito 10 (PAGADO) - User 5 / Luis Fernandez
(10, 18, 2),  -- 2 * 220,000 = 440,000
(10, 14, 4),  -- 4 *  85,000 = 340,000
(10, 15, 2),  -- 2 *  35,000 =  70,000
-- Total carrito 10 = 850,000

-- Carrito 11 (PAGADO) - User 9 / Camila Rojas
(11, 16, 1),  -- 1 * 380,000 = 380,000
(11, 17, 1),  -- 1 * 120,000 = 120,000
(11, 15, 1),  -- 1 *  35,000 =  35,000
-- Total carrito 11 = 535,000

-- Carrito 12 (PAGADO) - User 10 / Javier Tello
(12,  1, 1),  -- 1 * 1,200,000 = 1,200,000
(12,  2, 2),  -- 2 *   350,000 =   700,000
(12, 10, 5);  -- 5 *   120,000 =   600,000
-- Total carrito 12 = 2,500,000

INSERT INTO informacion_entrega_entidad (usuario_usuario, direccion, numero, rut_recibe_entrega, rut_empresa, estado_entrega, activa) VALUES
(2,  'Av. Apoquindo',        '3200', '13.444.555-6', '76.123.456-7', 'ENTREGADO',  TRUE),
(5,  'Ruta 5 Sur',           '8800', '17.111.222-3', '90.222.333-1', 'EN CAMINO',  TRUE),
(9,  'Camino Industrial',    '1500', '18.333.444-5', '88.999.000-4', 'PREPARANDO', TRUE),
(10, 'Av. Libertador Norte', '245',  '19.666.777-8', '70.888.777-6', 'ENTREGADO',  TRUE);

-- Geocodificación aproximada del lote anterior
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6120, -33.4270), 4326), comuna = 'Las Condes'    WHERE info_entrega_id = 12;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6980, -33.7150), 4326), comuna = 'San Bernardo' WHERE info_entrega_id = 13;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6480, -33.4830), 4326), comuna = 'Quilicura'    WHERE info_entrega_id = 14;
UPDATE informacion_entrega_entidad SET ubicacion = ST_SetSRID(ST_MakePoint(-70.6710, -33.4390), 4326), comuna = 'Independencia' WHERE info_entrega_id = 15;

INSERT INTO ordenes_entidad (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado) VALUES
(9,  5, '2024-04-18 10:10:00', 'ENTREGADO'),
(10, 6, '2024-04-22 12:25:00', 'EN_RUTA'),
(11, 7, '2024-05-03 09:40:00', 'PREPARANDO'),
(12, 8, '2024-05-08 15:05:00', 'ENTREGADO');

UPDATE informacion_entrega_entidad SET orden_orden_id = 5 WHERE info_entrega_id = 5;
UPDATE informacion_entrega_entidad SET orden_orden_id = 6 WHERE info_entrega_id = 6;
UPDATE informacion_entrega_entidad SET orden_orden_id = 7 WHERE info_entrega_id = 7;
UPDATE informacion_entrega_entidad SET orden_orden_id = 8 WHERE info_entrega_id = 8;

INSERT INTO factura_entidad (usuario_usuario, orden_orden_id, precio_total, fecha_emision, total_neto, iva) VALUES
(2,  5,  735000.0, '2024-04-18 10:35:00',  617647.0, 117353.0),
(5,  6,  850000.0, '2024-04-22 12:50:00',  714286.0, 135714.0),
(9,  7,  535000.0, '2024-05-03 10:05:00',  449580.0,  85420.0),
(10, 8, 2500000.0, '2024-05-08 15:30:00', 2100840.0, 399160.0);

-- Actualizar la última compra de los clientes que recibieron nuevas órdenes
UPDATE usuario_entidad SET ultima_compra = '2024-04-18 10:10:00' WHERE usuario_id = 2;
UPDATE usuario_entidad SET ultima_compra = '2024-04-22 12:25:00' WHERE usuario_id = 5;
UPDATE usuario_entidad SET ultima_compra = '2024-05-03 09:40:00' WHERE usuario_id = 9;
UPDATE usuario_entidad SET ultima_compra = '2024-05-08 15:05:00' WHERE usuario_id = 10;

-- ─── 6. VISTA MATERIALIZADA (Req. 7) ───────────────────────

CREATE MATERIALIZED VIEW vw_ventas_mensuales_por_categoria AS
SELECT
    TO_CHAR(o.fecha_orden, 'YYYY-MM')      AS mes_ano,
    EXTRACT(YEAR  FROM o.fecha_orden)::INT AS anio,
    EXTRACT(MONTH FROM o.fecha_orden)::INT AS mes,
    c.nombre_categoria,
    COUNT(DISTINCT o.orden_id)             AS cantidad_ordenes,
    SUM(cp.unidad_producto)::INT           AS cantidad_productos,
    ROUND(SUM(cp.unidad_producto * p.precio)::NUMERIC, 2) AS total_vendido,
    ROUND(AVG(p.precio)::NUMERIC, 2)       AS precio_promedio
FROM ordenes_entidad o
JOIN carrito_entidad         cart ON o.carrito_carrito_id   = cart.carrito_id
JOIN carrito_producto_entidad cp  ON cart.carrito_id        = cp.carrito_carrito_id
JOIN producto_entidad         p   ON cp.producto_producto_id = p.producto_id
JOIN categoria_entidad        c   ON p.categoria_categoria_id = c.categoria_id
WHERE o.estado IN ('PENDIENTE', 'APROBADA', 'CANCELADA')
  AND o.fecha_orden IS NOT NULL
GROUP BY
    TO_CHAR(o.fecha_orden, 'YYYY-MM'),
    EXTRACT(YEAR  FROM o.fecha_orden),
    EXTRACT(MONTH FROM o.fecha_orden),
    c.nombre_categoria
ORDER BY anio DESC, mes DESC, c.nombre_categoria;

-- Índices para acelerar consultas
CREATE INDEX idx_vw_ventas_mes_ano   ON vw_ventas_mensuales_por_categoria (mes_ano);
CREATE INDEX idx_vw_ventas_categoria ON vw_ventas_mensuales_por_categoria (nombre_categoria);
CREATE INDEX idx_vw_ventas_anio      ON vw_ventas_mensuales_por_categoria (anio);

-- Ventas agrupadas por comuna: reemplazada por ventas_por_comuna /
-- ventas_por_distrito, basadas en los polígonos reales de comuna_entidad
-- (ver sección "CHOROPLETH COMUNA/DISTRITO" al final de este archivo).
-- La vista anterior (vw_ventas_por_comuna) usaba un blob ST_Buffer(ST_Union(...))
-- de los puntos de entrega en vez del polígono administrativo real.

-- ─── 7. ÍNDICES PARA OPTIMIZACIÓN (Req. 8) ──────────────────

-- Índices en tablas principales para acelerar búsquedas frecuentes
CREATE INDEX idx_producto_sku ON producto_entidad(sku);
CREATE INDEX idx_usuario_id ON usuario_entidad(usuario_id);
CREATE INDEX idx_carrito_usuario ON carrito_entidad(carrito_usuario_id);
CREATE INDEX idx_carrito_producto_carrito ON carrito_producto_entidad(carrito_carrito_id);
CREATE INDEX idx_carrito_producto_producto ON carrito_producto_entidad(producto_producto_id);
CREATE INDEX idx_orden_carrito ON ordenes_entidad(carrito_carrito_id);
CREATE INDEX idx_orden_usuario ON ordenes_entidad(informacion_info_entrega_id);
CREATE INDEX idx_factura_usuario ON factura_entidad(usuario_usuario);
CREATE INDEX idx_factura_orden ON factura_entidad(orden_orden_id);
CREATE INDEX idx_producto_categoria ON producto_entidad(categoria_categoria_id);
CREATE INDEX idx_informacion_entrega_usuario ON informacion_entrega_entidad(usuario_usuario);
CREATE INDEX idx_informacion_entrega_orden ON informacion_entrega_entidad(orden_orden_id);

-- ─── 7.1 ÍNDICES ESPACIALES GIST ──────────
CREATE INDEX idx_informacion_entrega_ubicacion ON informacion_entrega_entidad USING GIST (ubicacion);
CREATE INDEX idx_almacen_ubicacion ON almacen_entidad USING GIST (ubicacion);
CREATE INDEX idx_zona_cobertura_geom ON zona_cobertura_entidad USING GIST (geom);

-- ============================================================
-- INTEGRACIÓN FINAL: PUNTOS 2, 3 Y 4
-- 2) El procedimiento no descuenta stock global directamente:
--    solo cambia el carrito a PAGADO y el trigger consume la reserva.
-- 3) Stock normalizado por almacén y selección del almacén más cercano
--    que pueda satisfacer el carrito completo.
-- 4) Categorías restringidas y zonas residenciales protegidas.
--
-- Esta sección forma parte del init.sql y reemplaza las definiciones previas.
-- ============================================================

BEGIN;

-- ============================================================
-- 1. EXTENSIONES DEL MODELO
-- ============================================================

ALTER TABLE categoria_entidad
    ADD COLUMN IF NOT EXISTS restringida_zona_residencial
    BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ordenes_entidad
    ADD COLUMN IF NOT EXISTS distancia_envio_km NUMERIC(12,3);

-- Costo de envío calculado en el checkout (valor_km * distancia), incluido en el total.
ALTER TABLE factura_entidad
    ADD COLUMN IF NOT EXISTS costo_envio NUMERIC(14,2) NOT NULL DEFAULT 0;

-- Configuración global de la tarifa de envío: valor cobrado por kilómetro.
-- Tabla de una sola fila que el administrador puede editar.
CREATE TABLE IF NOT EXISTS configuracion_envio_entidad (
    config_id BIGSERIAL PRIMARY KEY,
    valor_km  NUMERIC(14,2) NOT NULL DEFAULT 0
);

-- Valor por km por defecto (solo si la tabla está vacía).
INSERT INTO configuracion_envio_entidad (valor_km)
SELECT 500
WHERE NOT EXISTS (SELECT 1 FROM configuracion_envio_entidad);

CREATE TABLE IF NOT EXISTS stock_almacen_producto_entidad (
    stock_almacen_id BIGSERIAL PRIMARY KEY,
    almacen_id       BIGINT NOT NULL
        REFERENCES almacen_entidad(almacen_id) ON DELETE CASCADE,
    producto_id      BIGINT NOT NULL
        REFERENCES producto_entidad(producto_id) ON DELETE CASCADE,
    stock_disponible INT NOT NULL DEFAULT 0,
    CONSTRAINT ux_stock_almacen_producto
        UNIQUE (almacen_id, producto_id),
    CONSTRAINT ck_stock_almacen_disponible
        CHECK (stock_disponible >= 0)
);

CREATE TABLE IF NOT EXISTS zona_residencial_protegida_entidad (
    zona_id     BIGSERIAL PRIMARY KEY,
    nombre_zona VARCHAR(255) NOT NULL UNIQUE,
    activa      BOOLEAN NOT NULL DEFAULT TRUE,
    poligono    geometry(Polygon, 4326) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stock_almacen_producto
    ON stock_almacen_producto_entidad (almacen_id, producto_id);

CREATE INDEX IF NOT EXISTS idx_zona_residencial_poligono_gist
    ON zona_residencial_protegida_entidad
    USING GIST (poligono);

-- ============================================================
-- 2. CONFIGURACIÓN INICIAL DE RESTRICCIONES RESIDENCIALES
-- ============================================================

UPDATE categoria_entidad
SET restringida_zona_residencial = TRUE
WHERE LOWER(nombre_categoria) IN (
    'insumos tecnológicos',
    'seguridad electrónica'
);

INSERT INTO zona_residencial_protegida_entidad (
    nombre_zona,
    activa,
    poligono
)
SELECT
    'Zona Residencial Providencia',
    TRUE,
    ST_GeomFromText(
        'POLYGON((-70.66 -33.47, -70.62 -33.47, -70.62 -33.42, -70.66 -33.42, -70.66 -33.47))',
        4326
    )
WHERE NOT EXISTS (
    SELECT 1
    FROM zona_residencial_protegida_entidad
    WHERE nombre_zona = 'Zona Residencial Providencia'
);

INSERT INTO zona_residencial_protegida_entidad (
    nombre_zona,
    activa,
    poligono
)
SELECT
    'Zona Residencial La Florida',
    TRUE,
    ST_GeomFromText(
        'POLYGON((-70.61 -33.58, -70.54 -33.58, -70.54 -33.52, -70.61 -33.52, -70.61 -33.58))',
        4326
    )
WHERE NOT EXISTS (
    SELECT 1
    FROM zona_residencial_protegida_entidad
    WHERE nombre_zona = 'Zona Residencial La Florida'
);

-- ============================================================
-- 3. DISTRIBUCIÓN INICIAL DEL STOCK ENTRE ALMACENES
-- ============================================================
-- Solo crea registros que todavía no existen.
-- No sobrescribe inventario ya administrado.

WITH distribucion AS (
    SELECT
        a.almacen_id,
        p.producto_id,
        p.stock,
        ROW_NUMBER() OVER (
            PARTITION BY p.producto_id
            ORDER BY a.almacen_id
        ) AS posicion,
        COUNT(*) OVER (
            PARTITION BY p.producto_id
        ) AS cantidad_almacenes
    FROM almacen_entidad a
    CROSS JOIN producto_entidad p
)
INSERT INTO stock_almacen_producto_entidad (
    almacen_id,
    producto_id,
    stock_disponible
)
SELECT
    almacen_id,
    producto_id,
    CASE
        WHEN cantidad_almacenes = 1 THEN stock
        WHEN posicion < cantidad_almacenes THEN
            FLOOR(stock::NUMERIC / cantidad_almacenes)::INT
        ELSE
            stock
            - FLOOR(stock::NUMERIC / cantidad_almacenes)::INT
              * (cantidad_almacenes - 1)
    END
FROM distribucion
ON CONFLICT (almacen_id, producto_id) DO NOTHING;

-- ============================================================
-- 4. RESERVA GLOBAL ROBUSTA
-- ============================================================

CREATE OR REPLACE FUNCTION ajustar_reserva_por_carrito(
    p_carrito_id BIGINT,
    p_accion TEXT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_accion = 'RESERVAR' THEN

        IF EXISTS (
            SELECT 1
            FROM (
                SELECT
                    producto_producto_id,
                    SUM(unidad_producto)::INT AS cantidad
                FROM carrito_producto_entidad
                WHERE carrito_carrito_id = p_carrito_id
                GROUP BY producto_producto_id
            ) solicitado
            JOIN producto_entidad p
              ON p.producto_id = solicitado.producto_producto_id
            WHERE p.activo IS NOT TRUE
               OR p.stock_reservado + solicitado.cantidad > p.stock
        ) THEN
            RAISE EXCEPTION
                'Stock insuficiente o producto inactivo al reactivar el carrito %',
                p_carrito_id;
        END IF;

        UPDATE producto_entidad p
        SET stock_reservado =
            p.stock_reservado + solicitado.cantidad
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id = p_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        WHERE p.producto_id = solicitado.producto_producto_id;

    ELSIF p_accion = 'LIBERAR' THEN

        IF EXISTS (
            SELECT 1
            FROM (
                SELECT
                    producto_producto_id,
                    SUM(unidad_producto)::INT AS cantidad
                FROM carrito_producto_entidad
                WHERE carrito_carrito_id = p_carrito_id
                GROUP BY producto_producto_id
            ) solicitado
            JOIN producto_entidad p
              ON p.producto_id = solicitado.producto_producto_id
            WHERE p.stock_reservado < solicitado.cantidad
        ) THEN
            RAISE EXCEPTION
                'Stock reservado insuficiente para liberar el carrito %',
                p_carrito_id;
        END IF;

        UPDATE producto_entidad p
        SET stock_reservado =
            p.stock_reservado - solicitado.cantidad
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id = p_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        WHERE p.producto_id = solicitado.producto_producto_id;

    ELSIF p_accion = 'CONSUMIR' THEN

        IF EXISTS (
            SELECT 1
            FROM (
                SELECT
                    producto_producto_id,
                    SUM(unidad_producto)::INT AS cantidad
                FROM carrito_producto_entidad
                WHERE carrito_carrito_id = p_carrito_id
                GROUP BY producto_producto_id
            ) solicitado
            JOIN producto_entidad p
              ON p.producto_id = solicitado.producto_producto_id
            WHERE p.stock < solicitado.cantidad
               OR p.stock_reservado < solicitado.cantidad
        ) THEN
            RAISE EXCEPTION
                'Stock global o reservado insuficiente para el carrito %',
                p_carrito_id;
        END IF;

        UPDATE producto_entidad p
        SET stock =
                p.stock - solicitado.cantidad,
            stock_reservado =
                p.stock_reservado - solicitado.cantidad
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id = p_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        WHERE p.producto_id = solicitado.producto_producto_id;

    ELSE
        RAISE EXCEPTION 'Acción de reserva no válida: %', p_accion;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION trg_carrito_estado_cambio()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.estado IS NOT DISTINCT FROM OLD.estado THEN
        RETURN NEW;
    END IF;

    IF OLD.estado = 'ACTIVO'
       AND NEW.estado = 'ABANDONADO' THEN

        PERFORM ajustar_reserva_por_carrito(
            NEW.carrito_id,
            'LIBERAR'
        );

    ELSIF OLD.estado = 'ABANDONADO'
          AND NEW.estado = 'ACTIVO' THEN

        PERFORM ajustar_reserva_por_carrito(
            NEW.carrito_id,
            'RESERVAR'
        );

    ELSIF OLD.estado = 'ACTIVO'
          AND NEW.estado IN ('PAGADO', 'ORDENADO') THEN

        -- ÚNICO punto donde se descuenta el stock global.
        PERFORM ajustar_reserva_por_carrito(
            NEW.carrito_id,
            'CONSUMIR'
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS carrito_estado_cambio
ON carrito_entidad;

CREATE TRIGGER carrito_estado_cambio
    AFTER UPDATE OF estado
    ON carrito_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_carrito_estado_cambio();

-- ============================================================
-- 5. COBERTURA GEOGRÁFICA
-- ============================================================
-- Se usa ST_Covers para aceptar también puntos ubicados
-- exactamente sobre el borde del polígono.

CREATE OR REPLACE FUNCTION trg_validar_cobertura_entrega()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ubicacion geometry(Point, 4326);
BEGIN
    SELECT ie.ubicacion
    INTO v_ubicacion
    FROM informacion_entrega_entidad ie
    WHERE ie.info_entrega_id =
          NEW.informacion_info_entrega_id;

    IF v_ubicacion IS NULL THEN
        RAISE EXCEPTION
            'La dirección de entrega % no tiene coordenadas geográficas registradas',
            NEW.informacion_info_entrega_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM zona_cobertura_entidad z
        WHERE z.activa = TRUE
          AND ST_Covers(z.geom, v_ubicacion)
    ) THEN
        RAISE EXCEPTION
            'La dirección de entrega está fuera del área de cobertura';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS validar_cobertura_entrega
ON ordenes_entidad;

CREATE TRIGGER validar_cobertura_entrega
    BEFORE INSERT OR UPDATE OF informacion_info_entrega_id
    ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_validar_cobertura_entrega();

-- ============================================================
-- 6. CATEGORÍAS RESTRINGIDAS EN ZONAS RESIDENCIALES
-- ============================================================

CREATE OR REPLACE FUNCTION
trg_validar_categoria_restringida_por_zona()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ubicacion geometry(Point, 4326);
BEGIN
    SELECT ie.ubicacion
    INTO v_ubicacion
    FROM informacion_entrega_entidad ie
    WHERE ie.info_entrega_id =
          NEW.informacion_info_entrega_id;

    IF v_ubicacion IS NULL THEN
        RAISE EXCEPTION
            'La entrega no tiene ubicación geoespacial definida';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM carrito_producto_entidad cp
        JOIN producto_entidad p
          ON p.producto_id = cp.producto_producto_id
        JOIN categoria_entidad c
          ON c.categoria_id = p.categoria_categoria_id
        WHERE cp.carrito_carrito_id =
              NEW.carrito_carrito_id
          AND c.restringida_zona_residencial = TRUE
          AND EXISTS (
              SELECT 1
              FROM zona_residencial_protegida_entidad z
              WHERE z.activa = TRUE
                AND ST_Covers(z.poligono, v_ubicacion)
          )
    ) THEN
        RAISE EXCEPTION
            'Orden bloqueada: contiene categorías restringidas para una zona residencial protegida';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS validar_categoria_restringida_por_zona
ON ordenes_entidad;

CREATE TRIGGER validar_categoria_restringida_por_zona
    BEFORE INSERT OR UPDATE OF
        carrito_carrito_id,
        informacion_info_entrega_id
    ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION
        trg_validar_categoria_restringida_por_zona();

-- ============================================================
-- 7. PREVENCIÓN DE SOBREVENTA GLOBAL Y POR ALMACÉN
-- ============================================================
-- La validación se ejecuta únicamente al CREAR una orden.
-- Los cambios administrativos posteriores, como
-- PENDIENTE -> APROBADA o PENDIENTE -> CANCELADA,
-- no vuelven a validar ni a descontar inventario.

CREATE OR REPLACE FUNCTION trg_prevenir_sobreventa()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM carrito_producto_entidad
        WHERE carrito_carrito_id =
              NEW.carrito_carrito_id
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: el carrito está vacío';
    END IF;

    -- Verifica el stock global y la reserva del carrito antes
    -- de confirmar la creación de la orden.
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id =
                  NEW.carrito_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        JOIN producto_entidad p
          ON p.producto_id =
             solicitado.producto_producto_id
        WHERE p.activo IS NOT TRUE
           OR p.stock < solicitado.cantidad
           OR p.stock_reservado < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: stock global o reservado insuficiente';
    END IF;

    IF NEW.almacen_asignado_id IS NULL THEN
        RAISE EXCEPTION
            'No se puede crear la orden sin almacén asignado';
    END IF;

    -- Verifica que el almacén asignado pueda satisfacer
    -- todos los productos del carrito.
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                producto_producto_id,
                SUM(unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad
            WHERE carrito_carrito_id =
                  NEW.carrito_carrito_id
            GROUP BY producto_producto_id
        ) solicitado
        LEFT JOIN stock_almacen_producto_entidad sap
          ON sap.almacen_id =
             NEW.almacen_asignado_id
         AND sap.producto_id =
             solicitado.producto_producto_id
        WHERE COALESCE(sap.stock_disponible, 0)
              < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: stock insuficiente en el almacén asignado';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS prevenir_sobreventa
ON ordenes_entidad;

CREATE TRIGGER prevenir_sobreventa
    BEFORE INSERT
    ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_prevenir_sobreventa();

-- ============================================================
-- 8. CHECKOUT ATÓMICO
-- ============================================================
-- El procedimiento:
--   1. Valida carrito, entrega, cobertura y restricciones.
--   2. Selecciona el almacén más cercano con stock completo.
--   3. Bloquea el inventario físico del almacén.
--   4. Crea orden, factura e ítems.
--   5. Descuenta el stock del almacén.
--   6. SOLO cambia el carrito a PAGADO.
--      El trigger consume el stock global una única vez.
--   7. Vacía el carrito dentro de la misma transacción.

-- El checkout real de la API invoca este procedimiento con la dirección
-- y el medio de pago seleccionados por el cliente.
DROP PROCEDURE IF EXISTS procesar_checkout(BIGINT, BIGINT);
DROP PROCEDURE IF EXISTS procesar_checkout(BIGINT, BIGINT, BIGINT);

CREATE OR REPLACE PROCEDURE procesar_checkout(
    p_carrito_id BIGINT,
    p_info_entrega_id BIGINT,
    p_datos_pago_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_usuario_id        BIGINT;
    v_ubicacion_entrega geometry(Point, 4326);
    v_almacen_id        BIGINT;
    v_almacen_nombre    VARCHAR(255);
    v_distancia_km      NUMERIC(12,3);
    v_candidato         RECORD;
    v_total_neto        NUMERIC(14,2);
    v_iva               NUMERIC(14,2);
    v_precio_total      NUMERIC(14,2);
    v_subtotal_productos NUMERIC(14,2);
    v_valor_km          NUMERIC(14,2);
    v_costo_envio       NUMERIC(14,2);
    v_orden_id          INT;
    v_factura_id        BIGINT;
BEGIN
    IF p_carrito_id IS NULL OR p_carrito_id <= 0 THEN
        RAISE EXCEPTION 'El carrito es obligatorio';
    END IF;

    IF p_info_entrega_id IS NULL OR p_info_entrega_id <= 0 THEN
        RAISE EXCEPTION 'La dirección de entrega es obligatoria';
    END IF;

    IF p_datos_pago_id IS NULL OR p_datos_pago_id <= 0 THEN
        RAISE EXCEPTION 'Los datos de pago son obligatorios';
    END IF;

    -- Impide que dos solicitudes procesen el mismo carrito simultáneamente.
    SELECT c.carrito_usuario_id
    INTO v_usuario_id
    FROM carrito_entidad c
    WHERE c.carrito_id = p_carrito_id
      AND c.estado = 'ACTIVO'
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Carrito % no existe o no está ACTIVO',
            p_carrito_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
    ) THEN
        RAISE EXCEPTION 'El carrito % está vacío', p_carrito_id;
    END IF;

    -- La entrega debe pertenecer al dueño del carrito y estar activa.
    SELECT ie.ubicacion
    INTO v_ubicacion_entrega
    FROM informacion_entrega_entidad ie
    WHERE ie.info_entrega_id = p_info_entrega_id
      AND ie.usuario_usuario = v_usuario_id
      AND ie.activa = TRUE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'La entrega % no existe, está inactiva o no pertenece al usuario',
            p_info_entrega_id;
    END IF;

    IF v_ubicacion_entrega IS NULL THEN
        RAISE EXCEPTION
            'La entrega % no tiene coordenadas geográficas',
            p_info_entrega_id;
    END IF;

    -- El medio de pago también debe pertenecer al dueño del carrito.
    IF NOT EXISTS (
        SELECT 1
        FROM datos_pago_entidad dp
        WHERE dp.datos_pago_id = p_datos_pago_id
          AND dp.usuario_usuario = v_usuario_id
    ) THEN
        RAISE EXCEPTION
            'Los datos de pago % no existen o no pertenecen al usuario',
            p_datos_pago_id;
    END IF;

    -- Cobertura general de la empresa.
    IF NOT EXISTS (
        SELECT 1
        FROM zona_cobertura_entidad z
        WHERE z.activa = TRUE
          AND ST_Covers(z.geom, v_ubicacion_entrega)
    ) THEN
        RAISE EXCEPTION
            'La dirección de entrega está fuera del área de cobertura';
    END IF;

    -- Categorías restringidas dentro de zonas residenciales protegidas.
    IF EXISTS (
        SELECT 1
        FROM carrito_producto_entidad cp
        JOIN producto_entidad p
          ON p.producto_id = cp.producto_producto_id
        JOIN categoria_entidad c
          ON c.categoria_id = p.categoria_categoria_id
        WHERE cp.carrito_carrito_id = p_carrito_id
          AND c.restringida_zona_residencial = TRUE
          AND EXISTS (
              SELECT 1
              FROM zona_residencial_protegida_entidad z
              WHERE z.activa = TRUE
                AND ST_Covers(z.poligono, v_ubicacion_entrega)
          )
    ) THEN
        RAISE EXCEPTION
            'No se permiten categorías restringidas en esta zona residencial';
    END IF;

    -- Bloquea los productos globales en un orden estable y revalida la reserva.
    PERFORM p.producto_id
    FROM producto_entidad p
    JOIN (
        SELECT
            cp.producto_producto_id,
            SUM(cp.unidad_producto)::INT AS cantidad
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
        GROUP BY cp.producto_producto_id
    ) solicitado
      ON solicitado.producto_producto_id = p.producto_id
    ORDER BY p.producto_id
    FOR UPDATE OF p;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                cp.producto_producto_id,
                SUM(cp.unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad cp
            WHERE cp.carrito_carrito_id = p_carrito_id
            GROUP BY cp.producto_producto_id
        ) solicitado
        JOIN producto_entidad p
          ON p.producto_id = solicitado.producto_producto_id
        WHERE p.activo IS NOT TRUE
           OR p.stock < solicitado.cantidad
           OR p.stock_reservado < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION 'Stock global o reservado insuficiente';
    END IF;

    -- Recorre los almacenes por proximidad. ST_Distance calcula la distancia
    -- real en metros sobre geography; el operador <-> permite usar el índice GIST.
    FOR v_candidato IN
        SELECT
            a.almacen_id,
            a.nombre,
            ROUND(
                (
                    ST_Distance(
                        a.ubicacion::geography,
                        v_ubicacion_entrega::geography
                    ) / 1000.0
                )::NUMERIC,
                3
            ) AS distancia_km
        FROM almacen_entidad a
        ORDER BY a.ubicacion <-> v_ubicacion_entrega
    LOOP
        -- Bloquea el inventario del candidato antes de comprobarlo.
        PERFORM sap.stock_almacen_id
        FROM stock_almacen_producto_entidad sap
        JOIN (
            SELECT
                cp.producto_producto_id,
                SUM(cp.unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad cp
            WHERE cp.carrito_carrito_id = p_carrito_id
            GROUP BY cp.producto_producto_id
        ) solicitado
          ON solicitado.producto_producto_id = sap.producto_id
        WHERE sap.almacen_id = v_candidato.almacen_id
        ORDER BY sap.producto_id
        FOR UPDATE OF sap;

        IF NOT EXISTS (
            SELECT 1
            FROM (
                SELECT
                    cp.producto_producto_id,
                    SUM(cp.unidad_producto)::INT AS cantidad
                FROM carrito_producto_entidad cp
                WHERE cp.carrito_carrito_id = p_carrito_id
                GROUP BY cp.producto_producto_id
            ) solicitado
            LEFT JOIN stock_almacen_producto_entidad sap
              ON sap.almacen_id = v_candidato.almacen_id
             AND sap.producto_id = solicitado.producto_producto_id
            WHERE COALESCE(sap.stock_disponible, 0) < solicitado.cantidad
        ) THEN
            v_almacen_id := v_candidato.almacen_id;
            v_almacen_nombre := v_candidato.nombre;
            v_distancia_km := v_candidato.distancia_km;
            EXIT;
        END IF;
    END LOOP;

    IF v_almacen_id IS NULL THEN
        RAISE EXCEPTION
            'No existe un almacén con stock suficiente para el carrito %',
            p_carrito_id;
    END IF;

    -- Los precios del catálogo se consideran precios finales con IVA incluido.
    SELECT ROUND(
        COALESCE(SUM(cp.unidad_producto * p.precio), 0)::NUMERIC,
        2
    )
    INTO v_subtotal_productos
    FROM carrito_producto_entidad cp
    JOIN producto_entidad p
      ON p.producto_id = cp.producto_producto_id
    WHERE cp.carrito_carrito_id = p_carrito_id;

    IF v_subtotal_productos <= 0 THEN
        RAISE EXCEPTION 'El total del carrito es inválido';
    END IF;

    -- Tarifa de última milla: valor por km (config global) * distancia al almacén asignado.
    SELECT valor_km INTO v_valor_km
    FROM configuracion_envio_entidad
    ORDER BY config_id
    LIMIT 1;

    v_valor_km := COALESCE(v_valor_km, 0);
    v_costo_envio := ROUND(v_valor_km * COALESCE(v_distancia_km, 0), 2);

    -- El total incluye productos + envío; el IVA se recalcula sobre ese total.
    v_precio_total := v_subtotal_productos + v_costo_envio;
    v_total_neto := ROUND(v_precio_total / 1.19, 2);
    v_iva := ROUND(v_precio_total - v_total_neto, 2);

    -- La orden queda pendiente de aprobación administrativa. El inventario
    -- se consume durante este checkout y no vuelve a descontarse al aprobar.
    INSERT INTO ordenes_entidad (
        carrito_carrito_id,
        informacion_info_entrega_id,
        fecha_orden,
        estado,
        almacen_asignado_id,
        distancia_envio_km
    )
    VALUES (
        p_carrito_id,
        p_info_entrega_id,
        NOW(),
        'PENDIENTE',
        v_almacen_id,
        v_distancia_km
    )
    RETURNING orden_id INTO v_orden_id;

    INSERT INTO factura_entidad (
        usuario_usuario,
        datos_pago_id,
        orden_orden_id,
        precio_total,
        fecha_emision,
        total_neto,
        iva,
        costo_envio
    )
    VALUES (
        v_usuario_id,
        p_datos_pago_id,
        v_orden_id,
        v_precio_total,
        NOW(),
        v_total_neto,
        v_iva,
        v_costo_envio
    )
    RETURNING factura_id INTO v_factura_id;

    -- Copia histórica de los productos antes de vaciar el carrito.
    INSERT INTO factura_item_entidad (
        factura_id,
        producto_id,
        cantidad,
        precio_unitario
    )
    SELECT
        v_factura_id,
        cp.producto_producto_id,
        SUM(cp.unidad_producto)::INT,
        p.precio
    FROM carrito_producto_entidad cp
    JOIN producto_entidad p
      ON p.producto_id = cp.producto_producto_id
    WHERE cp.carrito_carrito_id = p_carrito_id
    GROUP BY cp.producto_producto_id, p.precio;

    -- Único descuento del inventario físico del almacén.
    UPDATE stock_almacen_producto_entidad sap
    SET stock_disponible = sap.stock_disponible - solicitado.cantidad
    FROM (
        SELECT
            cp.producto_producto_id,
            SUM(cp.unidad_producto)::INT AS cantidad
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
        GROUP BY cp.producto_producto_id
    ) solicitado
    WHERE sap.almacen_id = v_almacen_id
      AND sap.producto_id = solicitado.producto_producto_id;

    -- Este cambio dispara carrito_estado_cambio y consume exactamente una vez
    -- el stock global y el stock reservado.
    UPDATE carrito_entidad
    SET estado = 'PAGADO',
        ultima_actualizacion = NOW()
    WHERE carrito_id = p_carrito_id;

    -- Registra el momento real de la compra. La orden se crea en estado
    -- PENDIENTE, por lo que el trigger de ultima_compra (que solo actúa sobre
    -- estados posteriores) no se dispara aquí; se actualiza explícitamente.
    UPDATE usuario_entidad
    SET ultima_compra = NOW()
    WHERE usuario_id = v_usuario_id;

    UPDATE informacion_entrega_entidad
    SET orden_orden_id = v_orden_id
    WHERE info_entrega_id = p_info_entrega_id;

    -- Debe ejecutarse después del trigger del carrito.
    DELETE FROM carrito_producto_entidad
    WHERE carrito_carrito_id = p_carrito_id;

    RAISE NOTICE
        'Checkout exitoso. Orden: %, Factura: %, Almacén: %, Distancia: % km, Total: %',
        v_orden_id,
        v_factura_id,
        v_almacen_nombre,
        v_distancia_km,
        v_precio_total;
END;
$$;
COMMIT;

-- ============================================================
-- VERIFICACIONES
-- ============================================================

-- La suma del inventario por almacenes debe coincidir con el stock global
-- antes de comenzar a procesar nuevas ventas.
SELECT
    p.producto_id,
    p.nombre_producto,
    p.stock AS stock_global,
    COALESCE(
        SUM(sap.stock_disponible),
        0
    )::INT AS stock_en_almacenes
FROM producto_entidad p
LEFT JOIN stock_almacen_producto_entidad sap
  ON sap.producto_id = p.producto_id
GROUP BY
    p.producto_id,
    p.nombre_producto,
    p.stock
HAVING p.stock <>
       COALESCE(
           SUM(sap.stock_disponible),
           0
       )::INT;

-- Categorías restringidas.
SELECT
    categoria_id,
    nombre_categoria,
    restringida_zona_residencial
FROM categoria_entidad
ORDER BY categoria_id;

-- Inventario por almacén.
SELECT
    a.nombre AS almacen,
    p.nombre_producto,
    sap.stock_disponible
FROM stock_almacen_producto_entidad sap
JOIN almacen_entidad a
  ON a.almacen_id = sap.almacen_id
JOIN producto_entidad p
  ON p.producto_id = sap.producto_id
ORDER BY
    a.almacen_id,
    p.producto_id;

-- ============================================================
-- CHOROPLETH COMUNA/DISTRITO — REGIÓN METROPOLITANA (52 comunas)
-- ============================================================
-- comuna_entidad guarda los polígonos reales (OpenStreetMap/Overpass) de
-- las 52 comunas de la Región Metropolitana que cubre el área comercial.
-- La carga de datos la hace ComunaOverpassLoader (Java, job de ejecución
-- única) — este script SOLO define el esquema, nunca inserta comunas.

CREATE TABLE IF NOT EXISTS comuna_entidad (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    distrito_postal VARCHAR(3)   NOT NULL CHECK (distrito_postal IN ('7xx', '8xx', '9xx')),
    geom            GEOMETRY(MultiPolygon, 4326) NOT NULL,
    CONSTRAINT uq_comuna_nombre UNIQUE (nombre)
);

CREATE INDEX IF NOT EXISTS idx_comuna_geom      ON comuna_entidad USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_comuna_distrito  ON comuna_entidad (distrito_postal);

-- Enlace de cada dirección de entrega a su comuna real (join espacial,
-- nunca por texto — ver comuna_id vs. comuna en informacion_entrega_entidad).
ALTER TABLE informacion_entrega_entidad
    ADD COLUMN IF NOT EXISTS comuna_id BIGINT;

COMMENT ON COLUMN informacion_entrega_entidad.comuna IS
    'DEPRECADO: texto libre no confiable (valores sucios como "Providencia-Test", "Santiago Centro"). Usar comuna_id.';
COMMENT ON COLUMN informacion_entrega_entidad.comuna_id IS
    'FK a comuna_entidad.id, calculada por join espacial ST_Contains(comuna_entidad.geom, ubicacion). NULL si la ubicación cae fuera de las 52 comunas de la RM.';

-- Prueba de checkout:
-- CALL procesar_checkout(<carrito_activo>, <entrega_del_mismo_usuario>, <pago_del_mismo_usuario>);

-- 1) Backfill de filas existentes (no-op en una BD recién creada, ya que
--    comuna_entidad recién se puebla al correr ComunaOverpassLoader).
UPDATE informacion_entrega_entidad ie
SET comuna_id = c.id
FROM comuna_entidad c
WHERE ST_Contains(c.geom, ie.ubicacion);

-- 2) Trigger para que direcciones nuevas (o con ubicación editada) queden
--    clasificadas automáticamente, mismo patrón que los demás triggers de
--    ordenes_entidad (trg_validar_cobertura_entrega, etc).
CREATE OR REPLACE FUNCTION trg_asignar_comuna_id()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.ubicacion IS NULL THEN
        NEW.comuna_id := NULL;
        RETURN NEW;
    END IF;

    SELECT c.id INTO NEW.comuna_id
    FROM comuna_entidad c
    WHERE ST_Contains(c.geom, NEW.ubicacion)
    LIMIT 1;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS asignar_comuna_id ON informacion_entrega_entidad;
CREATE TRIGGER asignar_comuna_id
    BEFORE INSERT OR UPDATE OF ubicacion ON informacion_entrega_entidad
    FOR EACH ROW EXECUTE FUNCTION trg_asignar_comuna_id();


-- 3) FK nullable: impide asociar a futuro una comuna que no sea una de las 52.
ALTER TABLE informacion_entrega_entidad
    DROP CONSTRAINT IF EXISTS fk_informacion_entrega_comuna;
ALTER TABLE informacion_entrega_entidad
    ADD CONSTRAINT fk_informacion_entrega_comuna
    FOREIGN KEY (comuna_id) REFERENCES comuna_entidad(id);

CREATE INDEX IF NOT EXISTS idx_informacion_entrega_comuna_id
    ON informacion_entrega_entidad (comuna_id);

-- ── ventas_por_comuna ──────────────────────────────────────
-- "Confirmado/pagado" = tiene factura_entidad (solo se crea al aprobar
-- una orden, y una orden aprobada ya no puede cancelarse — OrdenesServicio).
CREATE MATERIALIZED VIEW ventas_por_comuna AS
WITH base AS (
    SELECT
        c.id                AS comuna_id,
        c.nombre            AS nombre_comuna,
        c.distrito_postal,
        c.geom,
        COUNT(DISTINCT o.orden_id) FILTER (WHERE f.factura_id IS NOT NULL) AS total_pedidos,
        COALESCE(ROUND(SUM(f.precio_total) FILTER (WHERE f.factura_id IS NOT NULL)::NUMERIC, 2), 0) AS monto_total_ventas
    FROM comuna_entidad c
    LEFT JOIN informacion_entrega_entidad ie ON ie.comuna_id = c.id
    LEFT JOIN ordenes_entidad o             ON o.informacion_info_entrega_id = ie.info_entrega_id
    LEFT JOIN factura_entidad f             ON f.orden_orden_id = o.orden_id
    GROUP BY c.id, c.nombre, c.distrito_postal, c.geom
),
-- Terciles calculados SOLO sobre comunas con ventas > 0, para que las
-- comunas sin ventas no distorsionen el corte y siempre queden en SIN_VENTAS.
terciles AS (
    SELECT
        PERCENTILE_CONT(0.333) WITHIN GROUP (ORDER BY monto_total_ventas) AS p33,
        PERCENTILE_CONT(0.667) WITHIN GROUP (ORDER BY monto_total_ventas) AS p66
    FROM base
    WHERE monto_total_ventas > 0
)
SELECT
    b.comuna_id,
    b.nombre_comuna,
    b.distrito_postal,
    b.total_pedidos,
    b.monto_total_ventas,
    b.geom,
    (b.monto_total_ventas > 0) AS tiene_ventas,
    CASE
        WHEN b.monto_total_ventas <= 0    THEN 'SIN_VENTAS'
        WHEN b.monto_total_ventas <= t.p33 THEN 'BAJO'
        WHEN b.monto_total_ventas <= t.p66 THEN 'MEDIO'
        ELSE 'ALTO'
    END AS nivel_semaforo
FROM base b
CROSS JOIN terciles t;

CREATE UNIQUE INDEX idx_ventas_por_comuna_id   ON ventas_por_comuna (comuna_id);
CREATE INDEX        idx_ventas_por_comuna_geom ON ventas_por_comuna USING GIST (geom);

-- ── ventas_por_distrito ────────────────────────────────────
CREATE MATERIALIZED VIEW ventas_por_distrito AS
WITH agregado AS (
    SELECT
        distrito_postal,
        SUM(total_pedidos)      AS total_pedidos,
        SUM(monto_total_ventas) AS monto_total_ventas
    FROM ventas_por_comuna
    GROUP BY distrito_postal
),
geom_por_distrito AS (
    SELECT distrito_postal, ST_Union(geom) AS geom_union
    FROM comuna_entidad
    GROUP BY distrito_postal
),
terciles AS (
    SELECT
        PERCENTILE_CONT(0.333) WITHIN GROUP (ORDER BY monto_total_ventas) AS p33,
        PERCENTILE_CONT(0.667) WITHIN GROUP (ORDER BY monto_total_ventas) AS p66
    FROM agregado
    WHERE monto_total_ventas > 0
)
SELECT
    a.distrito_postal,
    a.total_pedidos,
    a.monto_total_ventas,
    g.geom_union,
    (a.monto_total_ventas > 0) AS tiene_ventas,
    CASE
        WHEN a.monto_total_ventas <= 0    THEN 'SIN_VENTAS'
        WHEN a.monto_total_ventas <= t.p33 THEN 'BAJO'
        WHEN a.monto_total_ventas <= t.p66 THEN 'MEDIO'
        ELSE 'ALTO'
    END AS nivel_semaforo
FROM agregado a
JOIN geom_por_distrito g ON g.distrito_postal = a.distrito_postal
CROSS JOIN terciles t;

CREATE UNIQUE INDEX idx_ventas_por_distrito_id   ON ventas_por_distrito (distrito_postal);
CREATE INDEX        idx_ventas_por_distrito_geom ON ventas_por_distrito USING GIST (geom_union);

-- ============================================================
-- VALIDACIÓN DE COBERTURA AL CREAR O EDITAR UNA DIRECCIÓN
-- ============================================================
-- Se define después de cargar los datos iniciales porque los INSERT de
-- prueba crean primero la dirección y luego asignan su geometría.
-- Desde este punto, toda dirección nueva debe incluir una ubicación válida
-- dentro de una zona de cobertura activa.

CREATE OR REPLACE FUNCTION trg_validar_cobertura_direccion()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.ubicacion IS NULL THEN
        RAISE EXCEPTION
            'No se puede guardar la dirección sin coordenadas geográficas';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM zona_cobertura_entidad z
        WHERE z.activa = TRUE
          AND ST_Covers(z.geom, NEW.ubicacion)
    ) THEN
        RAISE EXCEPTION
            'No se puede guardar la dirección: está fuera del área de cobertura';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS validar_cobertura_direccion
ON informacion_entrega_entidad;

CREATE TRIGGER validar_cobertura_direccion
    BEFORE INSERT OR UPDATE OF ubicacion
    ON informacion_entrega_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_validar_cobertura_direccion();

-- ── Refresco ────────────────────────────────────────────────
-- Orden importa: ventas_por_distrito depende de ventas_por_comuna.
-- Se dispara automáticamente cada 6h (LogisticaMapaScheduler) o a mano
-- vía POST /api/logistica/mapa/refrescar.
CREATE OR REPLACE FUNCTION refrescar_ventas_por_comuna_y_distrito()
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY ventas_por_comuna;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ventas_por_distrito;
END;
$$;

-- ============================================================
-- UNIDADES VECINALES (INE) — base para Zona Residencial Protegida
-- Fuente: Ministerio de Desarrollo Social y Familia, shapefile
-- "Unidades Vecinales", actualizado a agosto 2024.
-- Cobertura: Región Metropolitana.
-- Unidades vecinales sin comuna de cobertura asociada (zonas
-- "SIN DEF COMUNAL", territorio rural) son excluidas deliberadamente
-- por no aplicar al área de servicio de la empresa.
--
-- Carga vía UnidadVecinalGeoJsonLoader (Java, job de ejecución puntual
-- disparado por POST /api/admin/unidades-vecinales/cargar) a partir de
-- resources/data/unidades_vecinales_rm.geojson. Este script SOLO
-- define el esquema, nunca inserta unidades vecinales.
-- ============================================================
CREATE TABLE IF NOT EXISTS unidad_vecinal_entidad (
    id                 BIGSERIAL PRIMARY KEY,
    comuna_id          BIGINT NOT NULL REFERENCES comuna_entidad(id),
    codigo_uv          VARCHAR(20),
    nombre_uv          VARCHAR(255) NOT NULL,
    geom               geometry(Polygon, 4326) NOT NULL,
    es_zona_protegida  BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (comuna_id, codigo_uv)
);

COMMENT ON TABLE unidad_vecinal_entidad IS
    'Fuente: Ministerio de Desarrollo Social y Familia, shapefile "Unidades Vecinales", actualizado a agosto 2024. Cobertura: Región Metropolitana. Unidades vecinales sin comuna de cobertura asociada (zonas "SIN DEF COMUNAL", territorio rural) son excluidas deliberadamente por no aplicar al área de servicio de la empresa.';

CREATE INDEX IF NOT EXISTS idx_unidad_vecinal_geom
    ON unidad_vecinal_entidad USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_unidad_vecinal_comuna
    ON unidad_vecinal_entidad (comuna_id);

-- ============================================================
-- ZONA DE COBERTURA — reemplazo del polígono aproximado por
-- ST_Union real de las 52 comunas de comuna_entidad.
-- trg_validar_cobertura_entrega() ya soporta múltiples filas
-- (EXISTS ... WHERE activa = TRUE), así que en vez de sobreescribir
-- la fila original se desactiva (respaldo recuperable con un simple
-- UPDATE activa=true, sin transcribir WKT a mano) y se inserta una
-- fila nueva activa con la unión real.
-- ============================================================
UPDATE zona_cobertura_entidad
SET activa = FALSE,
    nombre = 'Región Metropolitana (respaldo — polígono aproximado, reemplazado por ST_Union real el 2026-07-19)'
WHERE zona_id = 1 AND activa = TRUE;

INSERT INTO zona_cobertura_entidad (nombre, geom, activa)
SELECT 'Región Metropolitana (unión real de las 52 comunas)', ST_Union(geom), TRUE
FROM comuna_entidad
WHERE NOT EXISTS (
    SELECT 1 FROM zona_cobertura_entidad
    WHERE nombre = 'Región Metropolitana (unión real de las 52 comunas)'
);