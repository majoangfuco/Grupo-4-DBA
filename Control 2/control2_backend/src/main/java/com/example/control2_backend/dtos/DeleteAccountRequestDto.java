package com.example.control2_backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequestDto {
    @NotBlank(message = "La contraseña es requerida para confirmar")
    private String password;
}
