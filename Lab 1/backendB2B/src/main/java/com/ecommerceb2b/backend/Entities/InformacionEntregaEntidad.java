package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InformacionEntregaEntidad {

    private Long info_Entrega_ID;
    private Long usuarioId;
    private Long ordenId;
    private String direccion;
    private String numero;
    private String rut_Recibe_Entrega;
    private String rut_Empresa;
    private String estado_Entrega;
    private Boolean activa;
}