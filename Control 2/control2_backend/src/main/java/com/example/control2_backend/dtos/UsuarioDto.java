package com.example.control2_backend.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class UsuarioDto {
    private Long id;
    private String username;

    @DecimalMin(value = "-90.0", message = "Latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90.0", message = "Latitud debe estar entre -90 y 90")
    private Double latitud;

    @DecimalMin(value = "-180.0", message = "Longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180.0", message = "Longitud debe estar entre -180 y 180")
    private Double longitud;
}