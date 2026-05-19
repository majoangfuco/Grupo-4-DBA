package com.example.control2_backend.dtos;

import lombok.Data;

@Data
public class TareaDto {
    private Long id;
    private String titulo;
    private String descripcion;
    private String fechaVencimiento;
    private Boolean estadoCompletada;
    private String sectorNombre;

    private Long usuarioId;
    private Long sectorId;
}