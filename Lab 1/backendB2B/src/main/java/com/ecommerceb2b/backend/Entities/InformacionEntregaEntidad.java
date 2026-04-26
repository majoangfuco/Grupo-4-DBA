package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InformacionEntregaEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long info_Entrega_ID;

    @ManyToOne
    @JoinColumn(name = "usuario_usuario")
    private UsuarioEntidad usuario;

    @ManyToOne
    @JoinColumn(name = "orden_orden_id")
    private OrdenesEntidad orden;

    private String direccion;

    private String numero;

    private String rut_Recibe_Entrega;

    private String rut_Empresa;


}
