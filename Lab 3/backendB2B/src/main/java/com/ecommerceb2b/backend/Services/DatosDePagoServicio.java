package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.DatosDePagoEntidad;
import com.ecommerceb2b.backend.Repository.DatosDePagoRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DatosDePagoServicio {

    private final DatosDePagoRepositorio datosDePagoRepositorio;

    public DatosDePagoServicio(DatosDePagoRepositorio datosDePagoRepositorio) {
        this.datosDePagoRepositorio = datosDePagoRepositorio;
    }

    public Optional<DatosDePagoEntidad> obtenerPorId(Long id) {
        return datosDePagoRepositorio.findById(id);
    }

    public List<DatosDePagoEntidad> obtenerPorUsuario(Long usuarioId) {
        return datosDePagoRepositorio.findByUsuarioId(usuarioId);
    }

    public Long guardar(DatosDePagoEntidad datosDePago) {
        return datosDePagoRepositorio.save(datosDePago);
    }

    public void actualizar(DatosDePagoEntidad datosDePago) {
        datosDePagoRepositorio.update(datosDePago);
    }

    public void eliminar(Long id) {
        datosDePagoRepositorio.deleteById(id);
    }
}
