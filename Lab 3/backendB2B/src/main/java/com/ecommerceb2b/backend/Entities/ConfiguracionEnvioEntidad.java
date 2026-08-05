package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionEnvioEntidad {

    private Long configId;

    // Valor cobrado por kilómetro para la tarifa de envío (última milla).
    private Double valorKm;
}
