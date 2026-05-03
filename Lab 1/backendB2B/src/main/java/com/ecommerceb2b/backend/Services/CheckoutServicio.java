package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.CheckoutPedidoDto;
import com.ecommerceb2b.backend.Entities.DatosDePagoEntidad;
import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import com.ecommerceb2b.backend.Entities.OrdenesEntidad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CheckoutServicio {

    private final CarritoServicio carritoServicio;
    private final CarritoProductoServicio carritoProductoServicio;
    private final DatosDePagoServicio datosDePagoServicio;
    private final InformacionEntregaServicio informacionEntregaServicio;
    private final OrdenesServicio ordenesServicio;
    private final FacturaServicio facturaServicio;

    public CheckoutServicio(CarritoServicio carritoServicio,
                            CarritoProductoServicio carritoProductoServicio,
                            DatosDePagoServicio datosDePagoServicio,
                            InformacionEntregaServicio informacionEntregaServicio,
                            OrdenesServicio ordenesServicio,
                            FacturaServicio facturaServicio) {
        this.carritoServicio = carritoServicio;
        this.carritoProductoServicio = carritoProductoServicio;
        this.datosDePagoServicio = datosDePagoServicio;
        this.informacionEntregaServicio = informacionEntregaServicio;
        this.ordenesServicio = ordenesServicio;
        this.facturaServicio = facturaServicio;
    }

    @Transactional
    public FacturaEntidad procesarCheckout(Long carritoId, CheckoutPedidoDto pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("El pedido de checkout es obligatorio");
        }

        CarritoEntidad carrito = carritoServicio.obtenerCarritoPorId(carritoId);
        if (carrito == null) {
            throw new IllegalArgumentException("Carrito no encontrado: " + carritoId);
        }
        if (!"ACTIVO".equalsIgnoreCase(carrito.getEstado())) {
            throw new IllegalStateException("Solo se puede procesar el checkout de un carrito ACTIVO");
        }

        if (carrito.getUsuario() == null || carrito.getUsuario().getUsuario_ID() == null) {
            throw new IllegalStateException("El carrito debe tener un usuario válido");
        }

        Long usuarioId = carrito.getUsuario().getUsuario_ID();
        List<CarritoProductoEntidad> items = carritoProductoServicio.listarItemsPorCarrito(carritoId);
        if (items.isEmpty()) {
            throw new IllegalStateException("El carrito no tiene productos para procesar la orden");
        }

        InformacionEntregaEntidad entrega = informacionEntregaServicio.obtenerPorId(pedido.getInfoEntregaId());
        if (!usuarioId.equals(entrega.getUsuarioId())) {
            throw new IllegalArgumentException("La información de entrega no pertenece al mismo usuario del carrito");
        }

        DatosDePagoEntidad datosPago = resolveDatosDePago(usuarioId, pedido);
        if (datosPago == null) {
            throw new IllegalArgumentException("Se requiere información de pago válida para completar el checkout");
        }

        BigDecimal subtotal = carritoProductoServicio.calcularSubtotal(carritoId);
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El subtotal del carrito es inválido");
        }

        float precioTotal = subtotal.floatValue();
        float totalNeto = subtotal.divide(BigDecimal.valueOf(1.19), 2, RoundingMode.HALF_UP).floatValue();
        float iva = precioTotal - totalNeto;

        OrdenesEntidad orden = new OrdenesEntidad();
        orden.setCarrito_ID(carritoId);
        orden.setInfo_Entrega_ID(entrega.getInfo_Entrega_ID());
        orden.setEstado("PENDIENTE");
        OrdenesEntidad ordenCreada = ordenesServicio.crearOrden(orden);
        ordenCreada.setUsuario_ID(usuarioId);

        FacturaEntidad factura = new FacturaEntidad();
        factura.setUsuarioId(usuarioId);
        factura.setDatos_Pago_ID(datosPago.getDatos_Pago_ID());
        factura.setOrdenId(ordenCreada.getOrden_ID());
        factura.setPrecio_Total(precioTotal);
        factura.setTotal_Neto(totalNeto);
        factura.setIva(iva);
        factura.setFecha_Emision(new java.util.Date());

        FacturaEntidad facturaCreada = facturaServicio.crearFactura(factura);
        carritoServicio.pagarCarrito(carritoId);

        return facturaCreada;
    }

    private DatosDePagoEntidad resolveDatosDePago(Long usuarioId, CheckoutPedidoDto pedido) {
        if (pedido.getDatosPagoId() != null) {
            DatosDePagoEntidad datos = datosDePagoServicio.obtenerPorId(pedido.getDatosPagoId())
                    .orElseThrow(() -> new IllegalArgumentException("Datos de pago no encontrados: " + pedido.getDatosPagoId()));
            if (!usuarioId.equals(datos.getUsuario_ID())) {
                throw new IllegalArgumentException("Los datos de pago deben pertenecer al mismo usuario");
            }
            return datos;
        }

        DatosDePagoEntidad datosPago = pedido.getDatosPago();
        if (datosPago != null) {
            datosPago.setUsuario_ID(usuarioId);
            Long idGuardado = datosDePagoServicio.guardar(datosPago);
            datosPago.setDatos_Pago_ID(idGuardado);
            return datosPago;
        }

        return null;
    }
}
