
    -- Eliminamos en orden inverso a la creación para respetar las dependencias
    DROP TABLE IF EXISTS factura_entidad;
    DROP TABLE IF EXISTS carrito_producto_entidad;
    DROP TABLE IF EXISTS informacion_entrega_entidad CASCADE;
    DROP TABLE IF EXISTS ordenes_entidad CASCADE;
    DROP TABLE IF EXISTS producto_entidad;
    DROP TABLE IF EXISTS carrito_entidad;
    DROP TABLE IF EXISTS categoria_entidad;
    DROP TABLE IF EXISTS usuario_entidad;

    -- Creación de tablas
    CREATE TABLE usuario_entidad (
        usuario_id BIGSERIAL PRIMARY KEY,
        nombre_usuario VARCHAR(255),
        correo VARCHAR(255),
        contrasena VARCHAR(255),
        ultima_compra TIMESTAMP,
        rut_empresa VARCHAR(255),
        rol VARCHAR(255)
    );

    CREATE TABLE categoria_entidad (
        categoria_id SERIAL PRIMARY KEY,
        nombre_categoria VARCHAR(255),
        estado_categoria BOOLEAN NOT NULL DEFAULT TRUE
    );

    CREATE TABLE producto_entidad (
        producto_id BIGSERIAL PRIMARY KEY,
        categoria_categoria_id INT REFERENCES categoria_entidad(categoria_id),
        nombre_producto VARCHAR(255),
        descripcion TEXT,
        precio REAL,
        stock INT NOT NULL DEFAULT 0,
        stock_reservado INT NOT NULL DEFAULT 0,
        sku VARCHAR(255),
        activo BOOLEAN NOT NULL DEFAULT TRUE,
        CONSTRAINT ck_stock_reservado CHECK (stock_reservado >= 0 AND stock_reservado <= stock)
    );

    CREATE TABLE carrito_entidad (
        carrito_id BIGSERIAL PRIMARY KEY,
        carrito_usuario_id BIGINT REFERENCES usuario_entidad(usuario_id),
        estado VARCHAR(255),
        costo_carrito BIGINT,
        ultima_actualizacion TIMESTAMP NOT NULL DEFAULT NOW()
    );

    -- Solo un carrito ACTIVO o ABANDONADO por usuario
    CREATE UNIQUE INDEX IF NOT EXISTS ux_carrito_activo_abandonado
    ON carrito_entidad (carrito_usuario_id)
    WHERE estado IN ('ACTIVO', 'ABANDONADO');

    CREATE TABLE carrito_producto_entidad (
        carrito_producto_id BIGSERIAL PRIMARY KEY,
        carrito_carrito_id BIGINT REFERENCES carrito_entidad(carrito_id),
        producto_producto_id BIGINT REFERENCES producto_entidad(producto_id),
        unidad_producto BIGINT
    );

    -- Stock reservado en productos
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
            SET stock = p.stock - cp.unidad_producto,
                stock_reservado = p.stock_reservado - cp.unidad_producto
            FROM carrito_producto_entidad cp
            WHERE cp.carrito_carrito_id = p_carrito_id
              AND p.producto_id = cp.producto_producto_id;
        END IF;
    END;
    $$;

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

    CREATE TABLE informacion_entrega_entidad (
        info_entrega_id BIGSERIAL PRIMARY KEY,
        usuario_usuario BIGINT REFERENCES usuario_entidad(usuario_id),
        -- orden_orden_id se agregará como FK más adelante para evitar dependencia circular
        orden_orden_id INT,
        direccion VARCHAR(255),
        numero VARCHAR(255),
        rut_recibe_entrega VARCHAR(255),
        rut_empresa VARCHAR(255),
        estado_entrega VARCHAR(255),
        activa BOOLEAN NOT NULL DEFAULT TRUE
    );

    CREATE TABLE ordenes_entidad (
        orden_id SERIAL PRIMARY KEY,
        carrito_carrito_id BIGINT REFERENCES carrito_entidad(carrito_id),
        informacion_info_entrega_id BIGINT REFERENCES informacion_entrega_entidad(info_entrega_id),
        fecha_orden TIMESTAMP,
        estado VARCHAR(255)
    );

    ALTER TABLE informacion_entrega_entidad
    ADD CONSTRAINT fk_info_entrega_orden
    FOREIGN KEY (orden_orden_id) REFERENCES ordenes_entidad(orden_id);


    CREATE TABLE factura_entidad (
        factura_id BIGSERIAL PRIMARY KEY,
        usuario_usuario BIGINT REFERENCES usuario_entidad(usuario_id),
        orden_orden_id INT REFERENCES ordenes_entidad(orden_id),
        precio_total REAL,
        fecha_emision TIMESTAMP,
        total_neto REAL,
        iva REAL
    );

    -- Poblamiento de datos

    INSERT INTO usuario_entidad (nombre_usuario, correo, contrasena, ultima_compra, rut_empresa, rol) VALUES
    ('Juan Perez', 'jperez@techsolutions.cl', '$2a$12$Ue51OzMXnpiqWLxMPaAKjOPSnfs1267HJakRsYNn1jUIlzKopwL/q', '2023-10-01 10:00:00', '76.123.456-7', 'CLIENTE'),
    ('Maria Lopez', 'mlopez@techsolutions.cl', '$2a$12$D4WkfIGsvL1tXLgfxuDp0uNaKuhEremwCRTYsyHWHlHd/Ac/PYVcC', '2023-11-15 14:20:00', '76.123.456-7', 'CLIENTE'),
    ('Carlos Ruiz', 'admin@ecommerceb2b.cl', '$2a$12$Z0rQJcFIeZcX6.XwosKuqOI.Y.kpfY/hdXxtA127heFc6.xyZXz4q', '2022-01-10 09:00:00', '77.000.111-K', 'ADMIN'),
    ('Ana Soto', 'aso@construccionesdelnorte.cl', '$2a$12$QPGq3X4zz.Fe8Sl1A/UkMOVESAEDlNcUnq2d9MyWHhQzRAFW1hrCm', '2024-01-20 16:45:00', '80.555.444-2', 'CLIENTE'),
    ('Luis Fernandez', 'lfernandez@logisticadelsur.cl', '$2a$12$IZ6UqglJaxjyqbiQXz4njuTvZPDz95.Nfs0L6AOg.5Kcj9hgGZWEO', NULL, '90.222.333-1', 'CLIENTE'),
    ('Pedro Morales', 'pmo@desarrolloweb.cl', '$2a$12$jxYs5vcAixX9biOL/h91k.kIy0yfJn3PqlbSWHi3cnCUzfEUTRMBW', '2024-03-10 11:30:00', '78.333.444-5', 'CLIENTE'),
    ('Sofia Vergara', 'svergara@saludintegral.cl', '$2a$12$Uh1qdcJFW5JhpNrxPFA60.TtPpWx6yVnRvEvvX5woy5mim7tlmlF6', '2024-02-28 09:15:00', '79.111.222-3', 'CLIENTE'),
    ('Diego Vargas', 'dvar@adminb2b.cl', '$2a$12$NQUmXqv6moflf/rSszbz6up2GF9UpY0dLgKGDlquH0G6Dqek7dY5S', '2023-05-05 10:00:00', '77.000.111-K', 'ADMIN'),
    ('Camila Rojas', 'crojas@mineraandina.cl', '$2a$12$67Wz0mNx8GYBYWUXwKLy3.2TXq8mQfjATolERMawVZYPXywzQmNi6', NULL, '88.999.000-4', 'CLIENTE'),
    ('Javier Tello', 'jtello@educacionfuturo.cl', '$2a$12$vlx4hMEyKHW9nT8cHKQmmeV6HHlvB7iE1oJffk0aDpXDsg2oSrH.y', '2024-04-12 15:45:00', '70.888.777-6', 'CLIENTE');

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
    (1, 'Notebook Empresarial Pro 15"', 'Notebook Intel Core i7, 16GB RAM, 512GB SSD', 1200000.0, 150, 'SKU-COMP-001', 'TRUE'),
    (1, 'Monitor 27" 4K', 'Monitor IPS orientable para trabajo prolongado', 350000.0, 200, 'SKU-COMP-002','TRUE' ),
    (1, 'Mini PC Corporativo', 'Desktop compacto Intel Core i5, 8GB RAM', 600000.0, 80, 'SKU-COMP-003','TRUE'),
    (2, 'Silla Ergonómica Premium', 'Silla de oficina ergonómica avanzada, soporte lumbar', 180000.0, 300, 'SKU-OFI-001','TRUE'),
    (2, 'Escritorio Eléctrico Ajustable', 'Escritorio con ajuste de altura motorizado', 450000.0, 50, 'SKU-OFI-002','TRUE'),
    (3, 'Set Toners Impresora Láser', 'Pack de 4 tóners CMYK alto rendimiento', 85000.0, 500, 'SKU-INS-001','TRUE'),
    (4, 'Router Empresarial Wi-Fi 6', 'Router VPN Gigabit Dual-WAN', 150000.0, 120, 'SKU-RED-001','TRUE'),
    (4, 'Switch Administrable 24 Puertos', 'Switch Gigabit Ethernet con PoE+', 250000.0, 60, 'SKU-RED-002','TRUE'),
    (5, 'Antivirus Corporativo (Licencia Anual)', 'Protección de endpoints para 10 usuarios', 300000.0, 999, 'SKU-SOFT-001','TRUE'),
    (5, 'Suite Ofimática 365 (Licencia Anual)', 'Suscripción empresarial de herramientas de oficina por usuario', 120000.0, 999, 'SKU-SOFT-002','TRUE'),
    (6, 'Servidor Rack 1U', 'Servidor Xeon, 32GB RAM, 2TB SSD NVMe', 2500000.0, 20, 'SKU-SRV-001','TRUE'),
    (6, 'Disco Duro NAS 8TB', 'HDD optimizado para almacenamiento en red', 280000.0, 100, 'SKU-SRV-002','TRUE'),
    (7, 'Cámara Videoconferencia 4K', 'Cámara PTZ para salas de reuniones', 450000.0, 40, 'SKU-AUD-001','TRUE'),
    (7, 'Auriculares con Cancelación de Ruido', 'Headset profesional UC', 85000.0, 250, 'SKU-AUD-002','TRUE'),
    (3, 'Resmas de Papel A4 (Caja)', 'Caja de 10 resmas de papel obra 75g', 35000.0, 1000, 'SKU-INS-002','TRUE'),
    (8, 'Kit Cámaras de Seguridad CCTV', '4 cámaras 1080p + DVR 1TB', 380000.0, 30, 'SKU-SEG-001','TRUE'),
    (8, 'Control de Acceso Biométrico', 'Lector de huella y tarjeta RFID', 120000.0, 80, 'SKU-SEG-002','TRUE'),
    (1, 'Tablet Corporativa 10"', 'Tablet Android empresarial 64GB', 220000.0, 150, 'SKU-COMP-004','TRUE'),
    (2, 'Cajonera Metálica', 'Cajonera bajo escritorio de 3 gavetas', 95000.0, 180, 'SKU-OFI-003','TRUE'),
    (4, 'Access Point Techo Wi-Fi 6', 'Punto de acceso empresarial doble banda', 135000.0, 100, 'SKU-RED-003','TRUE');

    -- Carritos 
    INSERT INTO carrito_entidad (carrito_usuario_id, estado, costo_carrito) VALUES
    (1, 'PAGADO', 3350000),   -- Carrito 1 (Completado)
    (2, 'ACTIVO', 450000),       -- Carrito 2 (Activo)
    (4, 'PAGADO', 1260000),  -- Carrito 3 (Completado)
    (6, 'PAGADO', 3400000),  -- Carrito 4 (Completado)
    (7, 'PAGADO', 255000),   -- Carrito 5 (Completado)
    (10, 'ACTIVO', 2500000),     -- Carrito 6 (Activo)
    (9, 'ABANDONADO', 180000),   -- Carrito 7 (Abandonado)
    (1, 'ACTIVO', 35000);        -- Carrito 8 (Activo nuevo para el user 1)

    INSERT INTO carrito_producto_entidad (carrito_carrito_id, producto_producto_id, unidad_producto) VALUES
    -- Carrito 1
    (1, 1, 2), -- 2 * 1,200,000 = 2,400,000
    (1, 2, 2), -- 2 * 350,000 = 700,000
    (1, 8, 1), -- 1 * 250,000 = 250,000

    -- Carrito 2
    (2, 5, 1), -- 1 * 450,000 = 450,000

    -- Carrito 3
    (3, 4, 5),  -- 5 * 180,000 = 900,000
    (3, 10, 3), -- 3 * 120,000 = 360,000 

    -- Carrito 4 (User 6) - Servidor y discos
    (4, 11, 1), -- 1 * 2,500,000
    (4, 12, 3), -- 3 * 280,000 = 840,000
    (4, 20, 2), -- 2 * 135,000 = 270,000
                -- Subtotal: 3,610,000 

    -- Carrito 5 (User 7) - Headsets
    (5, 14, 3), -- 3 * 85,000 = 255,000

    -- Carrito 6 (User 10) - Switch y Notebooks
    (6, 8, 4),  -- 4 * 250,000 = 1,000,000
    (6, 18, 5), -- 5 * 220,000 = 1,100,000
    (6, 13, 1), -- 1 * 450,000 = 450,000 (Subtotal: 2,550,000)

    -- Carrito 7 (User 9) - Abandonado
    (7, 4, 1),  -- 1 * 180,000 = 180,000

    -- Carrito 8 (User 1) - Resmas
    (8, 15, 1); -- 1 * 35,000 = 35,000

    -- Recalcular stock_reservado segun carritos ACTIVO
    UPDATE producto_entidad SET stock_reservado = 0;

    UPDATE producto_entidad p
    SET stock_reservado = s.total
    FROM (
        SELECT cp.producto_producto_id, SUM(cp.unidad_producto) AS total
        FROM carrito_producto_entidad cp
        JOIN carrito_entidad c ON c.carrito_id = cp.carrito_carrito_id
        WHERE c.estado = 'ACTIVO'
        GROUP BY cp.producto_producto_id
    ) s
    WHERE p.producto_id = s.producto_producto_id;

    -- Actualizar los totales reales calculados arriba (ya se agregaron bien en el INSERT de carrito_entidad, se sobreescribe guiando un costo exacto)
    UPDATE carrito_entidad SET costo_carrito = 3610000 WHERE carrito_id = 4;
    UPDATE carrito_entidad SET costo_carrito = 2550000 WHERE carrito_id = 6;


    -- Información de entrega para Carritos Completados (1, 3, 4, 5)
    INSERT INTO informacion_entrega_entidad (usuario_usuario, direccion, numero, rut_recibe_entrega, rut_empresa, estado_entrega, activa) VALUES
    (1, 'Av. Las Condes', '5430', '15.555.666-K', '76.123.456-7', 'ENTREGADO', true),
    (4, 'Calle Industrias', '1020', '16.777.888-2', '80.555.444-2', 'EN CAMINO', true),
    (6, 'Providencia', '1100', '14.222.333-1', '78.333.444-5', 'PREPARANDO', true),
    (7, 'Alameda', '440', '12.888.999-5', '79.111.222-3', 'ENTREGADO', true);

    -- Ordenes generadas desde los carritos completados
    INSERT INTO ordenes_entidad (carrito_carrito_id, informacion_info_entrega_id, fecha_orden, estado) VALUES
    (1, 1, '2023-10-01 10:05:00', 'ENTREGADO'),
    (3, 2, '2024-01-20 16:50:00', 'EN_RUTA'),
    (4, 3, '2024-03-10 11:35:00', 'PREPARANDO'),
    (5, 4, '2024-02-28 09:20:00', 'ENTREGADO');

    -- Vincular ordenes_id a informacion_entrega 
    UPDATE informacion_entrega_entidad SET orden_orden_id = 1 WHERE info_entrega_id = 1;
    UPDATE informacion_entrega_entidad SET orden_orden_id = 2 WHERE info_entrega_id = 2;
    UPDATE informacion_entrega_entidad SET orden_orden_id = 3 WHERE info_entrega_id = 3;
    UPDATE informacion_entrega_entidad SET orden_orden_id = 4 WHERE info_entrega_id = 4;

    -- Facturas - Cálculo con IVA del 19%
    -- Total Carrito 4: 3610000 -> Neto: ~3033613, IVA: ~576387
    -- Total Carrito 5: 255000 -> Neto: ~214286, IVA: ~40714
    INSERT INTO factura_entidad (usuario_usuario, orden_orden_id, precio_total, fecha_emision, total_neto, iva) VALUES
    (1, 1, 3350000.0, '2023-10-01 10:30:00', 2815126.0, 534874.0),
    (4, 2, 1260000.0, '2024-01-20 17:15:00', 1058824.0, 201176.0),
    (6, 3, 3610000.0, '2024-03-10 12:00:00', 3033613.0, 576387.0),
    (7, 4, 255000.0, '2024-02-28 09:45:00', 214286.0, 40714.0);
