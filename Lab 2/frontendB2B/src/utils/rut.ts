// ============================================================
// utils/rut.ts
// Validación y formato de RUT/RUN chileno (Módulo 11).
// Se usa en el registro y en la edición de cuenta.
// ============================================================

/** Valida el dígito verificador de un RUT en cualquier formato. */
export function validarRut(rut: string): boolean {
  if (!rut) return false

  const limpio = rut.replace(/\./g, '').replace(/-/g, '').trim().toUpperCase()
  if (limpio.length < 2) return false

  const cuerpo = limpio.slice(0, -1)
  const dv = limpio.slice(-1)

  if (!/^\d+$/.test(cuerpo)) return false

  let suma = 0
  let multiplicador = 2
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * multiplicador
    multiplicador = multiplicador === 7 ? 2 : multiplicador + 1
  }

  const resto = 11 - (suma % 11)
  const dvEsperado = resto === 11 ? '0' : resto === 10 ? 'K' : String(resto)

  return dv === dvEsperado
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
