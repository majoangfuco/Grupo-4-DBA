package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificarDireccionRequestDto {

    private Double lat;
    private Double lng;

    // Alternativa GeoJSON Point de entrada: { "type": "Point", "coordinates": [lon, lat] }.
    private String type;
    private List<Double> coordinates;
}
