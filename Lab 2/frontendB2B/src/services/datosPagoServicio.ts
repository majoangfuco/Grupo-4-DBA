import httpClient from '@/http-common'

export interface DatosDePagoEntidad {
  datos_Pago_ID: number
  usuario_ID: number
  metodo_Pago: string
  numero_Tarjeta: string
  fecha_Expiracion: string
}

export const obtenerDatosPagoPorUsuario = (usuarioId: string | number) => {
  return httpClient.get<DatosDePagoEntidad[]>(`/api/datos-pago/usuario/${usuarioId}`)
}

export const crearDatosPago = (datos: Partial<DatosDePagoEntidad>) => {
  return httpClient.post(`/api/datos-pago`, datos)
}
