package com.example.control2_backend.entity;

import org.locationtech.jts.geom.Point;
import jakarta.persistence.*;
import java.util.List;


@Entity
@Table(name = "usuarios")
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    // SRID 4326 es el estándar para coordenadas GPS (WGS 84)
    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point ubicacionGeografica;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<TareaEntity> tareas;

}