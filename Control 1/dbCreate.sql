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

-- 1. Tablas Independientes 
CREATE TABLE "Compañia" (
  "Compania_ID" SERIAL PRIMARY KEY,
  "Nombre_Compañia" VARCHAR(100)
);

CREATE TABLE "Modelo" (
  "Modelo_ID" SERIAL PRIMARY KEY,
  "Nombre_Modelo" VARCHAR(50)
);

CREATE TABLE "Sección" (
  "Seccion_ID" SERIAL PRIMARY KEY,
  "Tipo_Seccion" VARCHAR(50)
);

CREATE TABLE "Cliente" (
  "Cliente_ID" SERIAL PRIMARY KEY,
  "Identificador_Cliente" VARCHAR(50),
  "Nombre_Cliente" VARCHAR(100),
  "Correo" VARCHAR(100),
  "Nacionalidad" VARCHAR(50)
);

-- 2. Tablas Dependientes 
CREATE TABLE "Avión" (
  "Avion_ID" SERIAL PRIMARY KEY,
  "Compania_ID" INT REFERENCES "Compañia"("Compania_ID"),
  "Modelo_ID" INT REFERENCES "Modelo"("Modelo_ID"),
  "Fecha_Adquisicion" TIMESTAMP  
);

CREATE TABLE "Empleado" (
  "Empleado_ID" SERIAL PRIMARY KEY,
  "Compania_ID" INT REFERENCES "Compañia"("Compania_ID"),
  "Puesto_Empleo" VARCHAR(50),
  "Nombre_Empleado" VARCHAR(100),
  "Identificador_Empleado" VARCHAR(50)
);

-- 3. Tablas Dependientes 
CREATE TABLE "Vuelo" (
  "Vuelo_ID" SERIAL PRIMARY KEY,
  "Avion_ID" INT REFERENCES "Avión"("Avion_ID"),
  "Compania_ID" INT REFERENCES "Compañia"("Compania_ID"),
  "Origen" VARCHAR(50),
  "Destino" VARCHAR(50),
  "Fecha_Vuelo" TIMESTAMP 
);

CREATE TABLE "Sueldo" (
  "Sueldo_ID" SERIAL PRIMARY KEY,
  "Empleado_ID" INT REFERENCES "Empleado"("Empleado_ID"),
  "Monto_Sueldo" DECIMAL(10,2)
);

-- 4. Tablas de Relación 
CREATE TABLE "Pasaje" (
  "Pasaje_ID" SERIAL PRIMARY KEY,
  "Seccion_ID" INT REFERENCES "Sección"("Seccion_ID"),
  "Cliente_ID" INT REFERENCES "Cliente"("Cliente_ID"),
  "Vuelo_ID" INT REFERENCES "Vuelo"("Vuelo_ID"),
  "Precio" DECIMAL(10,2)
);

CREATE TABLE "Emp_Vuelo" (
  "Emp_Vuelo_ID" SERIAL PRIMARY KEY,
  "Vuelo_ID" INT REFERENCES "Vuelo"("Vuelo_ID"),
  "Empleado_ID" INT REFERENCES "Empleado"("Empleado_ID")
);