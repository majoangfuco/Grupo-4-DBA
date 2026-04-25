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
public class FacturaEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long factura_ID;

    @ManyToOne
    @JoinColumn(name = "usuario_usuario")
    private UsuarioEntidad usuario;

    @OneToOne
    @JoinColumn(name = "orden_orden_id")
    private OrdenesEntidad orden;

    private Float precio_Total;

    private Date fecha_Emision;

    private Float total_Neto;

    private Float iva;



}
