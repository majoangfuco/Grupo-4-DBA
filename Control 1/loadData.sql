-- 1. Secciones (Requerido para consultas de clases de vuelo)
INSERT INTO "Seccion" ("Seccion_ID", "Tipo_Seccion") VALUES 
(1, 'Economy'), 
(2, 'Premium economy'), 
(3, 'Business'), 
(4, 'First class');

-- 2. Compañías
INSERT INTO "Compania" ("Compania_ID", "Nombre_Compania") VALUES 
(1, 'LATAM'), 
(2, 'Aerolineas Argentinas'), 
(3, 'Iberia');

-- 3. Modelos de Avión
INSERT INTO "Modelo" ("Modelo_ID", "Nombre_Modelo") VALUES 
(1, 'Boeing 787'), 
(2, 'Airbus A320'), 
(3, 'Boeing 747');

-- 4. Clientes (Nacionalidades estratégicas para filtrar)
INSERT INTO "Cliente" ("Cliente_ID", "Identificador_Cliente", "Nombre_Cliente", "Correo", "Nacionalidad") VALUES 
(1, '18.456.789-0', 'Javiera Iturra', 'j.iturra@mail.cl', 'Chileno'),
(2, '20.111.222-K', 'Carlos Gardel', 'carlos@mail.ar', 'Argentino'),
(3, 'PAS887766', 'John Smith', 'john@mail.com', 'Estadounidense'),
(4, '15.333.444-5', 'Pedro Pablo', 'pp@mail.cl', 'Chileno');

-- 5. Empleados
INSERT INTO "Empleado" ("Empleado_ID", "Compania_ID", "Puesto_Empleo", "Nombre_Empleado", "Identificador_Empleado") VALUES 
(1, 1, 'Piloto', 'Roberto Gomez', 'EMP001'),
(2, 1, 'Copiloto', 'Andrea Marin', 'EMP002'),
(3, 2, 'Piloto', 'Sebastian Vettel', 'EMP003'),
(4, 3, 'Piloto', 'Fernando Alonso', 'EMP004');

-- 6. Sueldos (Separados según tu nueva estructura)
INSERT INTO "Sueldo" ("Sueldo_ID", "Empleado_ID", "Monto_Sueldo") VALUES 
(1, 1, 5500000.00),
(2, 2, 3200000.00),
(3, 3, 4800000.00),
(4, 4, 6000000.00);

-- 7. Aviones (Fechas de adquisición variadas)
INSERT INTO "Avion" ("Avion_ID", "Compania_ID", "Modelo_ID", "Fecha_Adquisicion") VALUES 
(1, 1, 1, '2012-05-20 10:00:00'), -- Avión antiguo (>10 años)
(2, 2, 2, '2020-11-15 09:00:00'), 
(3, 1, 2, '2023-01-10 14:30:00');

-- 8. Vuelos
INSERT INTO "Vuelo" ("Vuelo_ID", "Avion_ID", "Compania_ID", "Origen", "Destino", "Fecha_Vuelo") VALUES 
(1, 1, 1, 'Santiago', 'Miami', '2024-03-15 22:00:00'), 
(2, 2, 2, 'Buenos Aires', 'Madrid', '2024-03-18 13:00:00'), 
(3, 1, 1, 'Santiago', 'Lima', '2024-03-20 08:00:00'),
(4, 3, 1, 'Santiago', 'Punta Arenas', '2024-04-01 07:00:00');

-- 9. Pasajes (Relacionando con Secciones)
-- Importante: El Cliente 1 viaja varias veces para cumplir con las consultas de frecuencia
INSERT INTO "Pasaje" ("Pasaje_ID", "Seccion_ID", "Cliente_ID", "Vuelo_ID") VALUES 
(1, 4, 1, 1), -- Cliente 1 en First Class
(2, 1, 2, 2), -- Cliente 2 en Economy
(3, 4, 1, 3), -- Cliente 1 en First Class
(4, 4, 1, 4), -- Cliente 1 en First Class
(5, 3, 4, 1); -- Cliente 4 en Business

-- 10. Costos
INSERT INTO "Costo" ("Costo_ID", "Pasaje_ID", "Precio") VALUES 
(1, 1, 2500.00), 
(2, 2, 850.00), 
(3, 3, 2200.00),
(4, 4, 1200.00),
(5, 5, 1800.00);

-- 11. Emp_Vuelo (Asignación de tripulación a vuelos)
INSERT INTO "Emp_Vuelo" ("Emp_Vuelo_ID", "Vuelo_ID", "Empleado_ID") VALUES 
(1, 1, 1), 
(2, 1, 2),
(3, 2, 3);

-- 12. Cliente_Comp (Clientes frecuentes por compañía)
INSERT INTO "Cliente_Comp" ("Cliente_Comp_ID", "Cliente_ID", "Compania_ID") VALUES 
(1, 1, 1), 
(2, 2, 2);

-- 13. Cliente_Vuelo
INSERT INTO "Cliente_Vuelo" ("Cliente_Vuelo_ID", "Cliente_ID", "Vuelo_ID") VALUES 
(1, 1, 1), 
(2, 2, 2),
(3, 1, 3);