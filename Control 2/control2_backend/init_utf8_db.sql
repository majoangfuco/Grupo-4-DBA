
-- Script para reinicializar la base de datos con UTF-8 correcto
-- Ejecutar en PostgreSQL ANTES de iniciar Spring Boot

-- Cerrar conexiones existentes
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'control2_db'
AND pid <> pg_backend_pid();

-- Eliminar la base de datos
DROP DATABASE IF EXISTS control2_db;

-- Crear la base de datos con UTF-8 explícito
CREATE DATABASE control2_db
  ENCODING 'UTF8'
  LC_COLLATE 'es_ES.UTF-8'
  LC_CTYPE 'es_ES.UTF-8'
  TEMPLATE template0;

-- Conectar a la nueva BD y ejecutar extensiones
\c control2_db

-- Crear extensión PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- Verificar la codificación
SELECT datname, encoding, datcollate, datctype 
FROM pg_database 
WHERE datname = 'control2_db';
