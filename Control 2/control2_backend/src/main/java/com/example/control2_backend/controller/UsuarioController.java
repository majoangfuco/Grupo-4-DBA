package com.example.control2_backend.controller;

import com.example.control2_backend.dtos.DeleteAccountRequestDto;
import com.example.control2_backend.dtos.RegisterRequestDto;
import com.example.control2_backend.dtos.UserUpdateRequestDto;
import com.example.control2_backend.dtos.UserUpdateResponseDto;
import com.example.control2_backend.dtos.UsuarioDto;
import com.example.control2_backend.security.JwtUtil;
import com.example.control2_backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    public UsuarioController(UsuarioService usuarioService, JwtUtil jwtUtil) {
        this.usuarioService = usuarioService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<UsuarioDto> register(@Valid @RequestBody RegisterRequestDto dto) {
        return ResponseEntity.ok(usuarioService.register(dto));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDto> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(usuarioService.getByUsername(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserUpdateResponseDto> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserUpdateRequestDto dto
    ) {
        UsuarioDto updatedUser = usuarioService.update(userDetails.getUsername(), dto);
        String newToken = jwtUtil.generateToken(updatedUser.getUsername());
        return ResponseEntity.ok(new UserUpdateResponseDto(updatedUser, newToken));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DeleteAccountRequestDto dto
    ) {
        usuarioService.delete(userDetails.getUsername(), dto.getPassword());
        return ResponseEntity.noContent().build();
    }

    // Obtener usuario por su ID
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.getById(id));
    }
}
