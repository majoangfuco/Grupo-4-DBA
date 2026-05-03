import httpClient from '@/http-common'

export interface CarritoEntidad {
  carrito_ID: number
  usuario?: { usuario_ID: number }
  estado: string
  ultima_Actualizacion?: string
  costo_Carrito?: number
}

const obtenerOCrearPorCliente = (idCliente: number) =>
  httpClient.get<CarritoEntidad>(`/api/carritos/cliente/${idCliente}/activo`)

const obtenerPorId = (id: number) => httpClient.get<CarritoEntidad>(`/api/carritos/${id}`)

const listarPorCliente = (idCliente: number) =>
  httpClient.get<CarritoEntidad[]>(`/api/carritos/cliente/${idCliente}`)

const vaciar = (id: number) => httpClient.post(`/api/carritos/${id}/vaciar`)

const cerrar = (id: number) => httpClient.patch(`/api/carritos/${id}/cerrar`)

const pagar = (id: number) => httpClient.patch(`/api/carritos/${id}/pagar`)

export const carritoServicio = {
  obtenerOCrearPorCliente,
  obtenerPorId,
  listarPorCliente,
  vaciar,
  cerrar,
  pagar,
}
