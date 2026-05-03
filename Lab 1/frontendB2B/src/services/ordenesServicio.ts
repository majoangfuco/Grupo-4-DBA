import httpClient from '@/http-common'

// ── Tipo compartido entre todos los componentes de órdenes ──
export interface Orden {
  orden_ID: number
  carrito_ID: number
  usuario_ID: number
  info_Entrega_ID: number
  fecha_Orden: string
  estado: string
}

export interface OrdenRequest {
  carrito_ID: number
  usuario_ID: number
  info_Entrega_ID: number
  fecha_Orden: string
  estado: string
}

const obtenerTodas = () => httpClient.get<Orden[]>('/api/ordenes')

const obtenerPorId = (id: number) => httpClient.get<Orden>(`/api/ordenes/${id}`)

const obtenerPorUsuario = (usuarioId: number) =>
  httpClient.get<Orden[]>(`/api/ordenes/usuario/${usuarioId}`)

const crear = (orden: OrdenRequest) => httpClient.post<Orden>('/api/ordenes', orden)

const aprobar = (id: number) => httpClient.patch<Orden>(`/api/ordenes/${id}/aprobar`)

const cancelar = (id: number) => httpClient.patch<Orden>(`/api/ordenes/${id}/cancelar`)

const eliminar = (id: number) => httpClient.delete(`/api/ordenes/${id}`)

export const ordenesServicio = {
  obtenerTodas,
  obtenerPorId,
  obtenerPorUsuario,
  crear,
  aprobar,
  cancelar,
  eliminar,
}

