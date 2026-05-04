package com.ecommerceb2b.backend.Entities;

public class FacturaItemEntidad {
    private Long factura_item_id;
    private Long factura_id;
    private Long producto_id;
    private String nombre_producto;
    private float precio_unitario;
    private int cantidad;

    // Getters y Setters
    public Long getFactura_item_id() {
        return factura_item_id;
    }

    public void setFactura_item_id(Long factura_item_id) {
        this.factura_item_id = factura_item_id;
    }

    public Long getFactura_id() {
        return factura_id;
    }

    public void setFactura_id(Long factura_id) {
        this.factura_id = factura_id;
    }

    public Long getProducto_id() {
        return producto_id;
    }

    public void setProducto_id(Long producto_id) {
        this.producto_id = producto_id;
    }

    public String getNombre_producto() {
        return nombre_producto;
    }

    public void setNombre_producto(String nombre_producto) {
        this.nombre_producto = nombre_producto;
    }

    public float getPrecio_unitario() {
        return precio_unitario;
    }

    public void setPrecio_unitario(float precio_unitario) {
        this.precio_unitario = precio_unitario;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }
}
