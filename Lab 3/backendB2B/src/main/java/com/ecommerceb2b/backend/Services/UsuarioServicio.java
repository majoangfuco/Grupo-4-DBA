package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Config.JwtMiddlewareService;
import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import com.ecommerceb2b.backend.Repository.UsuarioRepositorio;
import com.ecommerceb2b.backend.Util.RutValidador;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final JwtMiddlewareService jwtMiddlewareService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServicio(UsuarioRepositorio usuarioRepositorio, 
                          JwtMiddlewareService jwtMiddlewareService,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.jwtMiddlewareService = jwtMiddlewareService;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<String> login(String correo, String contrasena) {
        if (correo == null || contrasena == null) {
            return Optional.empty();
        }

        String correoNormalizado = correo.trim().toLowerCase();
        Optional<UsuarioEntidad> usuario = usuarioRepositorio.findByCorreo(correoNormalizado);
        
        if (usuario.isPresent()) {
            String storedPassword = usuario.get().getContrasena();
            boolean passwordMatches = passwordEncoder.matches(contrasena, storedPassword)
                    || storedPassword.equals(contrasena);

            if (passwordMatches) {
                UsuarioEntidad u = usuario.get();
                String token = jwtMiddlewareService.generateToken(u.getUsuario_ID(), u.getCorreo(), u.getRol());
                return Optional.of(token);
            }
        }
        
        return Optional.empty();
    }

    public void registrar(String nombre, String correo, String contrasena, String rutEmpresa) throws Exception {
        String correoNormalizado = correo == null ? null : correo.trim().toLowerCase();
        if (correoNormalizado == null || nombre == null || contrasena == null || rutEmpresa == null) {
            throw new Exception("Todos los campos son requeridos");
        }

        if (!RutValidador.esValido(rutEmpresa)) {
            throw new Exception("El RUT de empresa no es válido");
        }

        if (usuarioRepositorio.findByCorreo(correoNormalizado).isPresent()) {
            throw new Exception("El correo ya está registrado");
        }

        if (usuarioRepositorio.existeRutEmpresa(rutEmpresa)) {
            throw new Exception("El RUT de la empresa ya está registrado");
        }

        UsuarioEntidad usuario = new UsuarioEntidad();
        usuario.setNombre_Usuario(nombre);
        usuario.setCorreo(correoNormalizado);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRut_Empresa(rutEmpresa);
        usuario.setRol("CLIENTE");

        usuarioRepositorio.save(usuario);
    }

    /**
     * Actualiza los datos de una cuenta. Valida correo único y RUT.
     * La contraseña es opcional: si viene vacía o null, se conserva la actual.
     */
    @Transactional
    public UsuarioEntidad actualizarUsuario(Long usuarioId, String nombre, String correo,
                                            String rutEmpresa, String contrasena) throws Exception {
        UsuarioEntidad actual = usuarioRepositorio.findById(usuarioId)
                .orElseThrow(() -> new Exception("Usuario no encontrado"));

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new Exception("El nombre es obligatorio");
        }
        String correoNormalizado = correo == null ? null : correo.trim().toLowerCase();
        if (correoNormalizado == null || correoNormalizado.isEmpty()) {
            throw new Exception("El correo es obligatorio");
        }
        if (rutEmpresa == null || !RutValidador.esValido(rutEmpresa)) {
            throw new Exception("El RUT de empresa no es válido");
        }
        if (!actual.getRut_Empresa().equals(rutEmpresa.trim())) {
            throw new Exception("El RUT de la empresa no puede ser editado");
        }
        if (usuarioRepositorio.existeCorreoEnOtroUsuario(correoNormalizado, usuarioId)) {
            throw new Exception("El correo ya está registrado por otro usuario");
        }

        String contrasenaCodificada = null;
        if (contrasena != null && !contrasena.trim().isEmpty()) {
            contrasenaCodificada = passwordEncoder.encode(contrasena);
        }

        int filas = usuarioRepositorio.actualizar(usuarioId, nombre.trim(),
                correoNormalizado, rutEmpresa.trim(), contrasenaCodificada);
        if (filas == 0) {
            throw new Exception("No se pudo actualizar el usuario");
        }

        actual.setNombre_Usuario(nombre.trim());
        actual.setCorreo(correoNormalizado);
        actual.setRut_Empresa(rutEmpresa.trim());
        return actual;
    }

    /** Elimina la cuenta y todo su historial dependiente en una transacción. */
    @Transactional
    public void eliminarUsuario(Long usuarioId) throws Exception {
        usuarioRepositorio.findById(usuarioId)
                .orElseThrow(() -> new Exception("Usuario no encontrado"));
        usuarioRepositorio.eliminarEnCascada(usuarioId);
    }

    public Optional<UsuarioEntidad> obtenerUsuarioPorId(Long usuarioId) {
        return usuarioRepositorio.findById(usuarioId);
    }

    public Optional<UsuarioEntidad> obtenerUsuarioPorCorreo(String correo) {
        return usuarioRepositorio.findByCorreo(correo);
    }

    public List<UsuarioEntidad> obtenerUsuariosPorRol(String rol) {
        return usuarioRepositorio.findByRol(rol);
    }

    public void actualizarUltimaCompra(Long usuarioId) {
        if (usuarioId == null || usuarioId <= 0) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        usuarioRepositorio.actualizarUltimaCompra(usuarioId);
    }
}
