/*
  Archivo: runStatements.sql
  Objetivo: incluir las 10 sentencias SQL solicitadas por el grupo.
*/

-- Opcional: seleccionar base de datos
-- USE nombre_base_datos;

-- Ajustes de formato de salida de psql para este script
-- Sin paginador, sin bordes de tabla y solo filas (sin "(n rows)")
\pset pager off

/* =========================
    Consulta 1
    Lista de lugares al que más viajan los chilenos por año (durante los últimos 4 años).
    ========================= */
-- Descripcion:
-- SELECT ...

/* =========================
	Consulta 2
    Lista con las secciones de vuelos más compradas por argentinos.
	========================= */
-- Descripcion:
-- SELECT ...

/* =========================
	Consulta 3
    Lista mensual de países que más gastan en volar (durante los últimos 4 años).
	========================= */

SELECT 
    EXTRACT(YEAR FROM v.Fecha_Vuelo) AS Anio,
    EXTRACT(MONTH FROM v.Fecha_Vuelo) AS Mes,
    c.Nacionalidad AS Pais,
    SUM(co.Precio) AS Gasto_Total
FROM Cliente c
JOIN Pasaje p ON c.Cliente_ID = p.Cliente_ID
JOIN Costo co ON p.Pasaje_ID = co.Pasaje_ID
JOIN Vuelo v ON p.Vuelo_ID = v.Vuelo_ID
WHERE v.Fecha_Vuelo >= CURRENT_DATE - INTERVAL '4 years'
GROUP BY 
    EXTRACT(YEAR FROM v.Fecha_Vuelo), 
    EXTRACT(MONTH FROM v.Fecha_Vuelo), 
    c.Nacionalidad
ORDER BY Anio DESC, Mes DESC, Gasto_Total DESC;

/* =========================
	Consulta 4
    Lista de pasajeros que viajan en “First Class” más de 4 veces al mes.

	========================= */
-- Descripcion:
-- SELECT ...

/* =========================
	Consulta 5
    Avión con menos vuelos.
	========================= */

SELECT 
    a.Avion_ID,
    m.Nombre_Modelo,
    COUNT(v.Vuelo_ID) AS Total_Vuelos
FROM Avion a
JOIN Modelo m ON a.Modelo_ID = m.Modelo_ID
LEFT JOIN Vuelo v ON a.Avion_ID = v.Avion_ID
GROUP BY a.Avion_ID, m.Nombre_Modelo
ORDER BY Total_Vuelos ASC
LIMIT 1;

/* =========================
	Consulta 6
    Lista mensual de pilotos con mayor sueldo (durante los últimos 4 años).
	========================= */
-- Descripcion:
-- SELECT ...

/* =========================
	Consulta 7
    Lista de compañías indicando cuál es el avión que más ha recaudado en los últimos 4 años y cuál es el monto recaudado.
	========================= */
-- Descripcion:
-- SELECT ...


/* =========================
    Consulta 8
    Lista de compañías y total de aviones por año (en los últimos 10 años).
    ========================= */

SELECT 
    c.Nombre_Compania,
    EXTRACT(YEAR FROM a.Fecha_Adquisicion) AS Anio,
    COUNT(a.Avion_ID) AS Total_Aviones
FROM Compania c
LEFT JOIN Avion a
  ON c.Compania_ID = a.Compania_ID
 AND a.Fecha_Adquisicion >= CURRENT_DATE - INTERVAL '10 years'
GROUP BY 
    c.Nombre_Compania,
    EXTRACT(YEAR FROM a.Fecha_Adquisicion)
ORDER BY 
    Anio DESC NULLS LAST,
    Total_Aviones DESC;



/* =========================

    Consulta 9
    Lista anual de compañías que en promedio han pagado más a sus empleados(durante los últimos 10 años).

    ========================= */

-- Descripcion:
-- SELECT ...



/* =========================
    Consulta 10
    Modelo de avión más usado por compañía durante el 2021.
    ========================= */

-- Se utiliza una CTE para calcular la cantidad de vuelos por modelo de avión y compañía

WITH Cantidad_Modelos AS (

    SELECT 
        c.Nombre_Compania,
        m.Nombre_Modelo,
        COUNT(v.Vuelo_ID) AS Cantidad_Vuelos,
        -- RANK() para asignar un rango a cada modelo dentro de cada compañía, ordenado por cantidad de vuelos
        RANK() OVER (PARTITION BY c.Nombre_Compania ORDER BY COUNT(v.Vuelo_ID) DESC) AS Rango
    FROM Compania c
    JOIN Avion a ON c.Compania_ID = a.Compania_ID
    JOIN Modelo m ON a.Modelo_ID = m.Modelo_ID
    JOIN Vuelo v ON a.Avion_ID = v.Avion_ID
    -- Filtrar vuelos del año 2021, por rango ya que es más eficiente que usar YEAR() en la columna de fecha
    WHERE v.Fecha_Vuelo >= DATE '2021-01-01'
      AND v.Fecha_Vuelo < DATE '2022-01-01'
    GROUP BY c.Nombre_Compania, m.Nombre_Modelo

)

SELECT 
    Nombre_Compania,
    Nombre_Modelo,
    Cantidad_Vuelos
FROM Cantidad_Modelos
-- Solo seleccionar el modelo con el rango 1 para cada compañía, es decir, el más usado
WHERE Rango = 1;

