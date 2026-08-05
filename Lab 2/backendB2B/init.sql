-- ═══════════════════════════════════════════════════════════════
-- init.sql  ·  B2B E-Commerce Platform
-- Base de datos PostgreSQL con PostGIS
-- ═══════════════════════════════════════════════════════════════

-- ─── EXTENSIÓN POSTGIS ───────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS postgis;

-- ─── 1. LIMPIEZA DEL ESQUEMA ANTERIOR ────────────────────────
DROP MATERIALIZED VIEW IF EXISTS ventas_por_distrito          CASCADE;
DROP MATERIALIZED VIEW IF EXISTS ventas_por_comuna            CASCADE;
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_geo_por_distrito   CASCADE;
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_por_comuna         CASCADE;
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_mensuales_por_categoria CASCADE;

DROP TABLE IF EXISTS unidad_vecinal_entidad                   CASCADE;
DROP TABLE IF EXISTS stock_almacen_producto_entidad           CASCADE;
DROP TABLE IF EXISTS zona_residencial_protegida_entidad       CASCADE;
DROP TABLE IF EXISTS distrito_postal_entidad                  CASCADE;
DROP TABLE IF EXISTS cobertura_empresa_entidad                CASCADE;
DROP TABLE IF EXISTS configuracion_envio_entidad              CASCADE;
DROP TABLE IF EXISTS audit_ordenes                            CASCADE;
DROP TABLE IF EXISTS factura_item_entidad                     CASCADE;
DROP TABLE IF EXISTS factura_entidad                          CASCADE;
DROP TABLE IF EXISTS carrito_producto_entidad                 CASCADE;
DROP TABLE IF EXISTS informacion_entrega_entidad              CASCADE;
DROP TABLE IF EXISTS ordenes_entidad                          CASCADE;
DROP TABLE IF EXISTS carrito_entidad                          CASCADE;
DROP TABLE IF EXISTS producto_entidad                         CASCADE;
DROP TABLE IF EXISTS categoria_entidad                        CASCADE;
DROP TABLE IF EXISTS datos_pago_entidad                       CASCADE;
DROP TABLE IF EXISTS usuario_entidad                          CASCADE;
DROP TABLE IF EXISTS almacen_entidad                          CASCADE;
DROP TABLE IF EXISTS zona_cobertura_entidad                   CASCADE;
DROP TABLE IF EXISTS comuna_entidad                           CASCADE;

-- ─── 2. TABLAS ────────────────────────────────────────────────

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
    fecha_expiracion VARCHAR(255),
    activo           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE categoria_entidad (
    categoria_id                   SERIAL PRIMARY KEY,
    nombre_categoria               VARCHAR(255),
    estado_categoria               BOOLEAN NOT NULL DEFAULT TRUE,
    restringida_zona_residencial   BOOLEAN NOT NULL DEFAULT FALSE
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

-- Tabla geoespacial: almacenes
CREATE TABLE almacen_entidad (
    almacen_id  SERIAL PRIMARY KEY,
    nombre      VARCHAR(255) NOT NULL,
    direccion   VARCHAR(255),
    ubicacion   GEOMETRY(Point, 4326) NOT NULL
);

-- Zona de cobertura de servicio
CREATE TABLE zona_cobertura_entidad (
    zona_id   SERIAL PRIMARY KEY,
    nombre    VARCHAR(255) NOT NULL,
    geom      GEOMETRY(Polygon, 4326) NOT NULL,
    activa    BOOLEAN NOT NULL DEFAULT TRUE
);

-- Comunas de la Región Metropolitana
CREATE TABLE comuna_entidad (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    distrito_postal VARCHAR(3)   NOT NULL CHECK (distrito_postal IN ('7xx', '8xx', '9xx')),
    geom            GEOMETRY(MultiPolygon, 4326) NOT NULL,
    CONSTRAINT uq_comuna_nombre UNIQUE (nombre)
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
    activa                BOOLEAN NOT NULL DEFAULT TRUE,
    ubicacion             GEOMETRY(Point, 4326),
    comuna                VARCHAR(100),
    comuna_id             BIGINT REFERENCES comuna_entidad(id)
);

CREATE TABLE ordenes_entidad (
    orden_id                   SERIAL PRIMARY KEY,
    carrito_carrito_id         BIGINT REFERENCES carrito_entidad(carrito_id),
    informacion_info_entrega_id BIGINT REFERENCES informacion_entrega_entidad(info_entrega_id),
    fecha_orden                TIMESTAMP,
    estado                     VARCHAR(255),
    almacen_asignado_id        BIGINT REFERENCES almacen_entidad(almacen_id),
    distancia_envio_km         NUMERIC(12,3)
);

-- FK circular: informacion_entrega -> ordenes
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
    iva             REAL,
    costo_envio     NUMERIC(14,2) NOT NULL DEFAULT 0
);

CREATE TABLE factura_item_entidad (
    factura_item_id   BIGSERIAL PRIMARY KEY,
    factura_id        BIGINT NOT NULL REFERENCES factura_entidad(factura_id) ON DELETE CASCADE,
    producto_id       BIGINT NOT NULL REFERENCES producto_entidad(producto_id),
    cantidad          INT NOT NULL,
    precio_unitario   REAL NOT NULL,
    CONSTRAINT ck_cantidad    CHECK (cantidad > 0),
    CONSTRAINT ck_precio      CHECK (precio_unitario > 0)
);

-- Tabla de auditoría de órdenes
CREATE TABLE audit_ordenes (
    audit_id        BIGSERIAL PRIMARY KEY,
    orden_id        INT NOT NULL,
    estado_anterior VARCHAR(255),
    estado_nuevo    VARCHAR(255),
    usuario_id      BIGINT,
    fecha_cambio    TIMESTAMP DEFAULT NOW(),
    descripcion     TEXT
);

-- Configuración global de tarifa de envío (valor por km)
CREATE TABLE configuracion_envio_entidad (
    config_id BIGSERIAL PRIMARY KEY,
    valor_km  NUMERIC(14,2) NOT NULL DEFAULT 0
);

-- Stock por almacén
CREATE TABLE stock_almacen_producto_entidad (
    stock_almacen_id BIGSERIAL PRIMARY KEY,
    almacen_id       BIGINT NOT NULL REFERENCES almacen_entidad(almacen_id) ON DELETE CASCADE,
    producto_id      BIGINT NOT NULL REFERENCES producto_entidad(producto_id) ON DELETE CASCADE,
    stock_disponible INT NOT NULL DEFAULT 0,
    CONSTRAINT ux_stock_almacen_producto    UNIQUE (almacen_id, producto_id),
    CONSTRAINT ck_stock_almacen_disponible  CHECK  (stock_disponible >= 0)
);

-- Zonas residenciales protegidas
CREATE TABLE zona_residencial_protegida_entidad (
    zona_id     BIGSERIAL PRIMARY KEY,
    nombre_zona VARCHAR(255) NOT NULL UNIQUE,
    activa      BOOLEAN NOT NULL DEFAULT TRUE,
    poligono    GEOMETRY(Polygon, 4326) NOT NULL
);

-- Unidades vecinales (pobladas por UnidadVecinalGeoJsonLoader)
CREATE TABLE unidad_vecinal_entidad (
    id                 BIGSERIAL PRIMARY KEY,
    comuna_id          BIGINT NOT NULL REFERENCES comuna_entidad(id),
    codigo_uv          VARCHAR(20),
    nombre_uv          VARCHAR(255) NOT NULL,
    geom               GEOMETRY(Polygon, 4326) NOT NULL,
    es_zona_protegida  BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (comuna_id, codigo_uv)
);

COMMENT ON TABLE unidad_vecinal_entidad IS
    'Fuente: Ministerio de Desarrollo Social y Familia, shapefile "Unidades Vecinales", actualizado a agosto 2024.';
COMMENT ON COLUMN informacion_entrega_entidad.comuna IS
    'DEPRECADO: texto libre. Usar comuna_id (FK espacial a comuna_entidad).';

-- ─── 3. ÍNDICES ──────────────────────────────────────────────

CREATE INDEX idx_producto_sku             ON producto_entidad(sku);
CREATE INDEX idx_usuario_id               ON usuario_entidad(usuario_id);
CREATE INDEX idx_carrito_usuario          ON carrito_entidad(carrito_usuario_id);
CREATE INDEX idx_carrito_producto_carrito ON carrito_producto_entidad(carrito_carrito_id);
CREATE INDEX idx_carrito_producto_producto ON carrito_producto_entidad(producto_producto_id);
CREATE INDEX idx_orden_carrito            ON ordenes_entidad(carrito_carrito_id);
CREATE INDEX idx_orden_info_entrega       ON ordenes_entidad(informacion_info_entrega_id);
CREATE INDEX idx_factura_usuario          ON factura_entidad(usuario_usuario);
CREATE INDEX idx_factura_orden            ON factura_entidad(orden_orden_id);
CREATE INDEX idx_producto_categoria       ON producto_entidad(categoria_categoria_id);
CREATE INDEX idx_informacion_entrega_usuario ON informacion_entrega_entidad(usuario_usuario);
CREATE INDEX idx_informacion_entrega_orden   ON informacion_entrega_entidad(orden_orden_id);
CREATE INDEX idx_informacion_entrega_comuna_id ON informacion_entrega_entidad(comuna_id);

-- Índices espaciales GIST
CREATE INDEX idx_informacion_entrega_ubicacion ON informacion_entrega_entidad USING GIST (ubicacion);
CREATE INDEX idx_almacen_ubicacion             ON almacen_entidad USING GIST (ubicacion);
CREATE INDEX idx_zona_cobertura_geom           ON zona_cobertura_entidad USING GIST (geom);
CREATE INDEX idx_zona_residencial_poligono_gist ON zona_residencial_protegida_entidad USING GIST (poligono);
CREATE INDEX idx_stock_almacen_producto        ON stock_almacen_producto_entidad (almacen_id, producto_id);
CREATE INDEX idx_comuna_geom                   ON comuna_entidad USING GIST (geom);
CREATE INDEX idx_comuna_distrito               ON comuna_entidad (distrito_postal);
CREATE INDEX idx_unidad_vecinal_geom           ON unidad_vecinal_entidad USING GIST (geom);
CREATE INDEX idx_unidad_vecinal_comuna         ON unidad_vecinal_entidad (comuna_id);

-- ─── 4. PROCEDIMIENTOS Y FUNCIONES ───────────────────────────

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

-- ─── 5. TRIGGERS ─────────────────────────────────────────────

-- Trigger: gestionar stock al cambiar estado del carrito
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
        PERFORM ajustar_reserva_por_carrito(NEW.carrito_id, 'LIBERAR');

    ELSIF OLD.estado = 'ABANDONADO'
          AND NEW.estado = 'ACTIVO' THEN
        PERFORM ajustar_reserva_por_carrito(NEW.carrito_id, 'RESERVAR');

    ELSIF OLD.estado = 'ACTIVO'
          AND NEW.estado IN ('PAGADO', 'ORDENADO') THEN
        PERFORM ajustar_reserva_por_carrito(NEW.carrito_id, 'CONSUMIR');
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS carrito_estado_cambio ON carrito_entidad;
CREATE TRIGGER carrito_estado_cambio
    AFTER UPDATE OF estado ON carrito_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_carrito_estado_cambio();

-- Trigger: prevenir sobreventa al crear una orden
CREATE OR REPLACE FUNCTION trg_prevenir_sobreventa()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM carrito_producto_entidad
        WHERE carrito_carrito_id = NEW.carrito_carrito_id
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: el carrito está vacío';
    END IF;

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
        WHERE COALESCE(sap.stock_disponible, 0) < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION
            'No se puede crear la orden: stock insuficiente en el almacén asignado';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS prevenir_sobreventa ON ordenes_entidad;
CREATE TRIGGER prevenir_sobreventa
    BEFORE INSERT ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_prevenir_sobreventa();

-- Trigger: actualizar última compra
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

-- Trigger: auditar cambios en órdenes
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
        orden_id, estado_anterior, estado_nuevo,
        usuario_id, fecha_cambio, descripcion
    )
    VALUES (
        NEW.orden_id, v_estado_anterior, NEW.estado,
        v_usuario_id, NOW(), v_descripcion
    );

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS auditar_cambios_orden ON ordenes_entidad;
CREATE TRIGGER auditar_cambios_orden
    AFTER INSERT OR UPDATE ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_auditar_cambios_orden();

-- Trigger: validar cobertura geográfica al crear una orden
CREATE OR REPLACE FUNCTION trg_validar_cobertura_entrega()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ubicacion GEOMETRY(Point, 4326);
BEGIN
    SELECT ie.ubicacion
    INTO v_ubicacion
    FROM informacion_entrega_entidad ie
    WHERE ie.info_entrega_id = NEW.informacion_info_entrega_id;

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

DROP TRIGGER IF EXISTS validar_cobertura_entrega ON ordenes_entidad;
CREATE TRIGGER validar_cobertura_entrega
    BEFORE INSERT OR UPDATE OF informacion_info_entrega_id ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_validar_cobertura_entrega();

-- Trigger: validar categorías restringidas en zonas residenciales
CREATE OR REPLACE FUNCTION trg_validar_categoria_restringida_por_zona()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_ubicacion GEOMETRY(Point, 4326);
BEGIN
    SELECT ie.ubicacion
    INTO v_ubicacion
    FROM informacion_entrega_entidad ie
    WHERE ie.info_entrega_id = NEW.informacion_info_entrega_id;

    IF v_ubicacion IS NULL THEN
        RAISE EXCEPTION
            'La entrega no tiene ubicación geoespacial definida';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM carrito_producto_entidad cp
        JOIN producto_entidad p   ON p.producto_id  = cp.producto_producto_id
        JOIN categoria_entidad c  ON c.categoria_id = p.categoria_categoria_id
        WHERE cp.carrito_carrito_id = NEW.carrito_carrito_id
          AND c.restringida_zona_residencial = TRUE
          AND EXISTS (
              SELECT 1 FROM unidad_vecinal_entidad uv
              WHERE uv.es_zona_protegida = TRUE
                AND ST_Covers(uv.geom, v_ubicacion)
          )
    ) THEN
        RAISE EXCEPTION
            'Orden bloqueada: contiene categorías restringidas para una zona residencial protegida';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS validar_categoria_restringida_por_zona ON ordenes_entidad;
CREATE TRIGGER validar_categoria_restringida_por_zona
    BEFORE INSERT OR UPDATE OF carrito_carrito_id, informacion_info_entrega_id
    ON ordenes_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_validar_categoria_restringida_por_zona();

-- Trigger: asignar comuna_id a partir de join espacial
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


-- ─── 6. PROCEDIMIENTO CHECKOUT ───────────────────────────────

DROP PROCEDURE IF EXISTS procesar_checkout(BIGINT, BIGINT);
DROP PROCEDURE IF EXISTS procesar_checkout(BIGINT, BIGINT, BIGINT);

CREATE OR REPLACE PROCEDURE procesar_checkout(
    p_carrito_id      BIGINT,
    p_info_entrega_id BIGINT,
    p_datos_pago_id   BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_usuario_id         BIGINT;
    v_ubicacion_entrega  GEOMETRY(Point, 4326);
    v_almacen_id         BIGINT;
    v_almacen_nombre     VARCHAR(255);
    v_distancia_km       NUMERIC(12,3);
    v_candidato          RECORD;
    v_total_neto         NUMERIC(14,2);
    v_iva                NUMERIC(14,2);
    v_precio_total       NUMERIC(14,2);
    v_subtotal_productos NUMERIC(14,2);
    v_valor_km           NUMERIC(14,2);
    v_costo_envio        NUMERIC(14,2);
    v_orden_id           INT;
    v_factura_id         BIGINT;
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

    SELECT c.carrito_usuario_id
    INTO v_usuario_id
    FROM carrito_entidad c
    WHERE c.carrito_id = p_carrito_id
      AND c.estado = 'ACTIVO'
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Carrito % no existe o no está ACTIVO', p_carrito_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
    ) THEN
        RAISE EXCEPTION 'El carrito % está vacío', p_carrito_id;
    END IF;

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
            'La entrega % no tiene coordenadas geográficas', p_info_entrega_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM datos_pago_entidad dp
        WHERE dp.datos_pago_id = p_datos_pago_id
          AND dp.usuario_usuario = v_usuario_id
    ) THEN
        RAISE EXCEPTION
            'Los datos de pago % no existen o no pertenecen al usuario',
            p_datos_pago_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM zona_cobertura_entidad z
        WHERE z.activa = TRUE
          AND ST_Covers(z.geom, v_ubicacion_entrega)
    ) THEN
        RAISE EXCEPTION
            'La dirección de entrega está fuera del área de cobertura';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM carrito_producto_entidad cp
        JOIN producto_entidad p   ON p.producto_id  = cp.producto_producto_id
        JOIN categoria_entidad c  ON c.categoria_id = p.categoria_categoria_id
        WHERE cp.carrito_carrito_id = p_carrito_id
          AND c.restringida_zona_residencial = TRUE
          AND EXISTS (
              SELECT 1 FROM unidad_vecinal_entidad uv
              WHERE uv.es_zona_protegida = TRUE
                AND ST_Covers(uv.geom, v_ubicacion_entrega)
          )
    ) THEN
        RAISE EXCEPTION
            'No se permiten categorías restringidas en esta zona residencial';
    END IF;

    PERFORM p.producto_id
    FROM producto_entidad p
    JOIN (
        SELECT cp.producto_producto_id,
               SUM(cp.unidad_producto)::INT AS cantidad
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
        GROUP BY cp.producto_producto_id
    ) solicitado ON solicitado.producto_producto_id = p.producto_id
    ORDER BY p.producto_id
    FOR UPDATE OF p;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT cp.producto_producto_id,
                   SUM(cp.unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad cp
            WHERE cp.carrito_carrito_id = p_carrito_id
            GROUP BY cp.producto_producto_id
        ) solicitado
        JOIN producto_entidad p ON p.producto_id = solicitado.producto_producto_id
        WHERE p.activo IS NOT TRUE
           OR p.stock          < solicitado.cantidad
           OR p.stock_reservado < solicitado.cantidad
    ) THEN
        RAISE EXCEPTION 'Stock global o reservado insuficiente';
    END IF;

    FOR v_candidato IN
        SELECT a.almacen_id, a.nombre,
               ROUND(
                   (ST_Distance(a.ubicacion::geography, v_ubicacion_entrega::geography) / 1000.0)::NUMERIC,
                   3
               ) AS distancia_km
        FROM almacen_entidad a
        ORDER BY a.ubicacion <-> v_ubicacion_entrega
    LOOP
        PERFORM sap.stock_almacen_id
        FROM stock_almacen_producto_entidad sap
        JOIN (
            SELECT cp.producto_producto_id,
                   SUM(cp.unidad_producto)::INT AS cantidad
            FROM carrito_producto_entidad cp
            WHERE cp.carrito_carrito_id = p_carrito_id
            GROUP BY cp.producto_producto_id
        ) solicitado ON solicitado.producto_producto_id = sap.producto_id
        WHERE sap.almacen_id = v_candidato.almacen_id
        ORDER BY sap.producto_id
        FOR UPDATE OF sap;

        IF NOT EXISTS (
            SELECT 1
            FROM (
                SELECT cp.producto_producto_id,
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
            v_almacen_id     := v_candidato.almacen_id;
            v_almacen_nombre := v_candidato.nombre;
            v_distancia_km   := v_candidato.distancia_km;
            EXIT;
        END IF;
    END LOOP;

    IF v_almacen_id IS NULL THEN
        RAISE EXCEPTION
            'No existe un almacén con stock suficiente para el carrito %', p_carrito_id;
    END IF;

    SELECT ROUND(COALESCE(SUM(cp.unidad_producto * p.precio), 0)::NUMERIC, 2)
    INTO v_subtotal_productos
    FROM carrito_producto_entidad cp
    JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
    WHERE cp.carrito_carrito_id = p_carrito_id;

    IF v_subtotal_productos <= 0 THEN
        RAISE EXCEPTION 'El total del carrito es inválido';
    END IF;

    SELECT valor_km INTO v_valor_km
    FROM configuracion_envio_entidad
    ORDER BY config_id LIMIT 1;

    v_valor_km    := COALESCE(v_valor_km, 0);
    v_costo_envio := ROUND(v_valor_km * COALESCE(v_distancia_km, 0), 2);

    v_precio_total := v_subtotal_productos + v_costo_envio;
    v_total_neto   := ROUND(v_precio_total / 1.19, 2);
    v_iva          := ROUND(v_precio_total - v_total_neto, 2);

    INSERT INTO ordenes_entidad (
        carrito_carrito_id, informacion_info_entrega_id,
        fecha_orden, estado, almacen_asignado_id, distancia_envio_km
    )
    VALUES (
        p_carrito_id, p_info_entrega_id,
        NOW(), 'PENDIENTE', v_almacen_id, v_distancia_km
    )
    RETURNING orden_id INTO v_orden_id;

    INSERT INTO factura_entidad (
        usuario_usuario, datos_pago_id, orden_orden_id,
        precio_total, fecha_emision, total_neto, iva, costo_envio
    )
    VALUES (
        v_usuario_id, p_datos_pago_id, v_orden_id,
        v_precio_total, NOW(), v_total_neto, v_iva, v_costo_envio
    )
    RETURNING factura_id INTO v_factura_id;

    INSERT INTO factura_item_entidad (factura_id, producto_id, cantidad, precio_unitario)
    SELECT v_factura_id, cp.producto_producto_id,
           SUM(cp.unidad_producto)::INT, p.precio
    FROM carrito_producto_entidad cp
    JOIN producto_entidad p ON p.producto_id = cp.producto_producto_id
    WHERE cp.carrito_carrito_id = p_carrito_id
    GROUP BY cp.producto_producto_id, p.precio;

    UPDATE stock_almacen_producto_entidad sap
    SET stock_disponible = sap.stock_disponible - solicitado.cantidad
    FROM (
        SELECT cp.producto_producto_id,
               SUM(cp.unidad_producto)::INT AS cantidad
        FROM carrito_producto_entidad cp
        WHERE cp.carrito_carrito_id = p_carrito_id
        GROUP BY cp.producto_producto_id
    ) solicitado
    WHERE sap.almacen_id = v_almacen_id
      AND sap.producto_id = solicitado.producto_producto_id;

    UPDATE carrito_entidad
    SET estado = 'PAGADO', ultima_actualizacion = NOW()
    WHERE carrito_id = p_carrito_id;

    UPDATE usuario_entidad
    SET ultima_compra = NOW()
    WHERE usuario_id = v_usuario_id;

    UPDATE informacion_entrega_entidad
    SET orden_orden_id = v_orden_id
    WHERE info_entrega_id = p_info_entrega_id;

    DELETE FROM carrito_producto_entidad
    WHERE carrito_carrito_id = p_carrito_id;

    RAISE NOTICE
        'Checkout exitoso. Orden: %, Factura: %, Almacén: %, Distancia: % km, Total: %',
        v_orden_id, v_factura_id, v_almacen_nombre, v_distancia_km, v_precio_total;
END;
$$;

-- Función: refrescar vistas materializadas de ventas
CREATE OR REPLACE FUNCTION refrescar_ventas_por_comuna_y_distrito()
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY ventas_por_comuna;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ventas_por_distrito;
END;
$$;

-- ═══════════════════════════════════════════════════════════════
-- 7. DATOS INICIALES
-- ═══════════════════════════════════════════════════════════════

-- ── 7.1 Zona de cobertura ─────────────────────────────────────
-- Polígono real aproximado de la Región Metropolitana (el área total)
-- que se mantiene activo (activa = true).
INSERT INTO zona_cobertura_entidad (nombre, geom, activa) VALUES
(
    'Región Metropolitana',
    ST_GeomFromText(
        'POLYGON((-71.80 -32.80, -69.50 -32.80, -69.50 -34.50, -71.80 -34.50, -71.80 -32.80))',
        4326
    ),
    TRUE
);

-- ── 7.2 Comunas ───────────────────────────────────────────────
-- Poblamos las comunas de la RM con polígonos aproximados para
-- que la aplicación funcione inmediatamente y las vistas de
-- ventas muestren los mapas choropleth de Leaflet de forma consistente.
-- (Los datos de comunas reales se cargarán automáticamente desde Overpass API en segundo plano)

-- ── 7.3 Unidades Vecinales (Zonas de restricción / protegidas) ─
-- Poblamos directamente unidades vecinales asociadas a las comunas.
-- Marcamos varias como es_zona_protegida = TRUE para que se visualicen
-- de inmediato como zonas residenciales protegidas/restricciones en el mapa.
-- (Los datos de unidades vecinales reales se cargarán automáticamente desde el archivo geojson al iniciar la app)

-- ── 7.4 Almacenes ─────────────────────────────────────────────
INSERT INTO almacen_entidad (nombre, direccion, ubicacion) VALUES
('Almacén Central Santiago',  'Av. Libertador Bernardo O''Higgins 1449, Santiago',
    ST_SetSRID(ST_MakePoint(-70.6670, -33.4569), 4326)),
('Almacén Sur Puente Alto',   'Av. Concha y Toro 3300, Puente Alto',
    ST_SetSRID(ST_MakePoint(-70.5757, -33.6118), 4326)),
('Almacén Poniente Maipú',    'Av. Pajaritos 3050, Maipú',
    ST_SetSRID(ST_MakePoint(-70.7580, -33.4990), 4326));

-- ── 7.5 Configuración de envío ────────────────────────────────
INSERT INTO configuracion_envio_entidad (valor_km) VALUES (500);

-- ── 7.6 Categorías ───────────────────────────────────────────
INSERT INTO categoria_entidad (nombre_categoria, restringida_zona_residencial) VALUES
('Equipos de Computación',         FALSE),  -- 1
('Mobiliario de Oficina',          FALSE),  -- 2
('Insumos Tecnológicos',           TRUE),   -- 3  ← restringida
('Redes y Conectividad',           FALSE),  -- 4
('Licencias de Software',          FALSE),  -- 5
('Almacenamiento y Servidores',    FALSE),  -- 6
('Audio y Videoconferencia',       FALSE),  -- 7
('Seguridad Electrónica',          TRUE),   -- 8  ← restringida
('Químicos Peligrosos',            TRUE);   -- 9  ← restringida

-- ── 7.7 Usuarios ─────────────────────────────────────────────
-- Contraseñas hasheadas con BCrypt
INSERT INTO usuario_entidad (nombre_usuario, correo, contrasena, ultima_compra, rut_empresa, rol) VALUES
('Juan Perez',      'jperez@techsolutions.cl',       '$2a$12$Ue51OzMXnpiqWLxMPaAKjOPSnfs1267HJakRsYNn1jUIlzKopwL/q', '2023-10-01 10:00:00', '76.123.456-7', 'CLIENTE'),  -- 1
('Maria Lopez',     'mlopez@techsolutions.cl',       '$2a$12$D4WkfIGsvL1tXLgfxuDp0uNaKuhEremwCRTYsyHWHlHd/Ac/PYVcC', '2023-11-15 14:20:00', '76.123.456-7', 'CLIENTE'),  -- 2
('Carlos Ruiz',     'admin@ecommerceb2b.cl',         '$2a$12$Z0rQJcFIeZcX6.XwosKuqOI.Y.kpfY/hdXxtA127heFc6.xyZXz4q', '2022-01-10 09:00:00', '77.000.111-K', 'ADMIN'),    -- 3
('Ana Soto',        'aso@construccionesdelnorte.cl', '$2a$12$QPGq3X4zz.Fe8Sl1A/UkMOVESAEDlNcUnq2d9MyWHhQzRAFW1hrCm', '2024-01-20 16:45:00', '80.555.444-2', 'CLIENTE'),  -- 4
('Luis Fernandez',  'lfernandez@logisticadelsur.cl', '$2a$12$IZ6UqglJaxjyqbiQXz4njuTvZPDz95.Nfs0L6AOg.5Kcj9hgGZWEO', NULL,                  '90.222.333-1', 'CLIENTE'),  -- 5
('Pedro Morales',   'pmo@desarrolloweb.cl',          '$2a$12$jxYs5vcAixX9biOL/h91k.kIy0yfJn3PqlbSWHi3cnCUzfEUTRMBW', '2024-03-10 11:30:00', '78.333.444-5', 'CLIENTE'),  -- 6
('Sofia Vergara',   'svergara@saludintegral.cl',     '$2a$12$Uh1qdcJFW5JhpNrxPFA60.TtPpWx6yVnRvEvvX5woy5mim7tlmlF6', '2024-02-28 09:15:00', '79.111.222-3', 'CLIENTE'),  -- 7
('Diego Vargas',    'dvar@adminb2b.cl',              '$2a$12$NQUmXqv6moflf/rSszbz6up2GF9UpY0dLgKGDlquH0G6Dqek7dY5S', '2023-05-05 10:00:00', '77.000.111-K', 'ADMIN'),    -- 8
('Camila Rojas',    'crojas@mineraandina.cl',        '$2a$12$67Wz0mNx8GYBYWUXwKLy3.2TXq8mQfjATolERMawVZYPXywzQmNi6', NULL,                  '88.999.000-4', 'CLIENTE'),  -- 9
('Javier Tello',    'jtello@educacionfuturo.cl',     '$2a$12$vlx4hMEyKHW9nT8cHKQmmeV6HHlvB7iE1oJffk0aDpXDsg2oSrH.y', '2024-04-12 15:45:00', '70.888.777-6', 'CLIENTE'),  -- 10
('Isabel Castillo', 'icastillo@industriasnorte.cl',  '$2a$12$Ue51OzMXnpiqWLxMPaAKjOPSnfs1267HJakRsYNn1jUIlzKopwL/q', '2024-06-01 09:00:00', '85.111.222-3', 'CLIENTE'),  -- 11
('Ricardo Ponce',   'rponce@exportadora.cl',         '$2a$12$D4WkfIGsvL1tXLgfxuDp0uNaKuhEremwCRTYsyHWHlHd/Ac/PYVcC', '2024-07-15 14:00:00', '86.222.333-4', 'CLIENTE'),  -- 12
('Valentina Mora',  'vmora@consultora.cl',           '$2a$12$Z0rQJcFIeZcX6.XwosKuqOI.Y.kpfY/hdXxtA127heFc6.xyZXz4q', '2024-08-20 11:00:00', '87.333.444-5', 'CLIENTE'),  -- 13
('Andrés Salinas',  'asalinas@tecnologia.cl',        '$2a$12$QPGq3X4zz.Fe8Sl1A/UkMOVESAEDlNcUnq2d9MyWHhQzRAFW1hrCm', '2024-09-10 16:00:00', '89.444.555-6', 'CLIENTE'),  -- 14
('Patricia Lagos',  'pmagos@distribuidora.cl',       '$2a$12$IZ6UqglJaxjyqbiQXz4njuTvZPDz95.Nfs0L6AOg.5Kcj9hgGZWEO', '2024-10-05 10:30:00', '91.555.666-7', 'CLIENTE');  -- 15

-- ── 7.8 Productos ────────────────────────────────────────────
INSERT INTO producto_entidad (categoria_categoria_id, nombre_producto, descripcion, precio, stock, sku, activo) VALUES
(1, 'Notebook Empresarial Pro 15"',      'Notebook Intel Core i7, 16GB RAM, 512GB SSD',           1200000.0,  500, 'SKU-COMP-001', TRUE),   -- 1
(1, 'Monitor 27" 4K',                    'Monitor IPS orientable para trabajo prolongado',           350000.0,  600, 'SKU-COMP-002', TRUE),   -- 2
(1, 'Mini PC Corporativo',               'Desktop compacto Intel Core i5, 8GB RAM',                 600000.0,  300, 'SKU-COMP-003', TRUE),   -- 3
(2, 'Silla Ergonómica Premium',          'Silla de oficina ergonómica avanzada, soporte lumbar',    180000.0, 1000, 'SKU-OFI-001',  TRUE),   -- 4
(2, 'Escritorio Eléctrico Ajustable',    'Escritorio con ajuste de altura motorizado',              450000.0,  200, 'SKU-OFI-002',  TRUE),   -- 5
(3, 'Set Toners Impresora Láser',        'Pack de 4 tóners CMYK alto rendimiento',                   85000.0, 2000, 'SKU-INS-001',  TRUE),   -- 6
(4, 'Router Empresarial Wi-Fi 6',        'Router VPN Gigabit Dual-WAN',                             150000.0,  400, 'SKU-RED-001',  TRUE),   -- 7
(4, 'Switch Administrable 24 Puertos',   'Switch Gigabit Ethernet con PoE+',                        250000.0,  200, 'SKU-RED-002',  TRUE),   -- 8
(5, 'Antivirus Corporativo (Lic. Anual)','Protección de endpoints para 10 usuarios',                300000.0, 3000, 'SKU-SOFT-001', TRUE),   -- 9
(5, 'Suite Ofimática 365 (Lic. Anual)', 'Suscripción empresarial por usuario',                     120000.0, 3000, 'SKU-SOFT-002', TRUE),   -- 10
(6, 'Servidor Rack 1U',                  'Servidor Xeon, 32GB RAM, 2TB SSD NVMe',                 2500000.0,   80, 'SKU-SRV-001',  TRUE),   -- 11
(6, 'Disco Duro NAS 8TB',               'HDD optimizado para almacenamiento en red',                280000.0,  350, 'SKU-SRV-002',  TRUE),   -- 12
(7, 'Cámara Videoconferencia 4K',       'Cámara PTZ para salas de reuniones',                       450000.0,  150, 'SKU-AUD-001',  TRUE),   -- 13
(7, 'Auriculares con Cancelación Ruido','Headset profesional UC',                                    85000.0,  800, 'SKU-AUD-002',  TRUE),   -- 14
(3, 'Resmas de Papel A4 (Caja)',         'Caja de 10 resmas 75g',                                    35000.0, 4000, 'SKU-INS-002',  TRUE),   -- 15
(8, 'Kit Cámaras de Seguridad CCTV',    '4 cámaras 1080p + DVR 1TB',                               380000.0,  100, 'SKU-SEG-001',  TRUE),   -- 16
(8, 'Control de Acceso Biométrico',     'Lector de huella y tarjeta RFID',                          120000.0,  300, 'SKU-SEG-002',  TRUE),   -- 17
(1, 'Tablet Corporativa 10"',            'Tablet Android empresarial 64GB',                          220000.0,  500, 'SKU-COMP-004', TRUE),   -- 18
(2, 'Cajonera Metálica',                 'Cajonera bajo escritorio de 3 gavetas',                    95000.0,  600, 'SKU-OFI-003',  TRUE),   -- 19
(4, 'Access Point Techo Wi-Fi 6',       'Punto de acceso empresarial doble banda',                  135000.0,  350, 'SKU-RED-003',  TRUE),   -- 20
(1, 'Teclado Mecánico Empresarial',     'Teclado mecánico USB retroiluminado',                       65000.0, 1000, 'SKU-COMP-005', TRUE),   -- 21
(1, 'Mouse Inalámbrico Ergonómico',     'Mouse inalámbrico con precisión óptica',                    45000.0, 1500, 'SKU-COMP-006', TRUE),   -- 22
(2, 'Armario Archivo Metal 4 Cajones',  'Armario archivador metálico con llave',                    210000.0,  250, 'SKU-OFI-004',  TRUE),   -- 23
(6, 'UPS Online 1500VA',                'Sistema de alimentación ininterrumpida',                   450000.0,   80, 'SKU-SRV-003',  TRUE),   -- 24
(5, 'Licencia AutoCAD 2025 (Anual)',    'Licencia suscripción AutoCAD para empresas',               850000.0,  500, 'SKU-SOFT-003', TRUE),   -- 25
(9, 'Ácido Sulfúrico Industrial',       'Bidón de 20L de ácido sulfúrico (Peligroso)',              50000.0,   150, 'SKU-QUIM-001', TRUE),   -- 26
(9, 'Solvente Industrial',              'Tambor de 200L de solvente químico',                      120000.0,   300, 'SKU-QUIM-002', TRUE);   -- 27   -- 25

-- ── 7.9 Datos de pago ────────────────────────────────────────
INSERT INTO datos_pago_entidad (usuario_usuario, metodo_pago, numero_tarjeta, fecha_expiracion) VALUES
(1,  'Tarjeta Crédito',        '4111111111111111', '10/27'),  -- 1
(2,  'Transferencia Bancaria', 'N/A',              'N/A'),    -- 2
(3,  'Tarjeta Crédito',        '4222222222222222', '11/26'),  -- 3
(4,  'Tarjeta Débito',         '4333333333333333', '08/26'),  -- 4
(5,  'Transferencia Bancaria', 'N/A',              'N/A'),    -- 5
(6,  'Tarjeta Crédito',        '4444444444444444', '12/27'),  -- 6
(7,  'Tarjeta Débito',         '4555555555555555', '07/26'),  -- 7
(8,  'Tarjeta Crédito',        '4666666666666666', '09/27'),  -- 8
(9,  'Tarjeta Débito',         '4777777777777777', '05/28'),  -- 9
(10, 'Tarjeta Crédito',        '4888888888888888', '03/28'),  -- 10
(11, 'Transferencia Bancaria', 'N/A',              'N/A'),    -- 11
(12, 'Tarjeta Crédito',        '4999999999999999', '06/28'),  -- 12
(13, 'Tarjeta Débito',         '5111111111111111', '04/27'),  -- 13
(14, 'Tarjeta Crédito',        '5222222222222222', '02/28'),  -- 14
(15, 'Transferencia Bancaria', 'N/A',              'N/A');    -- 15

-- ── 7.10 CARRITOS PAGADOS (históricos) ────────────────────────
INSERT INTO carrito_entidad (carrito_usuario_id, estado, costo_carrito) VALUES
(1,  'PAGADO', 3350000),   --  1
(4,  'PAGADO', 1260000),   --  2
(6,  'PAGADO', 3610000),   --  3
(7,  'PAGADO',  255000),   --  4
(2,  'PAGADO',  840000),   --  5
(5,  'PAGADO',  520000),   --  6
(8,  'PAGADO', 3310000),   --  7
(1,  'PAGADO',  710000),   --  8
(3,  'PAGADO',  855000),   --  9
(4,  'PAGADO', 1420000),   -- 10
(7,  'PAGADO',  620000),   -- 11
(2,  'PAGADO',  735000),   -- 12
(5,  'PAGADO',  850000),   -- 13
(9,  'PAGADO',  535000),   -- 14
(10, 'PAGADO', 2500000),   -- 15
(11, 'PAGADO', 1680000),   -- 16
(12, 'PAGADO',  960000),   -- 17
(13, 'PAGADO', 1500000),   -- 18
(14, 'PAGADO',  780000),   -- 19
(15, 'PAGADO', 2100000);   -- 20

-- ── ÍTEMS DE CARRITOS PAGADOS ─────────────────────────────────
INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto) VALUES
(1, 1, 2), (1, 2, 2), (1, 8, 1),
(2, 4, 5), (2, 10, 3),
(3, 11, 1), (3, 12, 3), (3, 20, 2),
(4, 14, 3),
(5, 3, 1), (5, 6, 2), (5, 15, 2),
(6, 2, 1), (6, 14, 2),
(7, 11, 1), (7, 12, 2), (7, 8, 1),
(8, 2, 1), (8, 4, 2),
(9, 6, 3), (9, 7, 2), (9, 9, 1),
(10, 1, 1), (10, 20, 1), (10, 21, 1),
(11, 14, 2), (11, 5, 1),
(12, 7, 2), (12, 20, 1), (12, 9, 1),
(13, 18, 2), (13, 14, 4), (13, 15, 2),
(14, 16, 1), (14, 17, 1), (14, 15, 1),
(15, 1, 1), (15, 2, 2), (15, 10, 5),
(16, 4, 4), (16, 19, 2), (16, 22, 2), (16, 21, 5),
(17, 9, 2), (17, 10, 3),
(18, 13, 1), (18, 7, 2), (18, 14, 5), (18, 22, 5),
(19, 25, 1),
(20, 11, 1), (20, 22, 5);

-- ── 7.11 Informacion de entrega (ubicaciones dentro de la cobertura) ──
INSERT INTO informacion_entrega_entidad
    (usuario_usuario, direccion, numero, rut_recibe_entrega, rut_empresa, estado_entrega, activa, ubicacion, comuna)
VALUES
(1,  'Av. Las Condes',          '5430', '15.555.666-K', '76.123.456-7', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6019, -33.4172), 4326), 'Las Condes'),       -- 1
(4,  'Calle Industrias',        '1020', '16.777.888-2', '80.555.444-2', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6395, -33.4700), 4326), 'Quilicura'),        -- 2
(6,  'Providencia',             '1100', '14.222.333-1', '78.333.444-5', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6100, -33.4260), 4326), 'Providencia'),      -- 3
(7,  'Alameda',                  '440', '12.888.999-5', '79.111.222-3', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6500, -33.4600), 4326), 'Santiago'),         -- 4
(2,  'Las Flores',               '120', '11.222.333-4', '76.123.456-7', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.5980, -33.4489), 4326), 'Renca'),            -- 5
(5,  'Av. Norte',                '980', '18.111.222-3', '90.222.333-1', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6944, -33.3806), 4326), 'Huechuraba'),       -- 6
(8,  'Los Alerces',              '321', '10.999.888-7', '77.000.111-K', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.7200, -33.4900), 4326), 'Maipú'),            -- 7
(1,  'Av. La Florida',           '777', '12.123.123-1', '76.123.456-7', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6070, -33.4050), 4326), 'La Florida'),       -- 8
(3,  'Av. Central',              '455', '19.555.222-9', '77.000.111-K', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6693, -33.4569), 4326), 'Santiago'),         -- 9
(4,  'Nueva Esperanza',          '990', '17.888.777-6', '80.555.444-2', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6300, -33.4900), 4326), 'Peñalolén'),        -- 10
(7,  'Gran Avenida',             '333', '13.333.444-5', '79.111.222-3', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6600, -33.4650), 4326), 'San Miguel'),       -- 11
(2,  'Av. Apoquindo',           '3200', '13.444.555-6', '76.123.456-7', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6120, -33.4270), 4326), 'Las Condes'),       -- 12
(5,  'Ruta 5 Sur',              '8800', '17.111.222-3', '90.222.333-1', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6980, -33.6150), 4326), 'San Bernardo'),     -- 13
(9,  'Camino Industrial',       '1500', '18.333.444-5', '88.999.000-4', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6480, -33.4830), 4326), 'Quilicura'),        -- 14
(10, 'Av. Libertador Norte',     '245', '19.666.777-8', '70.888.777-6', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6710, -33.4390), 4326), 'Independencia'),    -- 15
(11, 'Av. Los Leones',          '1890', '20.111.222-3', '85.111.222-3', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6050, -33.4190), 4326), 'Providencia'),      -- 16
(12, 'Av. Grecia',               '512', '21.222.333-4', '86.222.333-4', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.5850, -33.4500), 4326), 'Ñuñoa'),            -- 17
(13, 'Av. Vicuña Mackenna',     '3400', '22.333.444-5', '87.333.444-5', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6200, -33.4700), 4326), 'La Florida'),       -- 18
(14, 'Los Presidentes',          '870', '23.444.555-6', '89.444.555-6', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.5700, -33.4250), 4326), 'Peñalolén'),        -- 19
(15, 'Av. Kennedy',             '9001', '24.555.666-7', '91.555.666-7', 'ENTREGADO',  TRUE, ST_SetSRID(ST_MakePoint(-70.6350, -33.4000), 4326), 'Vitacura');         -- 20

-- ── 7.12 Ejecutar asignación inicial de comuna_id ────────────
-- Enlaza las entregas con las comunas de prueba creadas arriba
UPDATE informacion_entrega_entidad ie
SET comuna_id = c.id
FROM comuna_entidad c
WHERE ST_Contains(c.geom, ie.ubicacion);

-- ── 7.13 ÓRDENES HISTÓRICAS ───────────────────────────────────
SET session_replication_role = replica;  -- deshabilita triggers temporalmente

INSERT INTO ordenes_entidad
    (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado, almacen_asignado_id, distancia_envio_km)
VALUES
(1,  1,  '2023-10-01 10:05:00', 'APROBADA',   1, 4.320),
(2,  2,  '2024-01-20 16:50:00', 'APROBADA',   1, 2.810),
(3,  3,  '2024-03-10 11:35:00', 'APROBADA',   1, 1.150),
(4,  4,  '2024-02-28 09:20:00', 'APROBADA',   1, 0.980),
(5,  5,  '2024-05-15 12:10:00', 'APROBADA',   1, 7.920),
(6,  6,  '2025-01-10 15:30:00', 'APROBADA',   1, 10.250),
(7,  7,  '2025-11-22 10:20:00', 'APROBADA',   3, 5.600),
(8,  8,  '2024-07-02 14:05:00', 'CANCELADA',  1, 3.800),
(9,  9,  '2024-09-18 11:45:00', 'APROBADA',   1, 0.100),
(10, 10, '2025-03-07 10:15:00', 'APROBADA',   2, 18.540),
(11, 11, '2025-06-21 18:25:00', 'APROBADA',   1, 1.440),
(12, 12, '2024-04-18 10:10:00', 'ENTREGADO',  1, 1.020),
(13, 13, '2024-04-22 12:25:00', 'ENTREGADO',  2, 22.150),
(14, 14, '2024-05-03 09:40:00', 'ENTREGADO',  1, 2.540),
(15, 15, '2024-05-08 15:05:00', 'ENTREGADO',  1, 0.550),
(16, 16, '2024-11-14 09:30:00', 'APROBADA',   1, 1.300),
(17, 17, '2024-12-05 14:00:00', 'APROBADA',   1, 3.640),
(18, 18, '2025-01-20 10:00:00', 'APROBADA',   1, 4.750),
(19, 19, '2025-02-10 16:00:00', 'PENDIENTE',  1, 6.210),
(20, 20, '2025-03-25 11:00:00', 'APROBADA',   1, 3.350);

SET session_replication_role = DEFAULT;

-- ── 7.14 Vincular orden_orden_id en informacion_entrega ───────
UPDATE informacion_entrega_entidad SET orden_orden_id = 1  WHERE info_entrega_id = 1;
UPDATE informacion_entrega_entidad SET orden_orden_id = 2  WHERE info_entrega_id = 2;
UPDATE informacion_entrega_entidad SET orden_orden_id = 3  WHERE info_entrega_id = 3;
UPDATE informacion_entrega_entidad SET orden_orden_id = 4  WHERE info_entrega_id = 4;
UPDATE informacion_entrega_entidad SET orden_orden_id = 5  WHERE info_entrega_id = 5;
UPDATE informacion_entrega_entidad SET orden_orden_id = 6  WHERE info_entrega_id = 6;
UPDATE informacion_entrega_entidad SET orden_orden_id = 7  WHERE info_entrega_id = 7;
UPDATE informacion_entrega_entidad SET orden_orden_id = 8  WHERE info_entrega_id = 8;
UPDATE informacion_entrega_entidad SET orden_orden_id = 9  WHERE info_entrega_id = 9;
UPDATE informacion_entrega_entidad SET orden_orden_id = 10 WHERE info_entrega_id = 10;
UPDATE informacion_entrega_entidad SET orden_orden_id = 11 WHERE info_entrega_id = 11;
UPDATE informacion_entrega_entidad SET orden_orden_id = 12 WHERE info_entrega_id = 12;
UPDATE informacion_entrega_entidad SET orden_orden_id = 13 WHERE info_entrega_id = 13;
UPDATE informacion_entrega_entidad SET orden_orden_id = 14 WHERE info_entrega_id = 14;
UPDATE informacion_entrega_entidad SET orden_orden_id = 15 WHERE info_entrega_id = 15;
UPDATE informacion_entrega_entidad SET orden_orden_id = 16 WHERE info_entrega_id = 16;
UPDATE informacion_entrega_entidad SET orden_orden_id = 17 WHERE info_entrega_id = 17;
UPDATE informacion_entrega_entidad SET orden_orden_id = 18 WHERE info_entrega_id = 18;
UPDATE informacion_entrega_entidad SET orden_orden_id = 19 WHERE info_entrega_id = 19;
UPDATE informacion_entrega_entidad SET orden_orden_id = 20 WHERE info_entrega_id = 20;

-- ── 7.15 FACTURAS ─────────────────────────────────────────────
INSERT INTO factura_entidad
    (usuario_usuario, datos_pago_id, orden_orden_id, precio_total, fecha_emision, total_neto, iva, costo_envio)
VALUES
(1,  1,  1,  3352160.0, '2023-10-01 10:30:00', 2817311.0,  534849.0,  2160.00),
(4,  4,  2,  1261405.0, '2024-01-20 17:15:00', 1060005.0,  201400.0,  1405.00),
(6,  6,  3,  3610575.0, '2024-03-10 12:00:00', 3034004.0,  576571.0,   575.00),
(7,  7,  4,   255490.0, '2024-02-28 09:45:00',  214698.0,   40792.0,   490.00),
(2,  2,  5,   843960.0, '2024-05-15 12:30:00',  709210.0,  134750.0,  3960.00),
(5,  5,  6,   525125.0, '2025-01-10 16:00:00',  441281.0,   83844.0,  5125.00),
(8,  8,  7,  3312800.0, '2025-11-22 10:45:00', 2783866.0,  528934.0,  2800.00),
(1,  1,  8,   711900.0, '2024-07-02 14:20:00',  598235.0,  113665.0,  1900.00),
(3,  3,  9,   855050.0, '2024-09-18 12:10:00',  718529.0,  136521.0,    50.00),
(4,  4,  10, 1409270.0, '2025-03-07 10:40:00', 1184261.0,  225009.0,  9270.00),
(7,  7,  11,  620720.0, '2025-06-21 18:40:00',  521613.0,   99107.0,   720.00),
(2,  2,  12,  735510.0, '2024-04-18 10:35:00',  617655.0,  117855.0,   510.00),
(5,  5,  13,  861075.0, '2024-04-22 12:50:00',  723592.0,  137483.0, 11075.00),
(9,  9,  14,  536270.0, '2024-05-03 10:05:00',  450647.0,   85623.0,  1270.00),
(10, 10, 15, 2500275.0, '2024-05-08 15:30:00', 2101072.0,  399203.0,   275.00),
(11, 11, 16, 1325650.0, '2024-11-14 09:45:00', 1114412.0,  211238.0,   650.00),
(12, 12, 17,  961820.0, '2024-12-05 14:20:00',  808252.0,  153568.0,  1820.00),
(13, 13, 18, 1402375.0, '2025-01-20 10:30:00', 1178466.0,  223909.0,  2375.00),
(14, 14, 19,  853105.0, '2025-02-10 16:30:00',  716895.0,  136210.0,  3105.00),
(15, 15, 20, 2226675.0, '2025-03-25 11:30:00', 1870315.0,  356360.0,  1675.00);

-- ── 7.16 FACTURA ITEMS ────────────────────────────────────────
INSERT INTO factura_item_entidad (factura_id, producto_id, cantidad, precio_unitario) VALUES
(1, 1,  2, 1200000.0), (1, 2,  2,  350000.0), (1, 8,  1,  250000.0),
(2, 4,  5, 180000.0), (2, 10, 3, 120000.0),
(3, 11, 1, 2500000.0), (3, 12, 3,  280000.0), (3, 20, 2,  135000.0),
(4, 14, 3, 85000.0),
(5, 3,  1, 600000.0), (5, 6,  2,  85000.0), (5, 15, 2,  35000.0),
(6, 2,  1, 350000.0), (6, 14, 2,  85000.0),
(7, 11, 1, 2500000.0), (7, 12, 2,  280000.0), (7, 8,  1,  250000.0),
(8, 2,  1, 350000.0), (8, 4,  2, 180000.0),
(9, 6,  3,  85000.0), (9, 7,  2, 150000.0), (9, 9,  1, 300000.0),
(10, 1,  1, 1200000.0), (10, 20, 1,  135000.0), (10, 21, 1,   65000.0),
(11, 14, 2, 85000.0), (11, 5,  1, 450000.0),
(12, 7,  2, 150000.0), (12, 20, 1, 135000.0), (12, 9,  1, 300000.0),
(13, 18, 2, 220000.0), (13, 14, 4,  85000.0), (13, 15, 2,  35000.0),
(14, 16, 1, 380000.0), (14, 17, 1, 120000.0), (14, 15, 1,  35000.0),
(15, 1,  1, 1200000.0), (15, 2,  2,  350000.0), (15, 10, 5,  120000.0),
(16, 4,  4, 180000.0), (16, 19, 2,  95000.0), (16, 22, 2,  45000.0), (16, 21, 5,  65000.0),
(17, 9,  2, 300000.0), (17, 10, 3, 120000.0),
(18, 13, 1, 450000.0), (18, 7,  2, 150000.0), (18, 14, 5,  85000.0), (18, 22, 5,  45000.0),
(19, 25, 1, 850000.0),
(20, 11, 1, 2500000.0), (20, 22, 5,    45000.0);

-- ── 7.17 DESCUENTO DE STOCK HISTÓRICO ────────────────────────
UPDATE producto_entidad SET stock = stock -
    COALESCE((
        SELECT SUM(fi.cantidad)
        FROM factura_item_entidad fi
        WHERE fi.producto_id = producto_entidad.producto_id
    ), 0);

-- ── 7.18 ZONAS RESIDENCIALES PROTEGIDAS (Configuración interna) ──
INSERT INTO zona_residencial_protegida_entidad (nombre_zona, activa, poligono) VALUES
(
    'Zona Residencial Providencia',
    TRUE,
    ST_GeomFromText(
        'POLYGON((-70.66 -33.47, -70.62 -33.47, -70.62 -33.42, -70.66 -33.42, -70.66 -33.47))',
        4326
    )
),
(
    'Zona Residencial La Florida',
    TRUE,
    ST_GeomFromText(
        'POLYGON((-70.61 -33.58, -70.54 -33.58, -70.54 -33.52, -70.61 -33.52, -70.61 -33.58))',
        4326
    )
),
(
    'Zona Residencial Ñuñoa',
    TRUE,
    ST_GeomFromText(
        'POLYGON((-70.62 -33.46, -70.59 -33.46, -70.59 -33.43, -70.62 -33.43, -70.62 -33.46))',
        4326
    )
);

-- ── 7.19 DISTRIBUCIÓN DE STOCK EN ALMACENES ───────────────────
WITH distribucion AS (
    SELECT
        a.almacen_id,
        p.producto_id,
        p.stock,
        ROW_NUMBER() OVER (PARTITION BY p.producto_id ORDER BY a.almacen_id) AS posicion,
        COUNT(*) OVER (PARTITION BY p.producto_id) AS cantidad_almacenes
    FROM almacen_entidad a
    CROSS JOIN producto_entidad p
    WHERE p.activo = TRUE
)
INSERT INTO stock_almacen_producto_entidad (almacen_id, producto_id, stock_disponible)
SELECT
    almacen_id,
    producto_id,
    CASE
        WHEN cantidad_almacenes = 1 THEN stock
        WHEN posicion < cantidad_almacenes THEN FLOOR(stock::NUMERIC / cantidad_almacenes)::INT
        ELSE stock - FLOOR(stock::NUMERIC / cantidad_almacenes)::INT * (cantidad_almacenes - 1)
    END
FROM distribucion
ON CONFLICT (almacen_id, producto_id) DO NOTHING;

-- ── 7.20 CARRITOS ACTIVOS Y ABANDONADOS ──────────────────────
INSERT INTO carrito_entidad (carrito_usuario_id, estado, costo_carrito) VALUES
(2,  'ACTIVO',      450000),  -- 21
(10, 'ACTIVO',     2550000),  -- 22
(9,  'ABANDONADO',  180000);  -- 23

INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto) VALUES
(21, 5,  1),
(22, 8,  4), (22, 18, 5), (22, 13, 1),
(23, 4,  1);

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

-- ── 7.21 Actualizar ultima_compra de usuarios ────────────────
UPDATE usuario_entidad SET ultima_compra = '2023-10-01 10:05:00' WHERE usuario_id = 1;
UPDATE usuario_entidad SET ultima_compra = '2024-05-15 12:10:00' WHERE usuario_id = 2;
UPDATE usuario_entidad SET ultima_compra = '2024-09-18 11:45:00' WHERE usuario_id = 3;
UPDATE usuario_entidad SET ultima_compra = '2025-03-07 10:15:00' WHERE usuario_id = 4;
UPDATE usuario_entidad SET ultima_compra = '2024-04-22 12:25:00' WHERE usuario_id = 5;
UPDATE usuario_entidad SET ultima_compra = '2024-03-10 11:35:00' WHERE usuario_id = 6;
UPDATE usuario_entidad SET ultima_compra = '2025-06-21 18:25:00' WHERE usuario_id = 7;
UPDATE usuario_entidad SET ultima_compra = '2025-11-22 10:20:00' WHERE usuario_id = 8;
UPDATE usuario_entidad SET ultima_compra = '2024-05-03 09:40:00' WHERE usuario_id = 9;
UPDATE usuario_entidad SET ultima_compra = '2024-05-08 15:05:00' WHERE usuario_id = 10;
UPDATE usuario_entidad SET ultima_compra = '2024-11-14 09:30:00' WHERE usuario_id = 11;
UPDATE usuario_entidad SET ultima_compra = '2024-12-05 14:00:00' WHERE usuario_id = 12;
UPDATE usuario_entidad SET ultima_compra = '2025-01-20 10:00:00' WHERE usuario_id = 13;
UPDATE usuario_entidad SET ultima_compra = '2025-02-10 16:00:00' WHERE usuario_id = 14;
UPDATE usuario_entidad SET ultima_compra = '2025-03-25 11:00:00' WHERE usuario_id = 15;

-- ═══════════════════════════════════════════════════════════════
-- 8. VISTAS MATERIALIZADAS
-- ═══════════════════════════════════════════════════════════════

-- Vista: ventas mensuales por categoría
CREATE MATERIALIZED VIEW vw_ventas_mensuales_por_categoria AS
SELECT
    TO_CHAR(o.fecha_orden, 'YYYY-MM')      AS mes_ano,
    EXTRACT(YEAR  FROM o.fecha_orden)::INT AS anio,
    EXTRACT(MONTH FROM o.fecha_orden)::INT AS mes,
    c.nombre_categoria,
    COUNT(DISTINCT o.orden_id)             AS cantidad_ordenes,
    SUM(fi.cantidad)::INT                  AS cantidad_productos,
    ROUND(SUM(fi.cantidad * fi.precio_unitario)::NUMERIC, 2) AS total_vendido,
    ROUND(AVG(fi.precio_unitario)::NUMERIC, 2)               AS precio_promedio
FROM ordenes_entidad o
JOIN factura_entidad        f   ON f.orden_orden_id = o.orden_id
JOIN factura_item_entidad   fi  ON fi.factura_id    = f.factura_id
JOIN producto_entidad       p   ON p.producto_id    = fi.producto_id
JOIN categoria_entidad      c   ON c.categoria_id   = p.categoria_categoria_id
WHERE o.estado IN ('PENDIENTE', 'APROBADA', 'ENTREGADO', 'EN_RUTA', 'PREPARANDO')
  AND o.fecha_orden IS NOT NULL
GROUP BY
    TO_CHAR(o.fecha_orden, 'YYYY-MM'),
    EXTRACT(YEAR  FROM o.fecha_orden),
    EXTRACT(MONTH FROM o.fecha_orden),
    c.nombre_categoria
ORDER BY anio DESC, mes DESC, c.nombre_categoria;

CREATE INDEX idx_vw_ventas_mes_ano   ON vw_ventas_mensuales_por_categoria (mes_ano);
CREATE INDEX idx_vw_ventas_categoria ON vw_ventas_mensuales_por_categoria (nombre_categoria);
CREATE INDEX idx_vw_ventas_anio      ON vw_ventas_mensuales_por_categoria (anio);

-- Vista: ventas por comuna
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
    LEFT JOIN ordenes_entidad o              ON o.informacion_info_entrega_id = ie.info_entrega_id
    LEFT JOIN factura_entidad f              ON f.orden_orden_id = o.orden_id
    GROUP BY c.id, c.nombre, c.distrito_postal, c.geom
),
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

-- Vista: ventas por distrito
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


-- ═══════════════════════════════════════════════════════════════
-- 9. TRIGGER DE COBERTURA PARA NUEVAS DIRECCIONES
-- ═══════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION trg_validar_cobertura_direccion()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.ubicacion IS NULL THEN
        RAISE EXCEPTION
            'No se puede guardar la dirección: no tiene coordenadas geográficas';
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

DROP TRIGGER IF EXISTS validar_cobertura_direccion ON informacion_entrega_entidad;
CREATE TRIGGER validar_cobertura_direccion
    BEFORE INSERT OR UPDATE OF ubicacion ON informacion_entrega_entidad
    FOR EACH ROW
    EXECUTE FUNCTION trg_validar_cobertura_direccion();



-- ═══════════════════════════════════════════════════════════════
-- FIN DEL SCRIPT
-- ═══════════════════════════════════════════════════════════════