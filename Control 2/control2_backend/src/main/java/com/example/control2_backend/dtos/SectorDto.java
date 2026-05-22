package com.example.control2_backend.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SectorDto {
    private Long id;

    @NotBlank(message = "El nombre del sector es obligatorio")
    private String nombre;

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "Latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90.0", message = "Latitud debe estar entre -90 y 90")
    private Double latitud;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "Longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180.0", message = "Longitud debe estar entre -180 y 180")
    private Double longitud;
}