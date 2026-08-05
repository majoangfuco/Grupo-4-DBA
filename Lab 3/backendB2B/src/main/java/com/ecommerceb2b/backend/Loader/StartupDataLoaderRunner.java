package com.ecommerceb2b.backend.Loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StartupDataLoaderRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupDataLoaderRunner.class);

    private final ComunaGeoJsonLoader comunaGeoJsonLoader;
    private final ComunaOverpassLoader comunaOverpassLoader;
    private final UnidadVecinalGeoJsonLoader unidadVecinalGeoJsonLoader;
    private final JdbcTemplate jdbcTemplate;

    public StartupDataLoaderRunner(ComunaGeoJsonLoader comunaGeoJsonLoader,
                                   ComunaOverpassLoader comunaOverpassLoader,
                                   UnidadVecinalGeoJsonLoader unidadVecinalGeoJsonLoader,
                                   JdbcTemplate jdbcTemplate) {
        this.comunaGeoJsonLoader = comunaGeoJsonLoader;
        this.comunaOverpassLoader = comunaOverpassLoader;
        this.unidadVecinalGeoJsonLoader = unidadVecinalGeoJsonLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Verificando si es necesario cargar geometrías reales en la base de datos...");

        // Todo el trabajo (comunas + UV) corre en UN solo hilo en segundo
        // plano, sin importar si las comunas ya estaban listas o no. Antes
        // la rama "comunas ya cargadas" llamaba a unidadVecinalGeoJsonLoader
        // directo en el hilo del CommandLineRunner, bloqueando el arranque
        // de Tomcat; ahora ambas ramas son async por igual. Un solo hilo
        // (no uno por rama) también evita que otro proceso independiente
        // dispare la carga de UV mientras las comunas todavía son los
        // cuadrados placeholder de init.sql — eso corrompía el matching
        // comuna_id ↔ UV vía centroide y dejaba filas huérfanas con el
        // comuna_id equivocado (ver UnidadVecinalStartupLoader, eliminado
        // por esta misma razón).
        Thread hilo = new Thread(this::cargarSiFaltaData, "startup-data-loader");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void cargarSiFaltaData() {
        try {
            // Verificar Comunas (Si hay menos de 52, o si los polígonos tienen menos de 10 puntos, faltan datos reales)
            Integer totalComunas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comuna_entidad", Integer.class);
            Integer comunasConCuadrados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comuna_entidad WHERE ST_NPoints(geom) < 10", Integer.class);

            if (totalComunas == null || totalComunas < 52 || (comunasConCuadrados != null && comunasConCuadrados > 0)) {
                log.info("Faltan comunas o tienen geometrías temporales. Cargando desde el GeoJSON local primero...");

                // Ruta rápida: comunas_rm.geojson ya trae las 52 con geometría
                // real (exportadas una vez desde una base que las tenía
                // cargadas por Overpass), así que no hay red ni rate-limit de
                // por medio — dura segundos en vez de minutos. Overpass solo
                // se usa como respaldo para lo que el archivo no logre cubrir
                // (por ejemplo si el archivo quedara desactualizado o
                // incompleto), y cargarComunas() ya es idempotente por
                // nombre, así que no repite trabajo para las que ya quedaron
                // con geometría real tras la carga local.
                comunaGeoJsonLoader.cargar();

                Integer totalTrasCargaLocal = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM comuna_entidad WHERE ST_NPoints(geom) > 10", Integer.class);
                if (totalTrasCargaLocal == null || totalTrasCargaLocal < 52) {
                    log.info("{}/52 comunas con geometría real tras la carga local, completando el resto vía Overpass...",
                            totalTrasCargaLocal == null ? 0 : totalTrasCargaLocal);
                    comunaOverpassLoader.cargarComunas();
                } else {
                    log.info("Las 52 comunas quedaron con geometría real desde el archivo local, sin necesidad de Overpass.");
                }

                // Como las comunas acaban de cargarse (y antes no estaban para enlazar con los pedidos),
                // actualizamos las entregas para asignarles su comuna_id en base a su ubicación
                jdbcTemplate.update("UPDATE informacion_entrega_entidad ie SET comuna_id = c.id FROM comuna_entidad c WHERE ST_Contains(c.geom, ie.ubicacion)");

                // Y refrescamos las vistas materializadas
                jdbcTemplate.update("REFRESH MATERIALIZED VIEW ventas_por_comuna");
                jdbcTemplate.update("REFRESH MATERIALIZED VIEW ventas_por_distrito");
                log.info("Carga de comunas en segundo plano finalizada y vistas actualizadas.");
            } else {
                log.info("Las comunas ya cuentan con sus polígonos reales.");
            }

            // Unidades Vecinales: solo se llega aquí una vez que las comunas
            // (recién cargadas arriba, o ya existentes) tienen geometría
            // real — nunca antes, para que el matching por centroide en
            // UnidadVecinalGeoJsonLoader caiga siempre contra el polígono
            // correcto de comuna_entidad.
            // Solo cargamos si hay menos de 2000 (deberían ser ~2310) para no re-procesar todo siempre
            Integer totalUnidadesVecinales = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM unidad_vecinal_entidad", Integer.class);
            if (totalUnidadesVecinales != null && totalUnidadesVecinales < 2000) {
                log.info("Cargando el archivo GeoJSON de unidades vecinales ahora que las comunas existen...");
                unidadVecinalGeoJsonLoader.cargar();

                // Establecemos 15 como protegidas para prueba visual
                jdbcTemplate.update("UPDATE unidad_vecinal_entidad SET es_zona_protegida = TRUE WHERE id IN (SELECT id FROM unidad_vecinal_entidad LIMIT 15)");
                log.info("Se han marcado 15 unidades vecinales reales como zonas protegidas de prueba.");
            } else {
                log.info("Las unidades vecinales ya están cargadas (Total: {}).", totalUnidadesVecinales);
            }
        } catch (Exception e) {
            log.error("Ocurrió un error al intentar verificar o cargar las geometrías iniciales: ", e);
        }

        log.info("Verificación de geometrías espaciales finalizada.");
    }
}
