package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import com.ecommerceb2b.backend.Repository.FacturaRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FacturaServicio {

    private final FacturaRepositorio repositorio;

    public FacturaServicio(FacturaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<FacturaEntidad> obtenerTodas() {
        return repositorio.findAll();
    }

    public FacturaEntidad obtenerPorId(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada con ID: " + id));
    }

    public List<FacturaEntidad> obtenerPorUsuario(Long usuarioId) {
        return repositorio.findByUsuarioId(usuarioId);
    }
}