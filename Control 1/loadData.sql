/*
  Archivo: loadData.sql
  Objetivo: cargar datos de prueba (dummy) con valores validos para tablas catalogo.
  Nota: ajuste nombres de columnas segun su modelo.
*/

-- USE control1_db;

START TRANSACTION;

/* =========================
   1) Tablas catalogo obligatorias
   ========================= */

-- TIPO_DOC: valores esperados por enunciado
-- Ajuste ID_TIPO_DOC/NOMBRE si usan otras columnas
INSERT INTO TIPO_DOC (ID_TIPO_DOC, NOMBRE)
VALUES
  (1, 'Boleta'),
  (2, 'Factura');

-- COMUNA: incluir valores validos que seran referenciados
-- Ajuste ID_COMUNA/NOMBRE segun su modelo
INSERT INTO COMUNA (ID_COMUNA, NOMBRE)
VALUES
  (1, 'Santiago'),
  (2, 'Providencia'),
  (3, 'Nunoa'),
  (4, 'Maipu'),
  (5, 'La Florida');

/* =========================
   2) Resto de tablas
   ========================= */

-- Ejemplo:
-- INSERT INTO CLIENTE (ID_CLIENTE, NOMBRE, ID_COMUNA) VALUES
--   (1, 'Cliente Dummy 1', 1),
--   (2, 'Cliente Dummy 2', 2);

-- INSERT INTO DOCUMENTO (ID_DOCUMENTO, ID_TIPO_DOC, ID_CLIENTE, FECHA_EMISION) VALUES
--   (1, 1, 1, '2026-04-14'),
--   (2, 2, 2, '2026-04-14');

COMMIT;
