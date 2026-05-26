package com.example.control2_backend.service;

import com.example.control2_backend.dtos.RegisterRequestDto;
import com.example.control2_backend.dtos.UserUpdateRequestDto;
import com.example.control2_backend.dtos.UsuarioDto;
import com.example.control2_backend.entity.UsuarioEntity;
import com.example.control2_backend.repository.UsuarioRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UsuarioDto register(RegisterRequestDto dto) {
        if (usuarioRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso");
        }

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setUsername(dto.getUsername());
        usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        // JTS usa (x=longitud, y=latitud)
        Point point = GF.createPoint(new Coordinate(dto.getLongitud(), dto.getLatitud()));
        usuario.setUbicacionGeografica(point);

        UsuarioEntity saved = usuarioRepository.save(usuario);
        return toDto(saved);
    }

    public UsuarioDto getByUsername(String username) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return toDto(usuario);
    }

    public UsuarioDto getById(Long id) {
        UsuarioEntity usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return toDto(usuario);
    }

    public UsuarioDto update(String currentUsername, UserUpdateRequestDto dto) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 1. Siempre verificar la contraseña actual
        if (!passwordEncoder.matches(dto.getCurrentPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        // 2. Si cambia el nombre de usuario
        if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty() && !dto.getUsername().equals(currentUsername)) {
            if (usuarioRepository.findByUsername(dto.getUsername().trim()).isPresent()) {
                throw new IllegalArgumentException("El nombre de usuario ya está en uso");
            }
            usuario.setUsername(dto.getUsername().trim());
        }

        // 3. Si se proporciona una nueva contraseña
        if (dto.getNewPassword() != null && !dto.getNewPassword().trim().isEmpty()) {
            usuario.setPassword(passwordEncoder.encode(dto.getNewPassword().trim()));
        }

        // 4. Actualizar ubicación
        Point point = GF.createPoint(new Coordinate(dto.getLongitud(), dto.getLatitud()));
        usuario.setUbicacionGeografica(point);

        UsuarioEntity saved = usuarioRepository.save(usuario);
        return toDto(saved);
    }

    public void delete(String username, String password) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña ingresada es incorrecta");
        }

        usuarioRepository.delete(usuario);
    }

    public UsuarioEntity getEntityByUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private UsuarioDto toDto(UsuarioEntity entity) {
        UsuarioDto dto = new UsuarioDto();
        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        if (entity.getUbicacionGeografica() != null) {
            dto.setLatitud(entity.getUbicacionGeografica().getY());
            dto.setLongitud(entity.getUbicacionGeografica().getX());
        }
        return dto;
    }
}
