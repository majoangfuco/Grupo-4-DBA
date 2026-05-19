package com.example.control2_backend.dtos;

import lombok.Data;

@Data
public class SectorDto {
    private Long id;
    private String nombre;
    private Double latitud;
    private Double longitud;
}