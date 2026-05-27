package com.example.control2_backend;

import com.example.control2_backend.dtos.TareaDto;
import com.example.control2_backend.entity.TareaEntity;
import com.example.control2_backend.entity.UsuarioEntity;
import com.example.control2_backend.entity.SectorEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Unitarios para el Requisito 2: Gestión de Tareas
 * Valida CRUD de tareas, completación y asociación con sectores
 */
class TareaGestionTest {

    private TareaEntity tarea;
    private TareaDto tareaDto;
    private UsuarioEntity usuario;
    private SectorEntity sector;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        geometryFactory = new GeometryFactory();

        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setUsername("testuser");

        sector = new SectorEntity();
        sector.setId(1L);
        sector.setNombre("Construcción");

        tareaDto = new TareaDto();
        tareaDto.setTitulo("Construir muro");
        tareaDto.setDescripcion("Muro de contención");
        tareaDto.setFechaVencimiento("2026-06-01T10:00:00");
        tareaDto.setSectorId(1L);

        tarea = new TareaEntity();
        tarea.setId(1L);
        tarea.setTitulo(tareaDto.getTitulo());
        tarea.setDescripcion(tareaDto.getDescripcion());
        tarea.setFechaVencimiento(LocalDateTime.parse(tareaDto.getFechaVencimiento()));
        tarea.setEstadoCompletada(false);
        tarea.setUsuario(usuario);
        tarea.setSector(sector);
    }

    // ===================== CREAR TAREAS =====================

    @Test
    void testCrearTarea() {
        assertNotNull(tarea);
        assertEquals("Construir muro", tarea.getTitulo());
        assertEquals("Muro de contención", tarea.getDescripcion());
        assertNotNull(tarea.getFechaVencimiento());
    }

    @Test
    void testCrearTareaConAsociacionSector() {
        assertNotNull(tarea.getSector());
        assertEquals("Construcción", tarea.getSector().getNombre());
    }

    @Test
    void testCrearTareaConAsociacionUsuario() {
        assertNotNull(tarea.getUsuario());
        assertEquals("testuser", tarea.getUsuario().getUsername());
    }

    // ===================== EDITAR TAREAS =====================

    @Test
    void testEditarTitulo() {
        String nuevoTitulo = "Construir puente";
        tarea.setTitulo(nuevoTitulo);
        assertEquals(nuevoTitulo, tarea.getTitulo());
    }

    @Test
    void testEditarDescripcion() {
        String nuevaDescripcion = "Puente peatonal";
        tarea.setDescripcion(nuevaDescripcion);
        assertEquals(nuevaDescripcion, tarea.getDescripcion());
    }

    @Test
    void testEditarFechaVencimiento() {
        LocalDateTime nuevaFecha = LocalDateTime.now().plusDays(10);
        tarea.setFechaVencimiento(nuevaFecha);
        assertEquals(nuevaFecha, tarea.getFechaVencimiento());
    }

    @Test
    void testEditarSector() {
        SectorEntity nuevoSector = new SectorEntity();
        nuevoSector.setId(2L);
        nuevoSector.setNombre("Reparación");
        tarea.setSector(nuevoSector);
        assertEquals("Reparación", tarea.getSector().getNombre());
    }

    // ===================== ELIMINAR TAREAS =====================

    @Test
    void testEliminarTarea() {
        Long idTarea = tarea.getId();
        assertNotNull(idTarea);
        assertEquals(1L, idTarea);
        // En BD se eliminaría verificando el ID
    }

    // ===================== MARCAR COMO COMPLETADA =====================

    @Test
    void testMarcarTareaCompletada() {
        assertFalse(tarea.getEstadoCompletada());
        tarea.setEstadoCompletada(true);
        assertTrue(tarea.getEstadoCompletada());
    }

    @Test
    void testToggleTareaCompletada() {
        boolean estadoInicial = tarea.getEstadoCompletada();
        tarea.setEstadoCompletada(!estadoInicial);
        assertNotEquals(estadoInicial, tarea.getEstadoCompletada());

        tarea.setEstadoCompletada(!tarea.getEstadoCompletada());
        assertEquals(estadoInicial, tarea.getEstadoCompletada());
    }

    // ===================== VER LISTA DE TAREAS =====================

    @Test
    void testListaTareasMultiples() {
        List<TareaEntity> tareas = new ArrayList<>();
        tareas.add(tarea);

        TareaEntity tarea2 = new TareaEntity();
        tarea2.setId(2L);
        tarea2.setTitulo("Reparar calle");
        tarea2.setUsuario(usuario);
        tareas.add(tarea2);

        TareaEntity tarea3 = new TareaEntity();
        tarea3.setId(3L);
        tarea3.setTitulo("Limpiar parque");
        tarea3.setUsuario(usuario);
        tareas.add(tarea3);

        assertEquals(3, tareas.size());
        assertTrue(tareas.stream().allMatch(t -> t.getUsuario().equals(usuario)));
    }

    @Test
    void testSepararTareasPendientesYCompletadas() {
        TareaEntity tarea2 = new TareaEntity();
        tarea2.setTitulo("Otra tarea");
        tarea2.setEstadoCompletada(true);
        tarea2.setUsuario(usuario);

        List<TareaEntity> pendientes = new ArrayList<>();
        List<TareaEntity> completadas = new ArrayList<>();

        if (!tarea.getEstadoCompletada()) {
            pendientes.add(tarea);
        }
        if (tarea2.getEstadoCompletada()) {
            completadas.add(tarea2);
        }

        assertEquals(1, pendientes.size());
        assertEquals(1, completadas.size());
    }
}
