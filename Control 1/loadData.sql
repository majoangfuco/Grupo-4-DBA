-- Insertar datos en Tablas Padre
INSERT INTO Cliente (Cliente_ID, Identificador_Cliente, Nombre_Cliente, Correo, Nacionalidad) VALUES
(1, '11.111.111-1', 'Pedro Pascal', 'pedro@mail.com', 'Chile'),
(2, '22.222.222-2', 'Lionel Messi', 'lio@mail.com', 'Argentina'),
(3, '33.333.333-3', 'Ricardo Darin', 'ricardo@mail.com', 'Argentina'),
(4, '44.444.444-4', 'Mon Laferte', 'mon@mail.com', 'Chile');

INSERT INTO Compañia (Compania_ID, Nombre_Compañia) VALUES
(1, 'LATAM Airlines'),
(2, 'Sky Airline');

INSERT INTO Modelo (Modelo_ID, Nombre_Modelo) VALUES
(1, 'Boeing 787 Dreamliner'),
(2, 'Airbus A320neo'),
(3, 'Boeing 737 MAX');

INSERT INTO Sección (Seccion_ID, Tipo_Seccion) VALUES
(1, 'Economy'),
(2, 'Premium economy'),
(3, 'Business'),
(4, 'First class');

-- Insertar Aviones y Vuelos
INSERT INTO Avión (Avion_ID, Compania_ID, Modelo_ID, Fecha_Adquisicion) VALUES
(1, 1, 1, '2020-01-15 00:00:00'),
(2, 2, 2, '2021-03-10 00:00:00'),
(3, 1, 3, '2022-05-20 00:00:00'); 

INSERT INTO Vuelo (Vuelo_ID, Avion_ID, Compania_ID, Origen, Destino, Fecha_Vuelo) VALUES
(1, 1, 1, 'Santiago', 'Miami', '2023-08-15 10:00:00'),
(2, 1, 1, 'Miami', 'Santiago', '2023-08-20 15:00:00'),
(3, 2, 2, 'Buenos Aires', 'Lima', '2024-01-10 09:00:00'),
(4, 3, 1, 'Santiago', 'Sao Paulo', '2024-02-05 12:00:00'),
(5, 1, 1, 'Santiago', 'Bogota', '2021-06-15 10:00:00'); -- Vuelo agregado para probar la Sentencia 10

-- Insertar Pasajes y Costos
INSERT INTO Pasaje (Pasaje_ID, Seccion_ID, Cliente_ID, Vuelo_ID) VALUES
(1, 1, 1, 1), 
(2, 3, 2, 3), 
(3, 4, 3, 3), 
(4, 2, 4, 2); 

INSERT INTO Costo (Costo_ID, Pasaje_ID, Precio) VALUES
(1, 1, 500.00),
(2, 2, 1200.00), 
(3, 3, 2500.00), 
(4, 4, 800.00);
