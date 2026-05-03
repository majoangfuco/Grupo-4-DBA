import httpClient from '@/http-common'

export interface ItemFactura {
  carrito_Producto_ID: number
  unidad_producto: number
  producto: {
    producto_ID: number
    nombre_producto: string
    precio: number
  }
}

export interface Factura {
  factura_ID: number
  usuarioId: number
  ordenId: number
  precio_Total: number
  fecha_Emision: string
  total_Neto: number
  iva: number
  items: ItemFactura[]
}

const obtenerFacturaPorOrden = (ordenId: number) =>
  httpClient.get<Factura>(`/api/facturas/orden/${ordenId}`)

const descargarPdf = (facturaId: number) =>
  httpClient.get(`/api/facturas/${facturaId}/descargar`, { responseType: 'blob' })

export const facturasServicio = {
  obtenerFacturaPorOrden,
  descargarPdf,
}
