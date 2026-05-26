package com.example.control2_backend.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateRequestDto {
    private String username;

    @NotBlank(message = "La contraseña actual es obligatoria para confirmar los cambios")
    private String currentPassword;

    private String newPassword;

    @NotNull(message = "Latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "Latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90.0", message = "Latitud debe estar entre -90 y 90")
    private Double latitud;

    @NotNull(message = "Longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "Longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180.0", message = "Longitud debe estar entre -180 y 180")
    private Double longitud;
}
