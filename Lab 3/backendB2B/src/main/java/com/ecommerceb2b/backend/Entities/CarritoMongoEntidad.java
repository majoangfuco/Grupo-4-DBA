package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Implementación NoSQL paralela a la entidad CarritoEntidad
 * Para el uso de MongoDB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarritoMongoEntidad {
    private Long id;
    private Long clienteId;
    //("ACTIVO", "ABANDONADO", "PAGADO")
    private String estado;
    private List<ItemCarritoMongoEntidad> items;
    private Date creadoEn;
    private Date ultimaActividad;
}
