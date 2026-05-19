package com.example.control2_backend.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TareaDto {
    private Long id;
    private String titulo;
    private String descripcion;
    private LocalDateTime fechaVencimiento;
    private Boolean estadoCompletada;
    private String sectorNombre;

    // Solo enviamos las referencias a las relaciones
    private Long usuarioId;
    private Long sectorId;

}