package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.ReporteVentasEntidad;
import com.ecommerceb2b.backend.Repository.ReporteVentasRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReporteVentasServicio {

    private final ReporteVentasRepositorio reporteVentasRepositorio;

    public ReporteVentasServicio(ReporteVentasRepositorio reporteVentasRepositorio) {
        this.reporteVentasRepositorio = reporteVentasRepositorio;
    }

    /**
     * Obtiene todos los registros de ventas mensuales por categoría
     * Útil para el dashboard general que muestra todos los datos consolidados
     */
    @Transactional(readOnly = true)
    public List<ReporteVentasEntidad> obtenerTodosLosReportes() {
        return reporteVentasRepositorio.obtenerTodosLosReportes();
    }

    /**
     * Filtra reportes por mes y año específico (formato YYYY-MM)
     * Útil para análisis de un mes particular
     */
    @Transactional(readOnly = true)
    public List<ReporteVentasEntidad> obtenerPorMesAno(String mesAno) {
        if (mesAno == null || mesAno.trim().isEmpty()) {
            throw new IllegalArgumentException("El mes y año son obligatorios");
        }
        // Validar formato YYYY-MM
        if (!mesAno.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("El formato debe ser YYYY-MM");
        }
        return reporteVentasRepositorio.obtenerPorMesAno(mesAno);
    }

    /**
     * Filtra reportes por categoría de producto
     * Útil para análisis de desempeño por línea de producto
     */
    @Transactional(readOnly = true)
    public List<ReporteVentasEntidad> obtenerPorCategoria(String nombreCategoria) {
        if (nombreCategoria == null || nombreCategoria.trim().isEmpty()) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        return reporteVentasRepositorio.obtenerPorCategoria(nombreCategoria.trim());
    }

    /**
     * Obtiene reportes de un año completo
     * Útil para análisis anuales
     */
    @Transactional(readOnly = true)
    public List<ReporteVentasEntidad> obtenerPorAnio(Integer anio) {
        if (anio == null || anio < 2000 || anio > 2100) {
            throw new IllegalArgumentException("El año debe estar entre 2000 y 2100");
        }
        return reporteVentasRepositorio.obtenerPorAnio(anio);
    }

    /**
     * Obtiene el total consolidado de todas las ventas
     * Útil para KPIs generales del dashboard
     */
    @Transactional(readOnly = true)
    public ReporteVentasEntidad obtenerTotalConsolidado() {
        return reporteVentasRepositorio.obtenerTotalConsolidado();
    }

    /**
     * Refresca la vista materializada para obtener datos más recientes
     * Se debe ejecutar después de cambios importantes en órdenes o productos
     */
    @Transactional
    public void refrescarReportes() {
        reporteVentasRepositorio.refrescarVistaMatrializada();
    }
}
