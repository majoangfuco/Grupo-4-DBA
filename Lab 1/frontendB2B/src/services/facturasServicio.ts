import httpClient from '@/http-common'

export interface Factura {
  factura_ID: number
  usuarioId: number
  ordenId: number
  precio_Total: number
  fecha_Emision: string
  total_Neto: number
  iva: number
}

const obtenerFacturaPorOrden = (ordenId: number) => 
  httpClient.get<Factura>(`/api/facturas/orden/${ordenId}`)

export const facturasServicio = {
  obtenerFacturaPorOrden
}
