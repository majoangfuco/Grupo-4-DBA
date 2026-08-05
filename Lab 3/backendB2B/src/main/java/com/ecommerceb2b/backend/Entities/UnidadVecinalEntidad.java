package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadVecinalEntidad {

    private Long id;
    private Long comunaId;
    private String codigoUv;
    private String nombreUv;

    // WKT (Well-Known Text) — nunca un tipo JTS aquí. Mismo criterio que
    // el resto del proyecto usa para geometría en una Entidad (ver
    // InformacionEntregaEntidad, que descompone su Point en lat/lon
    // escalares): la geometría vive como texto nativo de PostGIS,
    // convertida en el Repositorio con ST_GeomFromText / ST_AsText.
    private String geom;

    private Boolean esZonaProtegida;
}
