package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import com.ecommerceb2b.backend.Repository.InformacionEntregaRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InformacionEntregaServicio {

    private final InformacionEntregaRepositorio repositorio;

    public InformacionEntregaServicio(InformacionEntregaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<InformacionEntregaEntidad> obtenerTodas() {
        return repositorio.findAllActivas();
    }

    public InformacionEntregaEntidad obtenerPorId(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Entrega no encontrada con ID: " + id));
    }

    public List<InformacionEntregaEntidad> obtenerPorUsuario(Long usuarioId) {
        return repositorio.findByUsuarioId(usuarioId);
    }

    public String crear(InformacionEntregaEntidad entrega) {
        repositorio.save(entrega);
        return "Información de entrega creada correctamente";
    }

    public String actualizar(Long id, InformacionEntregaEntidad entrega) {
        repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe la entrega con ID: " + id));
        entrega.setInfo_Entrega_ID(id);
        repositorio.update(entrega);
        return "Información de entrega actualizada correctamente";
    }

    public String eliminar(Long id) {
        repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe la entrega con ID: " + id));
        repositorio.softDelete(id);
        return "Entrega desactivada correctamente";
    }
}