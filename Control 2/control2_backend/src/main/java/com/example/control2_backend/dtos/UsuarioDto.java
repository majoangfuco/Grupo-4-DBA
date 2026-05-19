package com.example.control2_backend.dtos;

import lombok.Data;

@Data
public class UsuarioDto {
    private Long id;
    private String username;

    // Coordenadas planas extraídas del Point espacial
    private Double latitud;
    private Double longitud;
}