package com.ecommerceb2b.backend.Entities;

import java.math.BigDecimal;

/**
 * Entidad DTO para representar los datos de la vista materializada
 * vw_ventas_mensuales_por_categoria
 * 
 * Contiene el consolidado de ventas mensuales por categoría de producto
 */
public class ReporteVentasEntidad {

    private String mesAno;              // Formato: YYYY-MM
    private Integer anio;               // Año de la orden
    private Integer mes;                // Mes de la orden (1-12)
    private String nombreCategoria;     // Nombre de la categoría
    private Integer cantidadOrdenes;    // Cantidad de órdenes en el mes
    private Integer cantidadProductos;  // Cantidad total de productos vendidos
    private BigDecimal totalVendido;    // Total de dinero vendido
    private BigDecimal precioPromedio;  // Precio promedio de los productos

    // Constructor por defecto
    public ReporteVentasEntidad() {
    }

    // Constructor con todos los parámetros
    public ReporteVentasEntidad(String mesAno, Integer anio, Integer mes, 
                                String nombreCategoria, Integer cantidadOrdenes,
                                Integer cantidadProductos, BigDecimal totalVendido,
                                BigDecimal precioPromedio) {
        this.mesAno = mesAno;
        this.anio = anio;
        this.mes = mes;
        this.nombreCategoria = nombreCategoria;
        this.cantidadOrdenes = cantidadOrdenes;
        this.cantidadProductos = cantidadProductos;
        this.totalVendido = totalVendido;
        this.precioPromedio = precioPromedio;
    }

    // Getters y Setters
    public String getMesAno() {
        return mesAno;
    }

    public void setMesAno(String mesAno) {
        this.mesAno = mesAno;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public Integer getMes() {
        return mes;
    }

    public void setMes(Integer mes) {
        this.mes = mes;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public void setNombreCategoria(String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    public Integer getCantidadOrdenes() {
        return cantidadOrdenes;
    }

    public void setCantidadOrdenes(Integer cantidadOrdenes) {
        this.cantidadOrdenes = cantidadOrdenes;
    }

    public Integer getCantidadProductos() {
        return cantidadProductos;
    }

    public void setCantidadProductos(Integer cantidadProductos) {
        this.cantidadProductos = cantidadProductos;
    }

    public BigDecimal getTotalVendido() {
        return totalVendido;
    }

    public void setTotalVendido(BigDecimal totalVendido) {
        this.totalVendido = totalVendido;
    }

    public BigDecimal getPrecioPromedio() {
        return precioPromedio;
    }

    public void setPrecioPromedio(BigDecimal precioPromedio) {
        this.precioPromedio = precioPromedio;
    }

    @Override
    public String toString() {
        return "ReporteVentasEntidad{" +
                "mesAno='" + mesAno + '\'' +
                ", anio=" + anio +
                ", mes=" + mes +
                ", nombreCategoria='" + nombreCategoria + '\'' +
                ", cantidadOrdenes=" + cantidadOrdenes +
                ", cantidadProductos=" + cantidadProductos +
                ", totalVendido=" + totalVendido +
                ", precioPromedio=" + precioPromedio +
                '}';
    }
}
