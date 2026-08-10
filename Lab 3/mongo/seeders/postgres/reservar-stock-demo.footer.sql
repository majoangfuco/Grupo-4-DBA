-- ═══════════════════════════════════════════════════════════════
-- reservar-stock-demo.footer.sql
--
-- Tercera parte del script de 3 partes — ver reservar-stock-demo.header.sql
-- para el contexto completo. En el momento en que se ejecuta esto,
-- _reservas_objetivo (temp) ya tiene las filas generadas por
-- generar-reservas-stock-postgres.js, y _demo_reservas_activas (persistente)
-- todavía tiene lo que quedó reservado por la corrida ANTERIOR de este
-- mismo mecanismo (vacía la primera vez).
--
-- Estrategia idempotente ("libera lo viejo, reserva lo nuevo"): así se
-- puede correr en cada `docker compose up` (mongo-init se reejecuta
-- seguido en este proyecto, ver README) sin ir sumando reservas de más
-- cada vez ni romper el CHECK stock_reservado <= stock. Nunca toca
-- reservas de carritos reales de otros clientes sobre el mismo producto:
-- solo libera exactamente lo que ESTE bookkeeping registró la vez
-- anterior.
--
-- Usa CALL reservar_stock/liberar_stock — los MISMOS procedimientos que
-- llama CarritoProductoRepositorio (reservarStock/liberarStock) desde el
-- backend real — en vez de reimplementar el UPDATE a mano, para heredar
-- automáticamente cualquier cambio futuro a esa lógica.
-- ═══════════════════════════════════════════════════════════════

-- 1. Libera lo que esta siembra tenía reservado en la corrida anterior.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT producto_id, cantidad FROM _demo_reservas_activas LOOP
        CALL liberar_stock(r.producto_id, r.cantidad);
    END LOOP;
END $$;

-- 2. Reserva el objetivo de esta corrida (mismo procedimiento que usa
--    "agregar al carrito" en el flujo real).
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT producto_id, cantidad FROM _reservas_objetivo LOOP
        CALL reservar_stock(r.producto_id, r.cantidad);
    END LOOP;
END $$;

-- 3. Deja el bookkeeping listo para que la PRÓXIMA corrida sepa qué
--    liberar antes de volver a reservar.
TRUNCATE _demo_reservas_activas;
INSERT INTO _demo_reservas_activas (producto_id, cantidad)
    SELECT producto_id, cantidad FROM _reservas_objetivo;

COMMIT;
