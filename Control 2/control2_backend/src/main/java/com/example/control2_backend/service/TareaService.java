package com.example.control2_backend.service;

import com.example.control2_backend.dtos.TareaDto;
import com.example.control2_backend.entity.SectorEntity;
import com.example.control2_backend.entity.TareaEntity;
import com.example.control2_backend.entity.UsuarioEntity;
import com.example.control2_backend.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class TareaService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final TareaRepository tareaRepository;
    private final UsuarioService usuarioService;
    private final SectorService sectorService;

    public TareaService(TareaRepository tareaRepository,
                        UsuarioService usuarioService,
                        SectorService sectorService) {
        this.tareaRepository = tareaRepository;
        this.usuarioService = usuarioService;
        this.sectorService = sectorService;
    }

    public List<TareaDto> getTareas(String username, Boolean estado, String keyword) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        Long uid = usuario.getId();

        List<TareaEntity> tareas;
        if (estado != null && keyword != null && !keyword.isBlank()) {
            tareas = tareaRepository.findByUsuarioIdAndEstadoAndKeyword(uid, estado, keyword);
        } else if (estado != null) {
            tareas = tareaRepository.findByUsuarioIdAndEstadoCompletada(uid, estado);
        } else if (keyword != null && !keyword.isBlank()) {
            tareas = tareaRepository.findByUsuarioIdAndKeyword(uid, keyword);
        } else {
            tareas = tareaRepository.findByUsuarioId(uid);
        }

        return tareas.stream().map(this::toDto).toList();
    }

    public TareaDto getById(Long id, String username) {
        TareaEntity tarea = getEntityById(id, username);
        return toDto(tarea);
    }

    public TareaDto create(TareaDto dto, String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        SectorEntity sector = sectorService.getEntityById(dto.getSectorId());

        TareaEntity tarea = new TareaEntity();
        tarea.setTitulo(dto.getTitulo());
        tarea.setDescripcion(dto.getDescripcion());
        tarea.setFechaVencimiento(LocalDateTime.parse(dto.getFechaVencimiento(), FORMATTER));
        tarea.setEstadoCompletada(false);
        tarea.setUsuario(usuario);
        tarea.setSector(sector);

        return toDto(tareaRepository.save(tarea));
    }

    public TareaDto update(Long id, TareaDto dto, String username) {
        TareaEntity tarea = getEntityById(id, username);
        SectorEntity sector = sectorService.getEntityById(dto.getSectorId());

        tarea.setTitulo(dto.getTitulo());
        tarea.setDescripcion(dto.getDescripcion());
        tarea.setFechaVencimiento(LocalDateTime.parse(dto.getFechaVencimiento(), FORMATTER));
        tarea.setSector(sector);

        return toDto(tareaRepository.save(tarea));
    }

    public void delete(Long id, String username) {
        TareaEntity tarea = getEntityById(id, username);
        tareaRepository.delete(tarea);
    }

    public TareaDto toggleCompletada(Long id, String username) {
        TareaEntity tarea = getEntityById(id, username);
        tarea.setEstadoCompletada(!tarea.getEstadoCompletada());
        return toDto(tareaRepository.save(tarea));
    }

    public List<TareaDto> getNotificaciones(String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in24h = now.plusHours(24);
        return tareaRepository
                .findByUsuarioIdAndFechaVencimientoBetween(usuario.getId(), now, in24h)
                .stream()
                .filter(t -> !t.getEstadoCompletada())
                .map(this::toDto)
                .toList();
    }

    // ====== CONSULTAS GEOESPACIALES ======

    public List<Map<String, Object>> getTareasPorSector(String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        return tareaRepository.countTareasBySectorForUser(usuario.getId())
                .stream()
                .map(row -> Map.of("sector", row[0], "total", row[1]))
                .toList();
    }

    public Map<String, Object> getTareaMasCercana(String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        Object[] row = firstRow(tareaRepository.findTareaMasCercana(usuario.getId()));
        if (row == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row[0]);
        map.put("titulo", row[1]);
        map.put("descripcion", row[2]);
        map.put("fechaVencimiento", row[3]);
        map.put("estadoCompletada", row[4]);
        map.put("sectorId", row[5]);
        map.put("usuarioId", row[6]);
        map.put("sectorNombre", row[7]);
        map.put("distanciaMetros", row[8]);
        return map;
    }

    public Map<String, Object> getSectorRadio2km(String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        Object[] row = firstRow(tareaRepository.findSectorMasTareasCompletadasRadio2km(usuario.getId()));
        if (row == null) return Map.of();
        return Map.of("sector", row[0], "total", row[1]);
    }

    public Double getPromedioDistancia(String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        return tareaRepository.findPromedioDistanciaTareasCompletadas(usuario.getId());
    }

    public List<Map<String, Object>> getSectoresPendientes() {
        return tareaRepository.findSectoresConTareasPendientes()
                .stream()
                .map(row -> Map.of("sector", row[0], "total", row[1]))
                .toList();
    }

    public List<Map<String, Object>> getTareasPorUsuarioYSector() {
        return tareaRepository.countTareasByUsuarioAndSector()
                .stream()
                .map(row -> Map.of("usuario", row[0], "sector", row[1], "total", row[2]))
                .toList();
    }

    public Map<String, Object> getSectorRadio5km(String username) {
        UsuarioEntity usuario = usuarioService.getEntityByUsername(username);
        Object[] row = firstRow(tareaRepository.findSectorMasTareasCompletadasRadio5km(usuario.getId()));
        if (row == null) return Map.of();
        return Map.of("sector", row[0], "total", row[1]);
    }

    private Object[] firstRow(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        if (row.length == 1 && row[0] instanceof Object[] nestedRow) {
            return nestedRow;
        }
        return row;
    }

    private TareaEntity getEntityById(Long id, String username) {
        TareaEntity tarea = tareaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada: " + id));
        if (!tarea.getUsuario().getUsername().equals(username)) {
            throw new RuntimeException("No autorizado");
        }
        return tarea;
    }

    private TareaDto toDto(TareaEntity entity) {
        TareaDto dto = new TareaDto();
        dto.setId(entity.getId());
        dto.setTitulo(entity.getTitulo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setFechaVencimiento(entity.getFechaVencimiento() != null
                ? entity.getFechaVencimiento().format(FORMATTER) : null);
        dto.setEstadoCompletada(entity.getEstadoCompletada());
        dto.setUsuarioId(entity.getUsuario().getId());
        if (entity.getSector() != null) {
            dto.setSectorId(entity.getSector().getId());
            dto.setSectorNombre(entity.getSector().getNombre());
        }
        return dto;
    }
}
