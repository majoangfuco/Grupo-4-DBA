package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Config.JwtMiddlewareService;
import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import com.ecommerceb2b.backend.Repository.UsuarioRepositorio;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

        if (usuarioRepositorio.findByCorreo(correoNormalizado).isPresent()) {
            throw new Exception("El correo ya está registrado");
        }

        UsuarioEntidad usuario = new UsuarioEntidad();
        usuario.setNombre_Usuario(nombre);
        usuario.setCorreo(correoNormalizado);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRut_Empresa(rutEmpresa);
        usuario.setRol("CLIENTE");

        usuarioRepositorio.save(usuario);
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
}
