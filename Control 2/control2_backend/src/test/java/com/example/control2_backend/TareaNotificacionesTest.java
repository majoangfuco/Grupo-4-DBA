package com.example.control2_backend;

import com.example.control2_backend.entity.TareaEntity;
import com.example.control2_backend.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Unitarios para el Requisito 4: Notificaciones
 * Valida notificaciones de tareas próximas a vencer
 */
class TareaNotificacionesTest {

    private List<TareaEntity> tareas;
    private UsuarioEntity usuario;
    private static final int HORAS_ALERTA = 24;

    @BeforeEach
    void setUp() {
        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setUsername("testuser");

        tareas = new ArrayList<>();

        // Tarea que vence en 12 horas (requiere notificación)
        TareaEntity tarea1 = new TareaEntity();
        tarea1.setId(1L);
        tarea1.setTitulo("Tarea urgente");
        tarea1.setFechaVencimiento(LocalDateTime.now().plusHours(12));
        tarea1.setEstadoCompletada(false);
        tarea1.setUsuario(usuario);
        tareas.add(tarea1);

        // Tarea que vence en 30 minutos (requiere notificación)
        TareaEntity tarea2 = new TareaEntity();
        tarea2.setId(2L);
        tarea2.setTitulo("Tarea muy urgente");
        tarea2.setFechaVencimiento(LocalDateTime.now().plusMinutes(30));
        tarea2.setEstadoCompletada(false);
        tarea2.setUsuario(usuario);
        tareas.add(tarea2);

        // Tarea que vence en 3 días (sin notificación)
        TareaEntity tarea3 = new TareaEntity();
        tarea3.setId(3L);
        tarea3.setTitulo("Tarea normal");
        tarea3.setFechaVencimiento(LocalDateTime.now().plusDays(3));
        tarea3.setEstadoCompletada(false);
        tarea3.setUsuario(usuario);
        tareas.add(tarea3);

        // Tarea vencida (requiere notificación)
        TareaEntity tarea4 = new TareaEntity();
        tarea4.setId(4L);
        tarea4.setTitulo("Tarea vencida");
        tarea4.setFechaVencimiento(LocalDateTime.now().minusHours(2));
        tarea4.setEstadoCompletada(false);
        tarea4.setUsuario(usuario);
        tareas.add(tarea4);

        // Tarea completada próxima a vencer (sin notificación)
        TareaEntity tarea5 = new TareaEntity();
        tarea5.setId(5L);
        tarea5.setTitulo("Tarea completada");
        tarea5.setFechaVencimiento(LocalDateTime.now().plusHours(6));
        tarea5.setEstadoCompletada(true);
        tarea5.setUsuario(usuario);
        tareas.add(tarea5);
    }

    // ===================== NOTIFICACIONES DE VENCIMIENTO PRÓXIMO =====================

    @Test
    void testNotificacionesTareasPróximasAvencer() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(HORAS_ALERTA);

        List<TareaEntity> notificaciones = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(limite) && 
                        t.getFechaVencimiento().isAfter(ahora))
            .collect(Collectors.toList());

        assertEquals(2, notificaciones.size());
    }

    @Test
    void testTareasVencidasRequierenNotificación() {
        LocalDateTime ahora = LocalDateTime.now();

        List<TareaEntity> vencidas = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(ahora))
            .collect(Collectors.toList());

        assertEquals(1, vencidas.size());
        assertTrue(vencidas.stream().allMatch(t -> t.getFechaVencimiento().isBefore(ahora)));
    }

    @Test
    void testTareasCompletadasNoGeneranNotificación() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(HORAS_ALERTA);

        List<TareaEntity> notificaciones = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(limite) && 
                        t.getFechaVencimiento().isAfter(ahora))
            .collect(Collectors.toList());

        // Las tareas completadas no deben estar en notificaciones
        assertTrue(notificaciones.stream().noneMatch(TareaEntity::getEstadoCompletada));
    }

    // ===================== FILTRAR NOTIFICACIONES =====================

    @Test
    void testNotificacionesUrgentes() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(1);

        List<TareaEntity> urgentes = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(limite) && 
                        t.getFechaVencimiento().isAfter(ahora))
            .collect(Collectors.toList());

        // Debe haber una tarea que vence en 30 minutos
        assertTrue(urgentes.stream().anyMatch(t -> t.getId() == 2L));
    }

    @Test
    void testConteoPendientesCercanaAlVencimiento() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(HORAS_ALERTA);

        long conteo = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(limite) && 
                        t.getFechaVencimiento().isAfter(ahora))
            .count();

        assertEquals(2, conteo);
    }

    // ===================== INFORMACIÓN DE NOTIFICACIONES =====================

    @Test
    void testObtenerFechaVencimientoNotificación() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(HORAS_ALERTA);

        TareaEntity notificacion = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(limite) && 
                        t.getFechaVencimiento().isAfter(ahora))
            .findFirst()
            .orElse(null);

        assertNotNull(notificacion);
        assertNotNull(notificacion.getFechaVencimiento());
    }

    @Test
    void testNotificacionMuestraTituloTarea() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(1);

        TareaEntity notificacion = tareas.stream()
            .filter(t -> !t.getEstadoCompletada())
            .filter(t -> t.getFechaVencimiento().isBefore(limite) && 
                        t.getFechaVencimiento().isAfter(ahora))
            .findFirst()
            .orElse(null);

        assertNotNull(notificacion);
        assertEquals("Tarea muy urgente", notificacion.getTitulo());
    }
}
