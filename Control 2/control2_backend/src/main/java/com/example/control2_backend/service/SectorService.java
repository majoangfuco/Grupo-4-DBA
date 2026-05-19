package com.example.control2_backend.service;

import com.example.control2_backend.dtos.SectorDto;
import com.example.control2_backend.entity.SectorEntity;
import com.example.control2_backend.repository.SectorRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectorService {

    private final SectorRepository sectorRepository;
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    public SectorService(SectorRepository sectorRepository) {
        this.sectorRepository = sectorRepository;
    }

    public List<SectorDto> getAll() {
        return sectorRepository.findAll().stream().map(this::toDto).toList();
    }

    public SectorDto getById(Long id) {
        return toDto(getEntityById(id));
    }

    public SectorDto create(SectorDto dto) {
        SectorEntity sector = new SectorEntity();
        sector.setNombre(dto.getNombre());
        sector.setUbicacionEspacial(buildPoint(dto.getLongitud(), dto.getLatitud()));
        return toDto(sectorRepository.save(sector));
    }

    public SectorDto update(Long id, SectorDto dto) {
        SectorEntity sector = getEntityById(id);
        sector.setNombre(dto.getNombre());
        sector.setUbicacionEspacial(buildPoint(dto.getLongitud(), dto.getLatitud()));
        return toDto(sectorRepository.save(sector));
    }

    public void delete(Long id) {
        sectorRepository.deleteById(id);
    }

    public SectorEntity getEntityById(Long id) {
        return sectorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sector no encontrado: " + id));
    }

    private Point buildPoint(Double longitud, Double latitud) {
        return GF.createPoint(new Coordinate(longitud, latitud));
    }

    private SectorDto toDto(SectorEntity entity) {
        SectorDto dto = new SectorDto();
        dto.setId(entity.getId());
        dto.setNombre(entity.getNombre());
        if (entity.getUbicacionEspacial() != null) {
            dto.setLatitud(entity.getUbicacionEspacial().getY());
            dto.setLongitud(entity.getUbicacionEspacial().getX());
        }
        return dto;
    }
}
