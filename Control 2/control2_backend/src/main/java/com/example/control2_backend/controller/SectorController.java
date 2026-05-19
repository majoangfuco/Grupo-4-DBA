package com.example.control2_backend.controller;

import com.example.control2_backend.dtos.SectorDto;
import com.example.control2_backend.service.SectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sectores")
@CrossOrigin(origins = "*")
public class SectorController {

    private final SectorService sectorService;

    public SectorController(SectorService sectorService) {
        this.sectorService = sectorService;
    }

    @GetMapping
    public ResponseEntity<List<SectorDto>> getAll() {
        return ResponseEntity.ok(sectorService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectorDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sectorService.getById(id));
    }

    @PostMapping
    public ResponseEntity<SectorDto> create(@RequestBody SectorDto dto) {
        return ResponseEntity.ok(sectorService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SectorDto> update(@PathVariable Long id, @RequestBody SectorDto dto) {
        return ResponseEntity.ok(sectorService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sectorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
