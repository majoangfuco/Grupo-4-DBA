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

-- ========== CARGAR DATOS INICIALES DE PRUEBA ==========
-- Limpiar datos existentes (solo en ambiente de desarrollo)
DELETE FROM tareas;
DELETE FROM sectores;
DELETE FROM usuarios;

-- Resetear secuencias de ID a valores seguros
ALTER SEQUENCE usuarios_id_seq RESTART WITH 1;
ALTER SEQUENCE sectores_id_seq RESTART WITH 1;
ALTER SEQUENCE tareas_id_seq RESTART WITH 1;

-- ========== USUARIOS INICIALES ==========
-- Contraseña: 123456 (encriptada con BCrypt)
INSERT INTO usuarios (id, username, password, ubicacion_geografica) VALUES
(1, 'usuario1', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-74.0060 40.7128)', 4326)),
(2, 'usuario2', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-74.0070 40.7140)', 4326)),
(3, 'usuario3', '$2a$10$iRBafWlCUPLnGSzSx8jjcum3HzVQoiXoT3m/cUeC/fBMNFI1rQW0a', ST_GeomFromText('POINT(-74.0050 40.7115)', 4326));

-- ========== SECTORES INICIALES ==========
INSERT INTO sectores (id, nombre, ubicacion_espacial) VALUES
(1, 'Construcción Centro', ST_GeomFromText('POINT(-74.0065 40.7130)', 4326)),
(2, 'Reparación Semáforos Norte', ST_GeomFromText('POINT(-74.0050 40.7150)', 4326)),
(3, 'Mantenimiento Parques', ST_GeomFromText('POINT(-74.0080 40.7120)', 4326)),
(4, 'Limpieza Vías Este', ST_GeomFromText('POINT(-74.0040 40.7125)', 4326)),
(5, 'Servicios Públicos Oeste', ST_GeomFromText('POINT(-74.0090 40.7135)', 4326));

-- ========== TAREAS INICIALES ==========
-- Usuario 1: 4 tareas (2 pendientes, 2 completadas)
INSERT INTO tareas (titulo, descripcion, fecha_vencimiento, estado_completada, usuario_id, sector_id) VALUES
('Reparar acera', 'Parchar agujeros en la calle principal', CURRENT_TIMESTAMP + INTERVAL '5 days', false, 1, 1),
('Cambiar poste de luz', 'Cambiar poste dañado en esquina', CURRENT_TIMESTAMP + INTERVAL '3 days', false, 1, 2),
('Limpiar parque central', 'Recolectar basura y maleza', CURRENT_TIMESTAMP + INTERVAL '7 days', true, 1, 3),
('Pintar banqueta', 'Pintar líneas de estacionamiento', CURRENT_TIMESTAMP + INTERVAL '10 days', true, 1, 1);

-- Usuario 2: 3 tareas (2 pendientes, 1 completada)
INSERT INTO tareas (titulo, descripcion, fecha_vencimiento, estado_completada, usuario_id, sector_id) VALUES
('Reparar alcantarilla', 'Limpiar y reparar alcantarilla bloqueada', CURRENT_TIMESTAMP + INTERVAL '2 days', false, 2, 4),
('Mantenimiento de árboles', 'Poda de árboles en parque este', CURRENT_TIMESTAMP + INTERVAL '6 days', false, 2, 3),
('Instalación de señal', 'Colocar nueva señal de tránsito', CURRENT_TIMESTAMP + INTERVAL '4 days', true, 2, 2);

-- Usuario 3: 4 tareas (todas pendientes inicialmente)
INSERT INTO tareas (titulo, descripcion, fecha_vencimiento, estado_completada, usuario_id, sector_id) VALUES
('Revisión de servicios', 'Inspeccionar servicio eléctrico', CURRENT_TIMESTAMP + INTERVAL '8 days', false, 3, 5),
('Riego de zonas verdes', 'Riego programado en parques', CURRENT_TIMESTAMP + INTERVAL '1 day', false, 3, 3),
('Reparación de válvula', 'Reparación de válvula de agua', CURRENT_TIMESTAMP + INTERVAL '9 days', false, 3, 4),
('Barrido de vías', 'Barrido general de las vías asignadas', CURRENT_TIMESTAMP + INTERVAL '2 days', false, 3, 4);
