package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdenesEntidad {
    private Long orden_ID;
    private Long carrito_ID;
    private Long usuario_ID;
    private String rut_Empresa;
    private Long info_Entrega_ID;
    private Date fecha_Orden;
    //Estados: "PENDIENTE", "APROBADA", "CANCELADA"
    private String estado;
    private Long almacen_Asignado_ID;
    private String almacen_Nombre;
    private Double distancia_envio_km;
}
