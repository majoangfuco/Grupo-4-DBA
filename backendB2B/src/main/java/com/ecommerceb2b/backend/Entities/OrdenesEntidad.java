package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdenesEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int orden_ID;

    @OneToOne
    @JoinColumn(name = "carrito_carrito_id")
    private CarritoEntidad carrito;

    @ManyToOne
    @JoinColumn(name = "informacion_info_entrega_id")
    private InformacionEntregaEntidad info_Entrega;

    private Date fecha_Orden;

    private String estado;


}
