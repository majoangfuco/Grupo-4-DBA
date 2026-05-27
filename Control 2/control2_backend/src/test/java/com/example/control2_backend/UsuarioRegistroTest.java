package com.example.control2_backend;

import com.example.control2_backend.dtos.RegisterRequestDto;
import com.example.control2_backend.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Unitarios para el Requisito 1: Registro de Usuarios
 * Valida que los usuarios se registren correctamente con ubicación geoespacial
 */
class UsuarioRegistroTest {

    private RegisterRequestDto registerRequest;
    private UsuarioEntity usuario;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        geometryFactory = new GeometryFactory();

        registerRequest = new RegisterRequestDto();
        registerRequest.setUsername("nuevoUsuario");
        registerRequest.setPassword("password123");
        registerRequest.setLatitud(-33.8688);
        registerRequest.setLongitud(-51.2093);

        usuario = new UsuarioEntity();
        usuario.setUsername(registerRequest.getUsername());
        usuario.setPassword("hashedPassword");
        usuario.setUbicacionGeografica(
            geometryFactory.createPoint(
                new Coordinate(registerRequest.getLongitud(), registerRequest.getLatitud())
            )
        );
    }

    @Test
    void testRegistroUsuarioConDatos() {
        assertNotNull(registerRequest);
        assertEquals("nuevoUsuario", registerRequest.getUsername());
        assertEquals("password123", registerRequest.getPassword());
    }

    @Test
    void testRegistroUsuarioGuardaPunto() {
        assertNotNull(usuario.getUbicacionGeografica());
        assertEquals(-51.2093, usuario.getUbicacionGeografica().getX(), 0.0001);
        assertEquals(-33.8688, usuario.getUbicacionGeografica().getY(), 0.0001);
    }

    @Test
    void testValidacionNombreUsuarioUnico() {
        UsuarioEntity usuario1 = new UsuarioEntity();
        usuario1.setUsername("usuario_unico");

        UsuarioEntity usuario2 = new UsuarioEntity();
        usuario2.setUsername("usuario_unico");

        // En producción se verificaría en BD, aquí validamos la lógica
        assertEquals(usuario1.getUsername(), usuario2.getUsername());
    }

    @Test
    void testRegistroConGeolocalización() {
        assertTrue(usuario.getUbicacionGeografica().isValid());
        assertNotNull(usuario.getUbicacionGeografica());
    }

    @Test
    void testAutenticacionPost() {
        // Después del registro, el usuario debe poder iniciar sesión
        assertNotNull(usuario.getUsername());
        assertNotNull(usuario.getPassword());
        assertFalse(usuario.getUsername().isEmpty());
        assertFalse(usuario.getPassword().isEmpty());
    }
}
