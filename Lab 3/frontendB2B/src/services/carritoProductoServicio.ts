import httpClient from '@/http-common'

export interface CarritoProductoEntidad {
  carrito_Producto_ID: number
  carrito?: { carrito_ID: number }
  producto?: {
    producto_ID: number
    nombre_producto?: string
    descripcion?: string
    precio?: number
    stock?: number
    sku?: string
    activo?: boolean
  }
  unidad_producto: number
}

export interface AgregarProductoPayload {
  carritoId: number
  productoId: number
  cantidad: number
}

export interface ActualizarCantidadPayload {
  cantidad: number
}

const agregarProducto = (payload: AgregarProductoPayload) =>
  httpClient.post<CarritoProductoEntidad>('/api/carrito-productos', payload)

const obtenerItemPorId = (id: number) =>
  httpClient.get<CarritoProductoEntidad>(`/api/carrito-productos/${id}`)

const listarItemsPorCarrito = (carritoId: number) =>
  httpClient.get<CarritoProductoEntidad[]>(`/api/carrito-productos/carrito/${carritoId}`)

const actualizarCantidad = (id: number, cantidad: number) =>
  httpClient.patch<CarritoProductoEntidad>(`/api/carrito-productos/${id}`, { cantidad })

const eliminarItem = (id: number) => httpClient.delete(`/api/carrito-productos/${id}`)

const calcularSubtotal = (carritoId: number) =>
  httpClient.get<number>(`/api/carrito-productos/carrito/${carritoId}/subtotal`)

export const carritoProductoServicio = {
  agregarProducto,
  obtenerItemPorId,
  listarItemsPorCarrito,
  actualizarCantidad,
  eliminarItem,
  calcularSubtotal,
}
