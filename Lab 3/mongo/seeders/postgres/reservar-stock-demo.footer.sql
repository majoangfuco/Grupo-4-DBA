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
--
--    Se libera LEAST(bookkeeping, stock_reservado actual) y no el valor
--    del bookkeeping a secas: entre dos corridas, la app pudo haber
--    liberado por su cuenta parte de estas mismas reservas (borrar un
--    ítem del carrito de demo, o un intento de checkout que llegó a
--    llamar a liberar_stock). En ese caso stock_reservado ya bajó, y
--    pedirle a liberar_stock que reste el total original explota con
--    "Stock reservado insuficiente" — dejando el contenedor en bucle de
--    reinicio y el stack a medio levantar. El bookkeeping es un techo de
--    lo que ESTA siembra puede devolver, no una cantidad exacta.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT b.producto_id,
               LEAST(b.cantidad, p.stock_reservado) AS cantidad
        FROM _demo_reservas_activas b
        JOIN producto_entidad p ON p.producto_id = b.producto_id
    LOOP
        IF r.cantidad > 0 THEN
            CALL liberar_stock(r.producto_id, r.cantidad);
        END IF;
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
