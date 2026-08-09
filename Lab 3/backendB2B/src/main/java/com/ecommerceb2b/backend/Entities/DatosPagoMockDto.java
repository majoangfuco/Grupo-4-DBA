package com.ecommerceb2b.backend.Entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mock de validación de pago para el checkout documental (Lab 3).
 *
 * No integra ninguna pasarela real: el checkout confía en {@code aprobado}
 * tal cual llega. {@code medioPago}/{@code referencia} son solo para
 * trazabilidad en la factura y no se validan contra nada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatosPagoMockDto {

    private boolean aprobado;
    private String medioPago;
    private String referencia;
}
