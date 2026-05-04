package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.CarritoEntidad;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.CheckoutPedidoDto;
import com.ecommerceb2b.backend.Entities.DatosDePagoEntidad;
import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import com.ecommerceb2b.backend.Entities.OrdenesEntidad;
import com.ecommerceb2b.backend.Repository.OrdenesRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrdenesServicio {

    private final OrdenesRepositorio ordenesRepositorio;
    private final CarritoServicio carritoServicio;
    private final CarritoProductoServicio carritoProductoServicio;
    private final DatosDePagoServicio datosDePagoServicio;
    private final InformacionEntregaServicio informacionEntregaServicio;
    private final FacturaServicio facturaServicio;

    public OrdenesServicio(OrdenesRepositorio ordenesRepositorio,
                           CarritoServicio carritoServicio,
                           CarritoProductoServicio carritoProductoServicio,
                           DatosDePagoServicio datosDePagoServicio,
                           InformacionEntregaServicio informacionEntregaServicio,
                           FacturaServicio facturaServicio) {
        this.ordenesRepositorio = ordenesRepositorio;
        this.carritoServicio = carritoServicio;
        this.carritoProductoServicio = carritoProductoServicio;
        this.datosDePagoServicio = datosDePagoServicio;
        this.informacionEntregaServicio = informacionEntregaServicio;
        this.facturaServicio = facturaServicio;
    }


    @Transactional
    public OrdenesEntidad crearOrden(OrdenesEntidad orden) {
        validarOrden(orden);
        orden.setFecha_Orden(new Date());
        orden.setEstado("PENDIENTE");

        Long ordenId = ordenesRepositorio.crear(orden);
        orden.setOrden_ID(ordenId);
        return orden;
    }

    @Transactional
    public FacturaEntidad solicitarOrdenAtomica(Long carritoId, CheckoutPedidoDto pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("El pedido de checkout es obligatorio");
        }

        CarritoEntidad carrito = carritoServicio.obtenerCarritoPorId(carritoId);
        if (!"ACTIVO".equalsIgnoreCase(carrito.getEstado())) {
            throw new IllegalStateException("Solo se puede procesar la solicitud desde un carrito ACTIVO");
        }

        if (carrito.getUsuario() == null || carrito.getUsuario().getUsuario_ID() == null) {
            throw new IllegalStateException("El carrito debe tener un usuario válido");
        }

        Long usuarioId = carrito.getUsuario().getUsuario_ID();
        List<CarritoProductoEntidad> items = carritoProductoServicio.listarItemsPorCarrito(carritoId);
        if (items.isEmpty()) {
            throw new IllegalStateException("El carrito no tiene productos para generar la orden");
        }

        InformacionEntregaEntidad entrega = informacionEntregaServicio.obtenerPorId(pedido.getInfoEntregaId());
        if (!usuarioId.equals(entrega.getUsuarioId())) {
            throw new IllegalArgumentException("La información de entrega no pertenece al mismo usuario del carrito");
        }

        DatosDePagoEntidad datosPago = resolveDatosDePago(usuarioId, pedido);
        if (datosPago == null) {
            throw new IllegalArgumentException("Se requiere información de pago válida para completar la solicitud");
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
        orden.setUsuario_ID(usuarioId);
        orden.setInfo_Entrega_ID(entrega.getInfo_Entrega_ID());
        orden.setEstado("PENDIENTE");
        OrdenesEntidad ordenCreada = crearOrden(orden);
        ordenCreada.setUsuario_ID(usuarioId);

        FacturaEntidad factura = new FacturaEntidad();
        factura.setUsuarioId(usuarioId);
        factura.setDatos_Pago_ID(datosPago.getDatos_Pago_ID());
        factura.setOrdenId(ordenCreada.getOrden_ID());
        factura.setPrecio_Total(precioTotal);
        factura.setTotal_Neto(totalNeto);
        factura.setIva(iva);
        factura.setFecha_Emision(new Date());

        FacturaEntidad facturaCreada = facturaServicio.crearFactura(factura);

        // Mantener los productos del carrito para que la factura pueda recuperar
        // los items más tarde, mientras el carrito se marca como pagado.
        carritoServicio.ordenarCarrito(carritoId);

        return facturaCreada;
    }

    @Transactional(readOnly = true)
    public List<OrdenesEntidad> listarOrdenes() {
        return ordenesRepositorio.encontrarTodos();
    }

    @Transactional(readOnly = true)
    public OrdenesEntidad obtenerOrdenPorId(Long ordenId) {
        return ordenesRepositorio.encontrarPorId(ordenId)
                .orElseThrow(() -> new NoSuchElementException(
                    "Orden no encontrada: " + ordenId
                ));
    }

    @Transactional
    public OrdenesEntidad actualizarOrden(Long ordenId, OrdenesEntidad ordenActualizada) {
        OrdenesEntidad actual = obtenerOrdenPorId(ordenId);
        ordenActualizada.setOrden_ID(actual.getOrden_ID());
        validarOrden(ordenActualizada);

        int filasAfectadas = ordenesRepositorio.actualizar(ordenActualizada);
        if (filasAfectadas == 0) {
            throw new IllegalStateException("No se pudo actualizar la orden: " + ordenId);
        }
        return ordenActualizada;
    }

    @Transactional
    public void eliminarOrden(Long ordenId) {
        obtenerOrdenPorId(ordenId);
        int filasAfectadas = ordenesRepositorio.borrarPorId(ordenId);
        if (filasAfectadas == 0) {
            throw new IllegalStateException("No se pudo eliminar la orden: " + ordenId);
        }
    }


    // Req. 2 — Cliente ve sus propias órdenes
    @Transactional(readOnly = true)
    public List<OrdenesEntidad> listarPorUsuario(Long usuarioId) {
        if (usuarioId == null || usuarioId <= 0) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        return ordenesRepositorio.encontrarPorUsuarioId(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<OrdenesEntidad> listarPorEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        validarEstado(estado);
        return ordenesRepositorio.encontrarPorEstado(estado);
    }

    @Transactional(readOnly = true)
    public List<OrdenesEntidad> listarPorFecha(Date fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        return ordenesRepositorio.encontrarPorFechaOrden(fecha);
    }

    // Req. 2 — Admin aprueba la orden (el Trigger 2 actualiza ultima_compra)
    @Transactional
    public OrdenesEntidad aprobarOrden(Long ordenId) {
        OrdenesEntidad orden = obtenerOrdenPorId(ordenId);

        if (orden.getEstado().equals("APROBADA")) {
            throw new IllegalStateException("La orden ya está aprobada");
        }
        if (orden.getEstado().equals("CANCELADA")) {
            throw new IllegalStateException("No se puede aprobar una orden cancelada");
        }

        orden.setEstado("APROBADA");
        ordenesRepositorio.actualizar(orden);
        crearFacturaSiNoExiste(orden);
        return orden;
    }

    private void crearFacturaSiNoExiste(OrdenesEntidad orden) {
        if (facturaServicio.obtenerPorOrden(orden.getOrden_ID()).isPresent()) {
            return;
        }

        BigDecimal subtotal = carritoProductoServicio.calcularSubtotal(orden.getCarrito_ID());
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            // El carrito no tiene items - no crear factura en este caso
            // Esto puede ocurrir si la orden fue creada sin pasar por checkout normal
            return;
        }

        float precioTotal = subtotal.floatValue();
        float totalNeto = subtotal.divide(BigDecimal.valueOf(1.19), 2, RoundingMode.HALF_UP).floatValue();
        float iva = precioTotal - totalNeto;

        FacturaEntidad factura = new FacturaEntidad();
        factura.setUsuarioId(orden.getUsuario_ID());
        factura.setOrdenId(orden.getOrden_ID());
        factura.setPrecio_Total(precioTotal);
        factura.setTotal_Neto(totalNeto);
        factura.setIva(iva);
        factura.setFecha_Emision(new Date());

        var pagos = datosDePagoServicio.obtenerPorUsuario(orden.getUsuario_ID());
        if (pagos != null && !pagos.isEmpty()) {
            factura.setDatos_Pago_ID(pagos.get(0).getDatos_Pago_ID());
        }

        facturaServicio.crearFactura(factura);
    }

    @Transactional
    public OrdenesEntidad cancelarOrden(Long ordenId) {
        OrdenesEntidad orden = obtenerOrdenPorId(ordenId);

        if (orden.getEstado().equals("APROBADA")) {
            throw new IllegalStateException("No se puede cancelar una orden ya aprobada");
        }
        if (orden.getEstado().equals("CANCELADA")) {
            throw new IllegalStateException("La orden ya está cancelada");
        }

        orden.setEstado("CANCELADA");
        ordenesRepositorio.actualizar(orden);
        return orden;
    }


    private void validarOrden(OrdenesEntidad orden) {
        if (orden == null) {
            throw new IllegalArgumentException("La orden es obligatoria");
        }
        if (orden.getCarrito_ID() == null || orden.getCarrito_ID() <= 0) {
            throw new IllegalArgumentException("El carrito es obligatorio");
        }
        if (orden.getUsuario_ID() == null || orden.getUsuario_ID() <= 0) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        if (orden.getInfo_Entrega_ID() == null || orden.getInfo_Entrega_ID() <= 0) {
            throw new IllegalArgumentException("La información de entrega es obligatoria");
        }
    }

    private void validarEstado(String estado) {
        List<String> estadosValidos = List.of("PENDIENTE", "APROBADA", "CANCELADA");
        if (!estadosValidos.contains(estado.toUpperCase())) {
            throw new IllegalArgumentException(
                "Estado inválido. Valores permitidos: " + estadosValidos
            );
        }
    }

    @Transactional(readOnly = true)
    public List<OrdenesEntidad> listarOrdenesConRut() {
        return ordenesRepositorio.encontrarTodosConRut();
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
