package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarritoEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long carrito_ID;

    @ManyToOne
    @JoinColumn(name = "carrito_usuario_id")
    private UsuarioEntidad usuario;

    private String estado;

    private Long costo_Carrito;
}
