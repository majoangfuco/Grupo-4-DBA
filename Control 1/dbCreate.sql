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

CREATE TABLE "Vuelo" (
  "Vuelo_ID" PK,
  "Avión_ID" FK,
  "Compania_ID" FK,
  "Origen" VARCHAR(50),
  "Destino" VARCHAR(50),
  "Fecha_Vuelo" DATETIME
);

CREATE TABLE "Cliente_Vuelo" (
  "Cliente_Vuelo_ID" PK,
  "Cliente_ID" FK,
  "Vuelo_ID" FK,
  CONSTRAINT "FK_Cliente_Vuelo_Cliente_ID"
    FOREIGN KEY ("Cliente_ID")
      REFERENCES "Vuelo"("Compania_ID")
);

CREATE TABLE "Empleado" (
  "Empleado_ID" PK,
  "Compania_ID" FK,
  "Puesto_Empleo" VARCHAR(50),
  "Nombre_Empleado" VARCHAR(50),
  "Identificador_Empleado" VARCHAR(50)
);

CREATE TABLE "Sueldo" (
  "Sueldo_ID" PK,
  "Empleado_ID" FK,
  "Monto_Sueldo" DECIMAL(10,2)
);

CREATE TABLE "Cliente" (
  "Cliente_ID" PK,
  "Identificador_Cliente" VARCHAR(50),
  "Nombre_Cliente" VARCHAR(50),
  "Correo" VARCHAR(50),
  "Nacionalidad" VARCHAR(50)
);

CREATE TABLE "Sección" (
  "Seccion_ID" PK,
  "Tipo_Seccion" VARCHAR(50)
);

CREATE TABLE "Pasaje" (
  "Pasaje_ID" PK,
  "Seccion_ID" FK,
  "Cliente_ID" FK,
  "Vuelo_ID " FK,
  "Costo_ID" FK,
  CONSTRAINT "FK_Pasaje_Seccion_ID"
    FOREIGN KEY ("Seccion_ID")
      REFERENCES "Cliente"("Nacionalidad"),
  CONSTRAINT "FK_Pasaje_Vuelo_ID "
    FOREIGN KEY ("Vuelo_ID ")
      REFERENCES "Sección"("Seccion_ID")
);

CREATE TABLE "Costo" (
  "Costo_ID" PK,
  "Pasaje_ID" FK,
  "Precio" DECIMAL(10,2)
);

CREATE TABLE "Cliente_Comp" (
  "Cliente_Comp_ID" PK,
  "Cliente_ID" FK,
  "Compañia_ID" FK,
  CONSTRAINT "FK_Cliente_Comp_Cliente_ID"
    FOREIGN KEY ("Cliente_ID")
      REFERENCES "Cliente"("Nombre_Cliente")
);

CREATE TABLE "Modelo" (
  "Modelo _ID" PK,
  "Nombre_Modelo" VARCHAR(50)
);

CREATE TABLE "Emp_Vuelo" (
  "Emp_Vuelo" PK,
  "Vuelo_ID" FK,
  "Empleado_ID" FK,
  CONSTRAINT "FK_Emp_Vuelo_Vuelo_ID"
    FOREIGN KEY ("Vuelo_ID")
      REFERENCES "Vuelo"("Avión_ID"),
  CONSTRAINT "FK_Emp_Vuelo_Vuelo_ID"
    FOREIGN KEY ("Vuelo_ID")
      REFERENCES "Empleado"("Empleado_ID")
);

CREATE TABLE "Compañia" (
  "Compania_ID" PK,
  "Nombre_Compañia" VARCHAR(50)
);

CREATE TABLE "Avión" (
  "Avion_ID" PK,
  "Compania_ID" FK,
  "Modelo_ID" FK,
  "Fecha_Adquisicion" DATETIME,
  CONSTRAINT "FK_Avión_Avion_ID"
    FOREIGN KEY ("Avion_ID")
      REFERENCES "Modelo"("Modelo _ID"),
  CONSTRAINT "FK_Avión_Avion_ID"
    FOREIGN KEY ("Avion_ID")
      REFERENCES "Compañia"("Compania_ID")
);

