-- Script de Carga de Datos Inicial - Spring Boot
-- Se ejecuta automáticamente al iniciar la aplicación
-- Encoding: UTF-8

-- Crear extensión PostGIS si no existe
CREATE EXTENSION IF NOT EXISTS postgis;

-- Tabla de usuarios con ubicación geográfica
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    ubicacion_geografica geometry(Point, 4326) NOT NULL
);

-- Tabla de sectores con ubicación espacial
CREATE TABLE IF NOT EXISTS sectores (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    ubicacion_espacial geometry(Point, 4326) NOT NULL
);

-- Tabla de tareas vinculadas a usuarios y sectores
CREATE TABLE IF NOT EXISTS tareas (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    fecha_vencimiento TIMESTAMP NOT NULL,
    estado_completada BOOLEAN NOT NULL DEFAULT false,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    sector_id BIGINT NOT NULL REFERENCES sectores(id) ON DELETE CASCADE
);

-- Índices para optimizar búsquedas espaciales y consultas frecuentes
CREATE INDEX IF NOT EXISTS idx_usuarios_ubicacion ON usuarios USING GIST(ubicacion_geografica);
CREATE INDEX IF NOT EXISTS idx_sectores_ubicacion ON sectores USING GIST(ubicacion_espacial);
CREATE INDEX IF NOT EXISTS idx_tareas_usuario ON tareas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_tareas_sector ON tareas(sector_id);
CREATE INDEX IF NOT EXISTS idx_tareas_estado ON tareas(estado_completada);
CREATE INDEX IF NOT EXISTS idx_tareas_fecha ON tareas(fecha_vencimiento);

-- ========== USUARIOS INICIALES ==========
-- Contraseña: 123456 (encriptada con BCrypt)
-- ON CONFLICT (id) DO NOTHING: si el registro ya existe, no hace nada (idempotente)
INSERT INTO usuarios (id, username, password, ubicacion_geografica) VALUES
(1, 'usuario1', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-74.0060 40.7128)', 4326)),
(2, 'usuario2', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-74.0070 40.7140)', 4326)),
(3, 'usuario3', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-74.0050 40.7115)', 4326))
ON CONFLICT (id) DO NOTHING;

-- ========== SECTORES INICIALES ==========
INSERT INTO sectores (id, nombre, ubicacion_espacial) VALUES
(1, 'Construcción Centro',        ST_GeomFromText('POINT(-74.0065 40.7130)', 4326)),
(2, 'Reparación Semáforos Norte', ST_GeomFromText('POINT(-74.0050 40.7150)', 4326)),
(3, 'Mantenimiento Parques',      ST_GeomFromText('POINT(-74.0080 40.7120)', 4326)),
(4, 'Limpieza Vías Este',         ST_GeomFromText('POINT(-74.0040 40.7125)', 4326)),
(5, 'Servicios Públicos Oeste',   ST_GeomFromText('POINT(-74.0090 40.7135)', 4326))
ON CONFLICT (id) DO NOTHING;

-- ========== TAREAS INICIALES (solo si la tabla está vacía) ==========
-- Las tareas no tienen ID explícito, así que se usan con SELECT ... WHERE NOT EXISTS
-- para evitar duplicar los datos de prueba en cada reinicio.
INSERT INTO tareas (titulo, descripcion, fecha_vencimiento, estado_completada, usuario_id, sector_id)
SELECT titulo, descripcion, fecha_vencimiento::timestamp, estado_completada, usuario_id, sector_id
FROM (VALUES
  ('Reparar acera',            'Parchar agujeros en la calle principal',         CURRENT_TIMESTAMP + INTERVAL '5 days',  false, 1, 1),
  ('Cambiar poste de luz',     'Cambiar poste dañado en esquina',                CURRENT_TIMESTAMP + INTERVAL '3 days',  false, 1, 2),
  ('Limpiar parque central',   'Recolectar basura y maleza',                     CURRENT_TIMESTAMP + INTERVAL '7 days',  true,  1, 3),
  ('Pintar banqueta',          'Pintar líneas de estacionamiento',               CURRENT_TIMESTAMP + INTERVAL '10 days', true,  1, 1),
  ('Reparar alcantarilla',     'Limpiar y reparar alcantarilla bloqueada',       CURRENT_TIMESTAMP + INTERVAL '2 days',  false, 2, 4),
  ('Mantenimiento de árboles', 'Poda de árboles en parque este',                 CURRENT_TIMESTAMP + INTERVAL '6 days',  false, 2, 3),
  ('Instalación de señal',     'Colocar nueva señal de tránsito',                CURRENT_TIMESTAMP + INTERVAL '4 days',  true,  2, 2),
  ('Revisión de servicios',    'Inspeccionar servicio eléctrico',                CURRENT_TIMESTAMP + INTERVAL '8 days',  false, 3, 5),
  ('Riego de zonas verdes',    'Riego programado en parques',                    CURRENT_TIMESTAMP + INTERVAL '1 day',   false, 3, 3),
  ('Reparación de válvula',    'Reparación de válvula de agua',                  CURRENT_TIMESTAMP + INTERVAL '9 days',  false, 3, 4),
  ('Barrido de vías',          'Barrido general de las vías asignadas',          CURRENT_TIMESTAMP + INTERVAL '2 days',  false, 3, 4)
) AS v(titulo, descripcion, fecha_vencimiento, estado_completada, usuario_id, sector_id)
WHERE NOT EXISTS (SELECT 1 FROM tareas LIMIT 1);

-- ========== SINCRONIZAR SECUENCIAS ==========
-- CRÍTICO: Después de insertar con IDs explícitos, hay que avanzar la secuencia
-- al valor máximo actual + 1. Si no se hace esto, el próximo INSERT sin ID
-- generará un valor que ya existe y fallará con error de clave duplicada.
SELECT setval(pg_get_serial_sequence('usuarios', 'id'), COALESCE(MAX(id), 1)) FROM usuarios;
SELECT setval(pg_get_serial_sequence('sectores', 'id'), COALESCE(MAX(id), 1)) FROM sectores;
SELECT setval(pg_get_serial_sequence('tareas',   'id'), COALESCE(MAX(id), 1)) FROM tareas;
