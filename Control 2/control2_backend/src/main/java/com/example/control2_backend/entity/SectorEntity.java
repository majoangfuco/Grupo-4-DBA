package com.example.control2_backend.entity;

import org.locationtech.jts.geom.Point;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "sectores")
public class SectorEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; // Ej: "Construcción", "Reparación semáforos"

    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point ubicacionEspacial;

    @OneToMany(mappedBy = "sector")
    private List<TareaEntity> tareas;

}
