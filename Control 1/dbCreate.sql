/*
  Archivo: dbCreate.sql
  Objetivo: crear esquema, tablas, PK, FK, restricciones e indices.
*/

-- Opcional:
-- DROP DATABASE IF EXISTS control1_db;
-- CREATE DATABASE control1_db;
-- USE control1_db;

/* =========================
   1) Tablas de catalogo
   ========================= */

-- Ejemplo base para TIPO_DOC (ajustar si su modelo usa otros nombres)
-- CREATE TABLE TIPO_DOC (
--   ID_TIPO_DOC INT PRIMARY KEY,
--   NOMBRE VARCHAR(50) NOT NULL UNIQUE
-- );

-- Ejemplo base para COMUNA (ajustar a su modelo)
-- CREATE TABLE COMUNA (
--   ID_COMUNA INT PRIMARY KEY,
--   NOMBRE VARCHAR(100) NOT NULL UNIQUE
-- );

/* =========================
   2) Tablas maestras
   ========================= */

-- CREATE TABLE ...

/* =========================
   3) Tablas transaccionales
   ========================= */

-- CREATE TABLE ...

/* =========================
   4) Restricciones e indices
   ========================= */

-- ALTER TABLE ... ADD CONSTRAINT ...
-- CREATE INDEX ...
