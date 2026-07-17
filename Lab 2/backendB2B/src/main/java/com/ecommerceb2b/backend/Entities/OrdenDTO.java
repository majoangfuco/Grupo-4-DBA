package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdenDTO {
    private Long orden_ID;
    private String rut_Empresa;
    private Date fecha_Orden;
    private String estado;
    private Long usuario_ID;
}
