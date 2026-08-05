package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Entities.FacturaItemEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.ProductoEntidad;
import com.ecommerceb2b.backend.Repository.FacturaRepositorio;
import com.ecommerceb2b.backend.Repository.FacturaItemRepositorio;
import com.ecommerceb2b.backend.Repository.OrdenesRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FacturaServicio {

    private final FacturaRepositorio repositorio;
    private final FacturaItemRepositorio facturaItemRepositorio;
    private final CarritoProductoServicio carritoProductoServicio;
    private final OrdenesRepositorio ordenesRepositorio;

    public FacturaServicio(FacturaRepositorio repositorio,
                           FacturaItemRepositorio facturaItemRepositorio,
                           CarritoProductoServicio carritoProductoServicio,
                           OrdenesRepositorio ordenesRepositorio) {
        this.repositorio = repositorio;
        this.facturaItemRepositorio = facturaItemRepositorio;
        this.carritoProductoServicio = carritoProductoServicio;
        this.ordenesRepositorio = ordenesRepositorio;
    }

    private void cargarItems(FacturaEntidad factura) {
        // Primero intentar cargar desde la tabla factura_item
        List<FacturaItemEntidad> itemsGuardados = facturaItemRepositorio.obtenerPorFacturaId(factura.getFactura_ID());
        
        if (!itemsGuardados.isEmpty()) {
            // Convertir FacturaItemEntidad a CarritoProductoEntidad
            factura.setItems(itemsGuardados.stream().map(item -> {
                CarritoProductoEntidad cp = new CarritoProductoEntidad();
                
                ProductoEntidad producto = new ProductoEntidad();
                producto.setProducto_ID(item.getProducto_id());
                producto.setNombre_producto(item.getNombre_producto());
                producto.setPrecio(item.getPrecio_unitario());
                cp.setProducto(producto);
                
                cp.setUnidad_producto((long) item.getCantidad());
                return cp;
            }).collect(Collectors.toList()));
        } else {
            // Fallback: si no hay items guardados, intentar cargar del carrito (para compatibilidad)
            ordenesRepositorio.encontrarPorId(factura.getOrdenId()).ifPresentOrElse(orden ->
                factura.setItems(carritoProductoServicio.listarItemsPorCarrito(orden.getCarrito_ID())),
                () -> factura.setItems(java.util.Collections.emptyList())
            );
        }
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

    public Optional<FacturaEntidad> obtenerPorOrden(Long ordenId) {
        Optional<FacturaEntidad> facturaOpt = repositorio.findByOrdenId(ordenId);
        facturaOpt.ifPresent(this::cargarItems);
        return facturaOpt;
    }

    public FacturaEntidad crearFactura(FacturaEntidad factura) {
        validarFactura(factura);
        Long facturaId = repositorio.crear(factura);
        factura.setFactura_ID(facturaId);
        
        // Guardar los items de la factura en la tabla factura_item
        ordenesRepositorio.encontrarPorId(factura.getOrdenId()).ifPresent(orden -> {
            List<CarritoProductoEntidad> items = carritoProductoServicio.listarItemsPorCarrito(orden.getCarrito_ID());
            for (CarritoProductoEntidad item : items) {
                facturaItemRepositorio.crearItem(
                    facturaId,
                    item.getProducto().getProducto_ID(),
                    item.getProducto().getNombre_producto(),
                    item.getProducto().getPrecio(),
                    item.getUnidad_producto().intValue()
                );
            }
        });
        
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