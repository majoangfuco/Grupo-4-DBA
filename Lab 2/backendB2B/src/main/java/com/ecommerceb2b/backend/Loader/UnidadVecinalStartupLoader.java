package com.ecommerceb2b.backend.Loader;

import com.ecommerceb2b.backend.Repository.UnidadVecinalRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Antes, la carga de UnidadVecinalGeoJsonLoader dependía de que un ADMIN
 * disparara POST /api/admin/unidades-vecinales/cargar a mano en cada base de
 * datos nueva. En la práctica eso hacía que el mapa de Logística y de
 * Gestión de Zonas Protegidas solo se viera con polígonos en las máquinas
 * donde alguien ya lo había ejecutado alguna vez — un clone nuevo del repo
 * no revive datos que solo viven en el volumen de Postgres local.
 *
 * Este runner corre una sola vez al arrancar, y solo si la tabla está vacía
 * (chequeo barato, evita re-parsear el GeoJSON de 17MB en cada restart una
 * vez que ya se cargó). El endpoint POST manual se mantiene para recargar
 * a mano si se actualiza el archivo fuente.
 */
@Component
public class UnidadVecinalStartupLoader {

    private static final Logger log = LoggerFactory.getLogger(UnidadVecinalStartupLoader.class);

    private final UnidadVecinalGeoJsonLoader loader;
    private final UnidadVecinalRepositorio repositorio;

    public UnidadVecinalStartupLoader(UnidadVecinalGeoJsonLoader loader, UnidadVecinalRepositorio repositorio) {
        this.loader = loader;
        this.repositorio = repositorio;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargarSiFaltaData() {
        try {
            if (repositorio.contar() > 0) {
                log.info("unidad_vecinal_entidad ya tiene datos, se omite la carga automática desde GeoJSON");
                return;
            }
            log.info("unidad_vecinal_entidad está vacía, cargando automáticamente desde el GeoJSON fuente...");
            UnidadVecinalGeoJsonLoader.ResultadoCarga resultado = loader.cargar();
            log.info("Carga automática de unidades vecinales completada: {} procesadas, {} descartadas de {} leídas",
                    resultado.procesadas, resultado.descartadas, resultado.totalLeidas);
        } catch (Exception e) {
            // No debe tumbar el arranque de la app: si falla, el mapa queda
            // sin UVs pero el resto del sistema sigue funcionando, y el
            // endpoint POST manual sigue disponible para reintentar.
            log.error("Falló la carga automática de unidades vecinales al arrancar", e);
        }
    }
}
