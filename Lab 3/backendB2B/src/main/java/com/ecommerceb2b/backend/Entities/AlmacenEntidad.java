package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    // Alternativa GeoJSON Point de entrada: { "type": "Point", "coordinates": [lon, lat] }.
    // Se normaliza a latitud/longitud vía CoordenadasNormalizador antes de persistir.
    private String type;
    private List<Double> coordinates;
}
