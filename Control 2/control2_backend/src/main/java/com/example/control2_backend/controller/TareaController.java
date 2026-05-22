package com.example.control2_backend.controller;

import com.example.control2_backend.dtos.TareaDto;
import com.example.control2_backend.service.TareaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tareas")
@CrossOrigin(origins = "*")
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {
        this.tareaService = tareaService;
    }

    // ====== ENDPOINTS ESPECÍFICOS (van primero para evitar conflictos con /{id}) ======

    @GetMapping("/notificaciones")
    public ResponseEntity<List<TareaDto>> getNotificaciones(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getNotificaciones(userDetails.getUsername()));
    }

    // ====== ESTADÍSTICAS GEOESPACIALES ======

    @GetMapping("/estadisticas/por-sector")
    public ResponseEntity<List<Map<String, Object>>> getTareasPorSector(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getTareasPorSector(userDetails.getUsername()));
    }

    @GetMapping("/estadisticas/mas-cercana")
    public ResponseEntity<Map<String, Object>> getTareaMasCercana(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getTareaMasCercana(userDetails.getUsername()));
    }

    @GetMapping("/estadisticas/sector-radio-2km")
    public ResponseEntity<Map<String, Object>> getSectorRadio2km(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getSectorRadio2km(userDetails.getUsername()));
    }

    @GetMapping("/estadisticas/promedio-distancia")
    public ResponseEntity<Map<String, Object>> getPromedioDistancia(
            @AuthenticationPrincipal UserDetails userDetails) {
        Double promedio = tareaService.getPromedioDistancia(userDetails.getUsername());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("promedioMetros", promedio);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/estadisticas/sectores-pendientes")
    public ResponseEntity<List<Map<String, Object>>> getSectoresPendientes() {
        return ResponseEntity.ok(tareaService.getSectoresPendientes());
    }

    @GetMapping("/estadisticas/por-usuario-sector")
    public ResponseEntity<List<Map<String, Object>>> getTareasPorUsuarioYSector() {
        return ResponseEntity.ok(tareaService.getTareasPorUsuarioYSector());
    }

    @GetMapping("/estadisticas/sector-radio-5km")
    public ResponseEntity<Map<String, Object>> getSectorRadio5km(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getSectorRadio5km(userDetails.getUsername()));
    }

    // ====== CRUD GENÉRICOS ======

    @GetMapping
    public ResponseEntity<List<TareaDto>> getAll(
            @RequestParam(required = false) Boolean estado,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getTareas(userDetails.getUsername(), estado, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TareaDto> getById(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.getById(id, userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<TareaDto> create(@RequestBody TareaDto dto,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.create(dto, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TareaDto> update(@PathVariable Long id,
                                            @RequestBody TareaDto dto,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.update(id, dto, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        tareaService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/completar")
    public ResponseEntity<TareaDto> toggleCompletada(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tareaService.toggleCompletada(id, userDetails.getUsername()));
    }
}
