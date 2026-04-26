package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.*;

import java.util.Date;

@Entity
public class UsuarioEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long usuario_ID;

    private String nombre_Usuario;

    private String correo ;

    private String contrasena ;

    private Date ultima_Compra;

    private String rut_Empresa;

    private String rol;


}
