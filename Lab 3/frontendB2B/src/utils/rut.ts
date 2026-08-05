// ============================================================
// utils/rut.ts
// Validación y formato de RUT/RUN chileno.
// Se usa en el registro y en la edición de cuenta.
// ============================================================

/** Valida que el RUT tenga formato válido (cuerpo numérico + dígito verificador). */
export function validarRut(rut: string): boolean {
  if (!rut) return false

  const limpio = rut.replace(/\./g, '').replace(/-/g, '').trim().toUpperCase()
  if (limpio.length < 2) return false

  const cuerpo = limpio.slice(0, -1)
  const dv = limpio.slice(-1)

  if (!/^\d+$/.test(cuerpo)) return false
  if (!/^[\dK]$/.test(dv)) return false

  return true
}

/** Da formato 12.345.678-9 a un RUT ingresado sin puntos ni guión. */
export function formatearRut(rut: string): string {
  const limpio = rut.replace(/\./g, '').replace(/-/g, '').trim().toUpperCase()
  if (limpio.length < 2) return rut

  const cuerpo = limpio.slice(0, -1)
  const dv = limpio.slice(-1)
  const cuerpoConPuntos = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return `${cuerpoConPuntos}-${dv}`
}
