package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAlmacenProductoDto {

    private Long almacenId;
    private Long productoId;
    private String nombreProducto;
    private Integer stockDisponible;
}
