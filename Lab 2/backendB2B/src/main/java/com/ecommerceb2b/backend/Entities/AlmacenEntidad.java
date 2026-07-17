package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlmacenEntidad {

    private Long almacenId;
    private String nombre;
    private String direccion;

    // Coordenadas (WGS84 / SRID 4326)
    private Double latitud;
    private Double longitud;
}
