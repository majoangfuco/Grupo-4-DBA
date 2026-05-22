package com.example.control2_backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TareaDto {
    private Long id;

    @NotBlank(message = "El título de la tarea es obligatorio")
    private String titulo;

    private String descripcion;

    @NotBlank(message = "La fecha de vencimiento es obligatoria")
    private String fechaVencimiento;

    private Boolean estadoCompletada;
    private String sectorNombre;

    private Long usuarioId;

    @NotNull(message = "El sector es obligatorio")
    private Long sectorId;
}