package com.example.control2_backend;

import com.example.control2_backend.entity.UsuarioEntity;
import com.example.control2_backend.entity.SectorEntity;
import com.example.control2_backend.entity.TareaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Unitarios para el Sistema de Gestión de Tareas
 * Prueba la creación y validación de entidades principales
 */
class ControlSystemTest {

    private GeometryFactory geometryFactory;
    private UsuarioEntity usuario;
    private SectorEntity sector;
    private TareaEntity tarea;

    @BeforeEach
    void setUp() {
        geometryFactory = new GeometryFactory();

        // Crear usuario con ubicación geoespacial
        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setUsername("testuser");
        usuario.setPassword("hashedPassword123");
        // Ubicación: Latitude -33.8688, Longitude -51.2093 (Buenos Aires)
        usuario.setUbicacionGeografica(geometryFactory.createPoint(new Coordinate(-51.2093, -33.8688)));

        // Crear sector con ubicación geoespacial
        sector = new SectorEntity();
        sector.setId(1L);
        sector.setNombre("Centro");
        // Ubicación del sector
        sector.setUbicacionEspacial(geometryFactory.createPoint(new Coordinate(-51.2, -33.87)));

        // Crear tarea
        tarea = new TareaEntity();
        tarea.setId(1L);
        tarea.setTitulo("Reparar semáforo");
        tarea.setDescripcion("Semáforo roto en esquina");
        tarea.setFechaVencimiento(LocalDateTime.now().plusDays(3));
        tarea.setEstadoCompletada(false);
        tarea.setUsuario(usuario);
        tarea.setSector(sector);
    }

    // ===================== TESTS DE USUARIOS =====================

    @Test
    void testRegistroUsuario() {
        assertNotNull(usuario);
        assertEquals("testuser", usuario.getUsername());
        assertNotNull(usuario.getUbicacionGeografica());
        assertTrue(usuario.getId() > 0);
    }

    @Test
    void testUbicacionGeograficaUsuario() {
        Point ubicacion = usuario.getUbicacionGeografica();
        assertNotNull(ubicacion);
        assertEquals(-51.2093, ubicacion.getX(), 0.0001);
        assertEquals(-33.8688, ubicacion.getY(), 0.0001);
    }

    @Test
    void testValidacionNombreUsuario() {
        assertNotNull(usuario.getUsername());
        assertFalse(usuario.getUsername().isEmpty());
        assertTrue(usuario.getUsername().length() >= 3);
    }

    // ===================== TESTS DE TAREAS =====================

    @Test
    void testCreacionTarea() {
        assertNotNull(tarea);
        assertEquals("Reparar semáforo", tarea.getTitulo());
        assertEquals("Semáforo roto en esquina", tarea.getDescripcion());
        assertFalse(tarea.getEstadoCompletada());
    }

    @Test
    void testAsociacionTareaAlUsuario() {
        assertEquals(usuario.getId(), tarea.getUsuario().getId());
        assertEquals("testuser", tarea.getUsuario().getUsername());
    }

    @Test
    void testAsociacionTareaAlSector() {
        assertNotNull(tarea.getSector());
        assertEquals(sector.getId(), tarea.getSector().getId());
        assertEquals("Centro", tarea.getSector().getNombre());
    }

    @Test
    void testMarcarTareaCompletada() {
        assertFalse(tarea.getEstadoCompletada());
        tarea.setEstadoCompletada(true);
        assertTrue(tarea.getEstadoCompletada());
    }

    @Test
    void testFechaVencimientoTarea() {
        LocalDateTime fechaVencimiento = tarea.getFechaVencimiento();
        assertNotNull(fechaVencimiento);
        assertTrue(fechaVencimiento.isAfter(LocalDateTime.now()));
    }

    @Test
    void testEditarTarea() {
        String nuevoTitulo = "Semáforo reparado";
        tarea.setTitulo(nuevoTitulo);
        assertEquals(nuevoTitulo, tarea.getTitulo());

        String nuevaDescripcion = "Semáforo en buen estado";
        tarea.setDescripcion(nuevaDescripcion);
        assertEquals(nuevaDescripcion, tarea.getDescripcion());
    }

    // ===================== TESTS DE SECTORES =====================

    @Test
    void testCreacionSector() {
        assertNotNull(sector);
        assertEquals("Centro", sector.getNombre());
        assertEquals(1L, sector.getId());
    }

    @Test
    void testUbicacionGeoespacialSector() {
        Point ubicacion = sector.getUbicacionEspacial();
        assertNotNull(ubicacion);
        assertEquals(-51.2, ubicacion.getX(), 0.0001);
        assertEquals(-33.87, ubicacion.getY(), 0.0001);
    }

    @Test
    void testEditarSector() {
        sector.setNombre("Centro Actualizado");
        assertEquals("Centro Actualizado", sector.getNombre());
    }

    // ===================== TESTS DE OPERACIONES ESPACIALES =====================

    @Test
    void testCalculoDistancia() {
        Point ubicacionUsuario = usuario.getUbicacionGeografica();
        Point ubicacionTarea = sector.getUbicacionEspacial();

        assertNotNull(ubicacionUsuario);
        assertNotNull(ubicacionTarea);

        // Distancia no nula
        double distancia = ubicacionUsuario.distance(ubicacionTarea);
        assertTrue(distancia >= 0);
    }

    @Test
    void testVerificacionPuntosGeoespaciales() {
        // Verificar que todas las entidades tienen puntos válidos
        assertNotNull(usuario.getUbicacionGeografica());
        assertNotNull(sector.getUbicacionEspacial());

        // Verificar que los puntos tienen coordenadas válidas
        assertTrue(usuario.getUbicacionGeografica().isValid());
        assertTrue(sector.getUbicacionEspacial().isValid());
    }

    // ===================== TESTS DE VALIDACIONES =====================

    @Test
    void testValidacionTareaCompleta() {
        assertNotNull(tarea.getTitulo());
        assertNotNull(tarea.getDescripcion());
        assertNotNull(tarea.getFechaVencimiento());
        assertNotNull(tarea.getUsuario());
        assertNotNull(tarea.getSector());
    }

    @Test
    void testValidacionEstadoTarea() {
        boolean estado = tarea.getEstadoCompletada();
        assertFalse(estado);

        tarea.setEstadoCompletada(true);
        assertTrue(tarea.getEstadoCompletada());

        tarea.setEstadoCompletada(false);
        assertFalse(tarea.getEstadoCompletada());
    }

    @Test
    void testRelaciónTareaUsuarioSector() {
        // Verificar que la tarea está relacionada correctamente
        assertEquals(usuario, tarea.getUsuario());
        assertEquals(sector, tarea.getSector());

        // Cambiar de sector
        SectorEntity nuevoSector = new SectorEntity();
        nuevoSector.setId(2L);
        nuevoSector.setNombre("Reparación");

        tarea.setSector(nuevoSector);
        assertEquals(nuevoSector.getId(), tarea.getSector().getId());
    }

    @Test
    void testMultiplesTareasUsuario() {
        // Crear múltiples tareas para el mismo usuario
        TareaEntity tarea2 = new TareaEntity();
        tarea2.setId(2L);
        tarea2.setTitulo("Reparar calle");
        tarea2.setUsuario(usuario);

        TareaEntity tarea3 = new TareaEntity();
        tarea3.setId(3L);
        tarea3.setTitulo("Limpiar zona");
        tarea3.setUsuario(usuario);

        // Verificar que todas pertenecen al mismo usuario
        assertEquals(usuario.getId(), tarea.getUsuario().getId());
        assertEquals(usuario.getId(), tarea2.getUsuario().getId());
        assertEquals(usuario.getId(), tarea3.getUsuario().getId());
    }

    // ===================== TESTS DE FILTROS =====================

    @Test
    void testFiltrarPorEstado() {
        // Crear tareas con diferentes estados
        TareaEntity tareaPendiente = new TareaEntity();
        tareaPendiente.setEstadoCompletada(false);

        TareaEntity tareaCompletada = new TareaEntity();
        tareaCompletada.setEstadoCompletada(true);

        assertFalse(tareaPendiente.getEstadoCompletada());
        assertTrue(tareaCompletada.getEstadoCompletada());
    }

    @Test
    void testBusquedaPorPalabra() {
        String titulo = tarea.getTitulo();
        String descripcion = tarea.getDescripcion();

        assertTrue(titulo.contains("semáforo") || titulo.toLowerCase().contains("semáforo"));
        assertTrue(descripcion.contains("roto") || descripcion.toLowerCase().contains("roto"));
    }
}
