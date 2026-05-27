package com.example.control2_backend;

import com.example.control2_backend.entity.SectorEntity;
import com.example.control2_backend.entity.TareaEntity;
import com.example.control2_backend.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Unitarios para el Requisito 5: Asociación con Sectores
 * Valida la asociación de tareas con sectores geoespaciales
 */
class SectorAsociaciónTest {

    private List<SectorEntity> sectores;
    private List<TareaEntity> tareas;
    private UsuarioEntity usuario;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        geometryFactory = new GeometryFactory();

        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setUsername("testuser");

        // Crear sectores con georreferencia
        sectores = new ArrayList<>();

        SectorEntity construccion = new SectorEntity();
        construccion.setId(1L);
        construccion.setNombre("Construcción");
        construccion.setUbicacionEspacial(geometryFactory.createPoint(new Coordinate(-51.20, -33.87)));
        sectores.add(construccion);

        SectorEntity reparacion = new SectorEntity();
        reparacion.setId(2L);
        reparacion.setNombre("Reparación de semáforos");
        reparacion.setUbicacionEspacial(geometryFactory.createPoint(new Coordinate(-51.21, -33.88)));
        sectores.add(reparacion);

        SectorEntity calles = new SectorEntity();
        calles.setId(3L);
        calles.setNombre("Calles");
        calles.setUbicacionEspacial(geometryFactory.createPoint(new Coordinate(-51.22, -33.89)));
        sectores.add(calles);

        // Crear tareas asociadas a sectores
        tareas = new ArrayList<>();

        TareaEntity tarea1 = new TareaEntity();
        tarea1.setId(1L);
        tarea1.setTitulo("Construir muro");
        tarea1.setSector(construccion);
        tarea1.setUsuario(usuario);
        tareas.add(tarea1);

        TareaEntity tarea2 = new TareaEntity();
        tarea2.setId(2L);
        tarea2.setTitulo("Reparar semáforo");
        tarea2.setSector(reparacion);
        tarea2.setUsuario(usuario);
        tareas.add(tarea2);

        TareaEntity tarea3 = new TareaEntity();
        tarea3.setId(3L);
        tarea3.setTitulo("Limpiar calle");
        tarea3.setSector(calles);
        tarea3.setUsuario(usuario);
        tareas.add(tarea3);
    }

    // ===================== VALIDAR SECTORES GEORREFERENCIADOS =====================

    @Test
    void testSectorTieneGeorreferencia() {
        for (SectorEntity sector : sectores) {
            assertNotNull(sector.getUbicacionEspacial());
            assertTrue(sector.getUbicacionEspacial().isValid());
        }
    }

    @Test
    void testSectorTieneCoordenadas() {
        SectorEntity sector = sectores.get(0);
        assertNotNull(sector.getUbicacionEspacial());
        assertNotNull(sector.getUbicacionEspacial().getX());
        assertNotNull(sector.getUbicacionEspacial().getY());
    }

    @Test
    void testSectorDiferentesCoordenadas() {
        SectorEntity sector1 = sectores.get(0);
        SectorEntity sector2 = sectores.get(1);

        assertNotEquals(
            sector1.getUbicacionEspacial().getX(),
            sector2.getUbicacionEspacial().getX()
        );
    }

    // ===================== ASOCIAR TAREAS CON SECTORES =====================

    @Test
    void testTareaAsociadaASector() {
        TareaEntity tarea = tareas.get(0);
        assertNotNull(tarea.getSector());
        assertEquals("Construcción", tarea.getSector().getNombre());
    }

    @Test
    void testTareasDelMismoSector() {
        SectorEntity construccion = sectores.get(0);

        List<TareaEntity> tareasdelSector = tareas.stream()
            .filter(t -> t.getSector().equals(construccion))
            .collect(Collectors.toList());

        assertEquals(1, tareasdelSector.size());
    }

    @Test
    void testCambiarSectorTarea() {
        TareaEntity tarea = tareas.get(0);
        SectorEntity nuevoSector = sectores.get(2);

        tarea.setSector(nuevoSector);
        assertEquals(nuevoSector.getId(), tarea.getSector().getId());
    }

    // ===================== CONSULTAS ESPACIALES =====================

    @Test
    void testObtenerTareasPorSector() {
        SectorEntity reparacion = sectores.stream()
            .filter(s -> s.getNombre().equals("Reparación de semáforos"))
            .findFirst()
            .orElse(null);

        assertNotNull(reparacion);

        List<TareaEntity> tareasdeSector = tareas.stream()
            .filter(t -> t.getSector().equals(reparacion))
            .collect(Collectors.toList());

        assertTrue(tareasdeSector.size() > 0);
    }

    @Test
    void testListarSectoresActivos() {
        List<SectorEntity> sectoresconTareas = new ArrayList<>();

        for (SectorEntity sector : sectores) {
            long countTareas = tareas.stream()
                .filter(t -> t.getSector().equals(sector))
                .count();

            if (countTareas > 0) {
                sectoresconTareas.add(sector);
            }
        }

        assertEquals(3, sectoresconTareas.size());
    }

    @Test
    void testSectorTieneTareas() {
        SectorEntity construccion = sectores.get(0);

        long countTareas = tareas.stream()
            .filter(t -> t.getSector().equals(construccion))
            .count();

        assertTrue(countTareas > 0);
    }

    // ===================== TIPOS DE SECTORES =====================

    @Test
    void testSectorConstrucción() {
        SectorEntity construccion = sectores.stream()
            .filter(s -> s.getNombre().equals("Construcción"))
            .findFirst()
            .orElse(null);

        assertNotNull(construccion);
    }

    @Test
    void testSectorReparación() {
        SectorEntity reparacion = sectores.stream()
            .filter(s -> s.getNombre().equals("Reparación de semáforos"))
            .findFirst()
            .orElse(null);

        assertNotNull(reparacion);
    }

    @Test
    void testSectorCalles() {
        SectorEntity calles = sectores.stream()
            .filter(s -> s.getNombre().equals("Calles"))
            .findFirst()
            .orElse(null);

        assertNotNull(calles);
    }
}
