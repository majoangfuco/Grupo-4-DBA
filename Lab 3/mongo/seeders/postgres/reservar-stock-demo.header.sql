-- ═══════════════════════════════════════════════════════════════
-- reservar-stock-demo.header.sql
--
-- Primera mitad de un script de 3 partes que NO se corre solo. El
-- servicio `postgres-stock-seed` de docker-compose.yml lo concatena así:
--
--   cat reservar-stock-demo.header.sql \
--       <reservas-objetivo.sql generado por generar-reservas-stock-postgres.js> \
--       reservar-stock-demo.footer.sql \
--   | psql ...
--
-- Ver el comentario de cabecera de generar-reservas-stock-postgres.js
-- para el porqué completo de todo este mecanismo (en resumen: los
-- carritos de demo se siembran directo en Mongo, sin pasar por el código
-- Java que reserva stock en Postgres; sin esto, borrar un ítem de un
-- carrito de demo o solicitar su orden falla con "Stock reservado
-- insuficiente").
--
-- Este archivo solo prepara las tablas: la tabla de bookkeeping
-- persistente (_demo_reservas_activas, para saber qué liberar en la
-- PRÓXIMA corrida) y la tabla temporal donde caen los INSERT generados
-- (_reservas_objetivo, el objetivo de ESTA corrida).
-- ═══════════════════════════════════════════════════════════════

BEGIN;

CREATE TABLE IF NOT EXISTS _demo_reservas_activas (
    producto_id BIGINT PRIMARY KEY,
    cantidad    INT NOT NULL
);

CREATE TEMP TABLE _reservas_objetivo (
    producto_id BIGINT PRIMARY KEY,
    cantidad    INT NOT NULL
) ON COMMIT DROP;

-- ─── A partir de acá: filas generadas EN VIVO por ─────────────────────
-- generar-reservas-stock-postgres.js desde el estado actual de
-- mongo `carritos`. No editar a mano ni duplicar estos valores en otro
-- lado — si hace falta cambiar cuánto reserva un carrito de demo, se
-- cambia en mongo/seeders/ordenes-carritos-seed.js y esto se regenera
-- solo en el próximo `docker compose up`.
