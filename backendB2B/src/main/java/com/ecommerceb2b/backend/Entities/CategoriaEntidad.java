package com.ecommerceb2b.backend.Entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class CategoriaEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int categoria_ID;

    private String nombre_Categoria;
}
