package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Repository.LogisticaMapaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Refresca ventas_por_comuna / ventas_por_distrito periódicamente para que
// el choropleth no dependa de que un admin dispare el refresco a mano.
// No hay otro @Scheduled en el proyecto todavía, así que este es el primero;
// el intervalo (6h) es conservador para no competir con tráfico de checkout.
@Component
public class LogisticaMapaScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogisticaMapaScheduler.class);

    private final LogisticaMapaRepositorio repositorio;

    public LogisticaMapaScheduler(LogisticaMapaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Scheduled(fixedRate = 6, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    public void refrescarVistasVentas() {
        try {
            repositorio.refrescar();
            log.info("Refresco automático de ventas_por_comuna/ventas_por_distrito completado");
        } catch (Exception e) {
            log.error("Falló el refresco automático de las vistas de ventas por comuna/distrito", e);
        }
    }
}
