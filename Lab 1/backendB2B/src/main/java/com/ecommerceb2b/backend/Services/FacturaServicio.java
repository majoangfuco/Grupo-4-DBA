package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Repository.FacturaRepositorio;
import com.ecommerceb2b.backend.Repository.OrdenesRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FacturaServicio {

    private final FacturaRepositorio repositorio;
    private final CarritoProductoServicio carritoProductoServicio;
    private final OrdenesRepositorio ordenesRepositorio;

    public FacturaServicio(FacturaRepositorio repositorio,
                           CarritoProductoServicio carritoProductoServicio,
                           OrdenesRepositorio ordenesRepositorio) {
        this.repositorio = repositorio;
        this.carritoProductoServicio = carritoProductoServicio;
        this.ordenesRepositorio = ordenesRepositorio;
    }

    private void cargarItems(FacturaEntidad factura) {
        ordenesRepositorio.encontrarPorId(factura.getOrdenId()).ifPresent(orden ->
            factura.setItems(carritoProductoServicio.listarItemsPorCarrito(orden.getCarrito_ID()))
        );
    }

    public List<FacturaEntidad> obtenerTodas() {
        List<FacturaEntidad> facturas = repositorio.findAll();
        facturas.forEach(this::cargarItems);
        return facturas;
    }

    public FacturaEntidad obtenerPorId(Long id) {
        FacturaEntidad factura = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada con ID: " + id));
        cargarItems(factura);
        return factura;
    }

    public List<FacturaEntidad> obtenerPorUsuario(Long usuarioId) {
        List<FacturaEntidad> facturas = repositorio.findByUsuarioId(usuarioId);
        facturas.forEach(this::cargarItems);
        return facturas;
    }

    public FacturaEntidad obtenerPorOrden(Long ordenId) {
        FacturaEntidad factura = repositorio.findByOrdenId(ordenId)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada para la Orden ID: " + ordenId));
        cargarItems(factura);
        return factura;
    }

    public FacturaEntidad crearFactura(FacturaEntidad factura) {
        validarFactura(factura);
        Long facturaId = repositorio.crear(factura);
        factura.setFactura_ID(facturaId);
        cargarItems(factura);
        return factura;
    }

    private void validarFactura(FacturaEntidad factura) {
        if (factura == null) {
            throw new IllegalArgumentException("La factura es obligatoria");
        }
        if (factura.getUsuarioId() == null || factura.getUsuarioId() <= 0) {
            throw new IllegalArgumentException("El usuario de la factura es obligatorio");
        }
        if (factura.getOrdenId() == null || factura.getOrdenId() <= 0) {
            throw new IllegalArgumentException("La orden de la factura es obligatoria");
        }
        if (factura.getPrecio_Total() == null || factura.getPrecio_Total() <= 0) {
            throw new IllegalArgumentException("El precio total de la factura es obligatorio");
        }
        if (factura.getFecha_Emision() == null) {
            throw new IllegalArgumentException("La fecha de emisión es obligatoria");
        }
    }
}