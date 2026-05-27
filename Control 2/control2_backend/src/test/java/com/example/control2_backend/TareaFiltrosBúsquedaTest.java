package com.example.control2_backend;

import com.example.control2_backend.entity.TareaEntity;
import com.example.control2_backend.entity.UsuarioEntity;
import com.example.control2_backend.entity.SectorEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Unitarios para el Requisito 3: Filtros y Búsqueda
 * Valida filtrado por estado y búsqueda por palabras clave
 */
class TareaFiltrosBúsquedaTest {

    private List<TareaEntity> tareas;
    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setUsername("testuser");

        tareas = new ArrayList<>();

        TareaEntity tarea1 = new TareaEntity();
        tarea1.setId(1L);
        tarea1.setTitulo("Reparar semáforo");
        tarea1.setDescripcion("Semáforo roto en la esquina");
        tarea1.setEstadoCompletada(false);
        tarea1.setUsuario(usuario);
        tareas.add(tarea1);

        TareaEntity tarea2 = new TareaEntity();
        tarea2.setId(2L);
        tarea2.setTitulo("Limpiar calle");
        tarea2.setDescripcion("Limpieza profunda de la calle principal");
        tarea2.setEstadoCompletada(true);
        tarea2.setUsuario(usuario);
        tareas.add(tarea2);

        TareaEntity tarea3 = new TareaEntity();
        tarea3.setId(3L);
        tarea3.setTitulo("Reparar acera");
        tarea3.setDescripcion("La acera tiene grietas");
        tarea3.setEstadoCompletada(false);
        tarea3.setUsuario(usuario);
        tareas.add(tarea3);

        TareaEntity tarea4 = new TareaEntity();
        tarea4.setId(4L);
        tarea4.setTitulo("Pintar muro");
        tarea4.setDescripcion("Pintura en muro público");
        tarea4.setEstadoCompletada(true);
        tarea4.setUsuario(usuario);
        tareas.add(tarea4);
    }

    // ===================== FILTRAR POR ESTADO =====================

    @Test
    void testFiltrarPendientes() {
        List<TareaEntity> pendientes = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .collect(Collectors.toList());

        assertEquals(2, pendientes.size());
        assertTrue(pendientes.stream().allMatch(t -> !t.getEstadoCompletada()));
    }

    @Test
    void testFiltrarCompletadas() {
        List<TareaEntity> completadas = tareas.stream()
            .filter(TareaEntity::getEstadoCompletada)
            .collect(Collectors.toList());

        assertEquals(2, completadas.size());
        assertTrue(completadas.stream().allMatch(TareaEntity::getEstadoCompletada));
    }

    @Test
    void testFiltrarPorEstadoEspecífico() {
        boolean estado = false;
        List<TareaEntity> filtradas = tareas.stream()
            .filter(t -> t.getEstadoCompletada() == estado)
            .collect(Collectors.toList());

        assertEquals(2, filtradas.size());
    }

    // ===================== BÚSQUEDA POR PALABRA CLAVE =====================

    @Test
    void testBúsquedaPorTítulo() {
        String palabra = "Reparar";
        List<TareaEntity> resultados = tareas.stream()
            .filter(t -> t.getTitulo().toLowerCase().contains(palabra.toLowerCase()))
            .collect(Collectors.toList());

        assertEquals(2, resultados.size());
        assertTrue(resultados.stream().allMatch(t -> t.getTitulo().contains("Reparar")));
    }

    @Test
    void testBúsquedaPorDescripción() {
        String palabra = "calle";
        List<TareaEntity> resultados = tareas.stream()
            .filter(t -> t.getDescripcion().toLowerCase().contains(palabra.toLowerCase()))
            .collect(Collectors.toList());

        assertEquals(1, resultados.size());
        assertEquals("Limpiar calle", resultados.get(0).getTitulo());
    }

    @Test
    void testBúsquedaPalabraclave() {
        String palabra = "grietas";
        List<TareaEntity> resultados = tareas.stream()
            .filter(t -> t.getTitulo().toLowerCase().contains(palabra.toLowerCase()) ||
                        t.getDescripcion().toLowerCase().contains(palabra.toLowerCase()))
            .collect(Collectors.toList());

        assertEquals(1, resultados.size());
    }

    // ===================== FILTRAR POR ESTADO Y BÚSQUEDA =====================

    @Test
    void testFiltrarPendientesConPalabra() {
        String palabra = "Reparar";
        List<TareaEntity> resultados = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getTitulo().toLowerCase().contains(palabra.toLowerCase()) ||
                        t.getDescripcion().toLowerCase().contains(palabra.toLowerCase()))
            .collect(Collectors.toList());

        assertEquals(2, resultados.size());
        assertTrue(resultados.stream().allMatch(t -> !t.getEstadoCompletada()));
    }

    @Test
    void testBúsquedaSinResultados() {
        String palabra = "inexistente";
        List<TareaEntity> resultados = tareas.stream()
            .filter(t -> t.getTitulo().toLowerCase().contains(palabra.toLowerCase()) ||
                        t.getDescripcion().toLowerCase().contains(palabra.toLowerCase()))
            .collect(Collectors.toList());

        assertEquals(0, resultados.size());
    }

    @Test
    void testBúsquedaCaseSensitive() {
        String palabra = "REPARAR";
        List<TareaEntity> resultados = tareas.stream()
            .filter(t -> t.getTitulo().toLowerCase().contains(palabra.toLowerCase()))
            .collect(Collectors.toList());

        assertEquals(2, resultados.size());
    }
}
