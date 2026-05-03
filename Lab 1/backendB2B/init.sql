-- ─── 1. LIMPIEZA DEL ESQUEMA ANTERIOR ─────────────────────
DROP MATERIALIZED VIEW IF EXISTS vw_ventas_mensuales_por_categoria CASCADE;
DROP TABLE IF EXISTS factura_entidad;
DROP TABLE IF EXISTS carrito_producto_entidad;
DROP TABLE IF EXISTS informacion_entrega_entidad CASCADE;
DROP TABLE IF EXISTS ordenes_entidad CASCADE;
DROP TABLE IF EXISTS producto_entidad;
DROP TABLE IF EXISTS carrito_entidad;
DROP TABLE IF EXISTS categoria_entidad;
DROP TABLE IF EXISTS usuario_entidad;

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

CREATE TABLE ordenes_entidad (
    orden_id                   SERIAL PRIMARY KEY,
    carrito_carrito_id         BIGINT REFERENCES carrito_entidad(carrito_id),
    informacion_info_entrega_id BIGINT REFERENCES informacion_entrega_entidad(info_entrega_id),
    fecha_orden                TIMESTAMP,
    estado                     VARCHAR(255)
);

ALTER TABLE informacion_entrega_entidad
    ADD CONSTRAINT fk_info_entrega_orden
    FOREIGN KEY (orden_orden_id) REFERENCES ordenes_entidad(orden_id);

CREATE TABLE factura_entidad (
    factura_id      BIGSERIAL PRIMARY KEY,
    usuario_usuario BIGINT REFERENCES usuario_entidad(usuario_id),
    orden_orden_id  INT REFERENCES ordenes_entidad(orden_id),
    precio_total    REAL,
    fecha_emision   TIMESTAMP,
    total_neto      REAL,
    iva             REAL
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
    ELSIF NEW.estado = 'PAGADO' THEN
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

-- ─── 5. DATOS DE PRUEBA ────────────────────────────────────

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
(1,  'ACTIVO',      35000);  -- Carrito 8

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
(8, 15, 1); -- 1 * 35,000    =    35,000

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

-- Información de entrega (carritos completados)
INSERT INTO informacion_entrega_entidad (usuario_usuario, direccion, numero, rut_recibe_entrega, rut_empresa, estado_entrega, activa) VALUES
(1, 'Av. Las Condes',    '5430', '15.555.666-K', '76.123.456-7', 'ENTREGADO',  TRUE),
(4, 'Calle Industrias',  '1020', '16.777.888-2', '80.555.444-2', 'EN CAMINO',  TRUE),
(6, 'Providencia',       '1100', '14.222.333-1', '78.333.444-5', 'PREPARANDO', TRUE),
(7, 'Alameda',            '440', '12.888.999-5', '79.111.222-3', 'ENTREGADO',  TRUE);

-- Órdenes generadas desde los carritos completados
INSERT INTO ordenes_entidad (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado) VALUES
(1, 1, '2023-10-01 10:05:00', 'ENTREGADO'),
(3, 2, '2024-01-20 16:50:00', 'EN_RUTA'),
(4, 3, '2024-03-10 11:35:00', 'PREPARANDO'),
(5, 4, '2024-02-28 09:20:00', 'ENTREGADO');

-- Vincular orden_id a informacion_entrega
UPDATE informacion_entrega_entidad SET orden_orden_id = 1 WHERE info_entrega_id = 1;
UPDATE informacion_entrega_entidad SET orden_orden_id = 2 WHERE info_entrega_id = 2;
UPDATE informacion_entrega_entidad SET orden_orden_id = 3 WHERE info_entrega_id = 3;
UPDATE informacion_entrega_entidad SET orden_orden_id = 4 WHERE info_entrega_id = 4;

-- Facturas con IVA del 19%
INSERT INTO factura_entidad (usuario_usuario, orden_orden_id, precio_total, fecha_emision, total_neto, iva) VALUES
(1, 1, 3350000.0, '2023-10-01 10:30:00', 2815126.0,  534874.0),
(4, 2, 1260000.0, '2024-01-20 17:15:00', 1058824.0,  201176.0),
(6, 3, 3610000.0, '2024-03-10 12:00:00', 3033613.0,  576387.0),
(7, 4,  255000.0, '2024-02-28 09:45:00',  214286.0,   40714.0);


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
WHERE o.estado IN ('ENTREGADO', 'EN_RUTA', 'PREPARANDO', 'PAGADO')
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


