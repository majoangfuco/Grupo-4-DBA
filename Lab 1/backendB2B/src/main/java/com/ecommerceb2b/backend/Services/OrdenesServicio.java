package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.OrdenesEntidad;
import com.ecommerceb2b.backend.Repository.OrdenesRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrdenesServicio {

    private final OrdenesRepositorio ordenesRepositorio;

    public OrdenesServicio(OrdenesRepositorio ordenesRepositorio) {
        this.ordenesRepositorio = ordenesRepositorio;
    }


    @Transactional
    public OrdenesEntidad crearOrden(OrdenesEntidad orden) {
        validarOrden(orden);
        orden.setFecha_Orden(new Date());
        orden.setEstado("PENDIENTE");

        int filasAfectadas = ordenesRepositorio.crear(orden);
        if (filasAfectadas == 0) {
            throw new IllegalStateException("No se pudo crear la orden");
        }
        return orden;
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
        return orden;
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
}