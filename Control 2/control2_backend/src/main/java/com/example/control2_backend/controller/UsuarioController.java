package com.example.control2_backend.controller;

import com.example.control2_backend.dtos.RegisterRequestDto;
import com.example.control2_backend.dtos.UsuarioDto;
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

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/register")
    public ResponseEntity<UsuarioDto> register(@Valid @RequestBody RegisterRequestDto dto) {
        return ResponseEntity.ok(usuarioService.register(dto));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDto> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(usuarioService.getByUsername(userDetails.getUsername()));
    }
}
