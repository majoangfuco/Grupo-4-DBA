// ============================================================
// Paleta magenta/amarillo/cian + gris. NO usar rojo/verde.
// Fuente única de verdad de estos colores: no hardcodear hex en componentes.
// ============================================================

export type NivelSemaforo = 'SIN_VENTAS' | 'BAJO' | 'MEDIO' | 'ALTO'

export const COLOR_NIVEL_SEMAFORO: Record<NivelSemaforo, string> = {
  SIN_VENTAS: '#B0B0B0', // gris
  BAJO: '#00BCD4',       // cian
  MEDIO: '#FFEB3B',      // amarillo
  ALTO: '#E91E63',       // magenta
}

export const ETIQUETA_NIVEL_SEMAFORO: Record<NivelSemaforo, string> = {
  SIN_VENTAS: 'Sin ventas registradas',
  BAJO: 'Venta baja',
  MEDIO: 'Venta media',
  ALTO: 'Venta alta',
}

export function colorParaNivel(nivel: string | undefined | null): string {
  return COLOR_NIVEL_SEMAFORO[(nivel as NivelSemaforo)] ?? COLOR_NIVEL_SEMAFORO.SIN_VENTAS
}

export function etiquetaParaNivel(nivel: string | undefined | null): string {
  return ETIQUETA_NIVEL_SEMAFORO[(nivel as NivelSemaforo)] ?? ETIQUETA_NIVEL_SEMAFORO.SIN_VENTAS
}
