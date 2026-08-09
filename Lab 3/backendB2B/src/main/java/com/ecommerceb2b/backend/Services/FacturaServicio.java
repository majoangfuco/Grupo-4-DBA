package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Repository.FacturaRepositorio;
import com.ecommerceb2b.backend.Repository.OrdenesRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public List<FacturaEntidad> obtenerTodas() { return repositorio.findAll(); }

    public FacturaEntidad obtenerPorId(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada con ID: " + id));
    }

    public List<FacturaEntidad> obtenerPorUsuario(Long usuarioId) {
        return repositorio.findByUsuarioId(usuarioId);
    }

    public Optional<FacturaEntidad> obtenerPorOrden(Long ordenId) {
        return repositorio.findByOrdenId(ordenId);
    }

    public FacturaEntidad crearFactura(FacturaEntidad factura) {
        validarFactura(factura);
        if (factura.getItems() == null || factura.getItems().isEmpty()) {
            ordenesRepositorio.encontrarPorId(factura.getOrdenId()).ifPresent(orden -> {
                List<CarritoProductoEntidad> items = carritoProductoServicio
                        .listarItemsPorCarrito(orden.getCarrito_ID());
                factura.setItems(items);
            });
        }
        repositorio.crear(factura);
        return factura;
    }

    private void validarFactura(FacturaEntidad factura) {
        if (factura == null) throw new IllegalArgumentException("La factura es obligatoria");
        if (factura.getUsuarioId() == null || factura.getUsuarioId() <= 0)
            throw new IllegalArgumentException("El usuario de la factura es obligatorio");
        if (factura.getOrdenId() == null || factura.getOrdenId() <= 0)
            throw new IllegalArgumentException("La orden de la factura es obligatoria");
        if (factura.getPrecio_Total() == null || factura.getPrecio_Total() <= 0)
            throw new IllegalArgumentException("El precio total de la factura es obligatorio");
        if (factura.getFecha_Emision() == null)
            throw new IllegalArgumentException("La fecha de emisión es obligatoria");
    }
}
