package com.example.control2_backend.controller.Login;

import com.example.control2_backend.dtos.Login.AuthResponseDto;
import com.example.control2_backend.dtos.Login.LoginRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequest) {
        // Spring Security verifica las credenciales
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        //Se guarda la autenticación en el contexto
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generar el JWT
        String token = "aqui_va_el_token_jwt_generado";

        return ResponseEntity.ok(new AuthResponseDto(token));
    }
}