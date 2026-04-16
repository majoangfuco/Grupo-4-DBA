/*
  Archivo: dbCreate.sql
  Objetivo: crear esquema, tablas, PK, FK, restricciones e indices.
*/



-- Reinicio de esquema (PostgreSQL): borra tablas en orden por dependencias
DROP TABLE IF EXISTS Costo CASCADE;
DROP TABLE IF EXISTS Pasaje CASCADE;
DROP TABLE IF EXISTS Cliente_Vuelo CASCADE;
DROP TABLE IF EXISTS Cliente_Comp CASCADE;
DROP TABLE IF EXISTS Emp_Vuelo CASCADE;
DROP TABLE IF EXISTS Sueldo CASCADE;
DROP TABLE IF EXISTS Vuelo CASCADE;
DROP TABLE IF EXISTS Avion CASCADE;
DROP TABLE IF EXISTS Empleado CASCADE;
DROP TABLE IF EXISTS Seccion CASCADE;
DROP TABLE IF EXISTS Modelo CASCADE;
DROP TABLE IF EXISTS Cliente CASCADE;
DROP TABLE IF EXISTS Compania CASCADE;

/* =========================
   1) Tablas de catalogo
   ========================= */

-- Tablas Maestras
CREATE TABLE Cliente (
  Cliente_ID INT PRIMARY KEY Not NULL,
  Identificador_Cliente VARCHAR(50) Not NULL,
  Nombre_Cliente VARCHAR(50) Not NULL,
  Correo VARCHAR(50),
  Nacionalidad VARCHAR(50)
);

CREATE TABLE Compania (
  Compania_ID INT PRIMARY KEY Not NULL,
  Nombre_Compania VARCHAR(50) Not NULL
);

CREATE TABLE Modelo (
  Modelo_ID INT PRIMARY KEY Not NULL,
  Nombre_Modelo VARCHAR(50) Not NULL
);

CREATE TABLE Seccion (
  Seccion_ID INT PRIMARY KEY Not NULL,
  Tipo_Seccion VARCHAR(50) Not NULL
);

-- Tablas con Relaciones
CREATE TABLE Empleado (
  Empleado_ID INT PRIMARY KEY Not NULL,
  Compania_ID INT REFERENCES Compania(Compania_ID) Not NULL,
  Puesto_Empleo VARCHAR(50) Not NULL,
  Nombre_Empleado VARCHAR(50) Not NULL,
  Identificador_Empleado VARCHAR(50) Not NULL 
);

CREATE TABLE Avion (
  Avion_ID INT PRIMARY KEY Not NULL,
  Compania_ID INT REFERENCES Compania(Compania_ID) NOT NULL,
  Modelo_ID INT REFERENCES Modelo(Modelo_ID) NOT NULL,
  Fecha_Adquisicion TIMESTAMP NOT NULL
);

CREATE TABLE Vuelo (
  Vuelo_ID INT PRIMARY KEY Not NULL,
  Avion_ID INT REFERENCES Avion(Avion_ID) NOT NULL,
  Compania_ID INT REFERENCES Compania(Compania_ID) NOT NULL,
  Origen VARCHAR(50) Not NULL,
  Destino VARCHAR(50) Not NULL,
  Fecha_Vuelo TIMESTAMP Not NULL
);

CREATE TABLE Pasaje (
  Pasaje_ID INT PRIMARY KEY Not NULL,
  Seccion_ID INT REFERENCES Seccion(Seccion_ID) Not NULL,
  Cliente_ID INT REFERENCES Cliente(Cliente_ID) Not NULL,
  Vuelo_ID INT REFERENCES Vuelo(Vuelo_ID) Not NULL
);

CREATE TABLE Costo (
  Costo_ID INT PRIMARY KEY Not NULL,
  Pasaje_ID INT REFERENCES Pasaje(Pasaje_ID) Not NULL,
  Precio DECIMAL(10,2) Not NULL
);

CREATE TABLE Sueldo (
  Sueldo_ID INT PRIMARY KEY Not NULL,
  Empleado_ID INT REFERENCES Empleado(Empleado_ID) Not NULL,
  Monto_Sueldo DECIMAL(10,2) Not NULL,
  Fecha_Pago TIMESTAMP Not NULL
);

CREATE TABLE Emp_Vuelo (
  Emp_Vuelo_ID INT PRIMARY KEY Not NULL,
  Vuelo_ID INT REFERENCES Vuelo(Vuelo_ID) Not NULL,
  Empleado_ID INT REFERENCES Empleado(Empleado_ID) Not NULL
);

CREATE TABLE Cliente_Comp (
  Cliente_Comp_ID INT PRIMARY KEY Not NULL,
  Cliente_ID INT REFERENCES Cliente(Cliente_ID) Not NULL,
  Compania_ID INT REFERENCES Compania(Compania_ID) Not NULL
);

CREATE TABLE Cliente_Vuelo (
  Cliente_Vuelo_ID INT PRIMARY KEY,
  Cliente_ID INT REFERENCES Cliente(Cliente_ID),
  Vuelo_ID INT REFERENCES Vuelo(Vuelo_ID)
);