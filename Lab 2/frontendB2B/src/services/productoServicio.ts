import httpClient from '@/http-common'

export interface ProductoRequest {
  categoria_ID: number
  nombre_producto: string
  descripcion: string
  precio: number
  stock: number
  sku: string
  activo?: boolean
}

const crearProducto = (producto: ProductoRequest) =>
  httpClient.post('/api/productos', producto)

const obtenerTodos = () => httpClient.get('/api/productos')

const obtenerPorId = (id: number) => httpClient.get(`/api/productos/${id}`)

const actualizar = (id: number, producto: ProductoRequest) => httpClient.put(`/api/productos/${id}`, producto)

const actualizarStock = (id: number, nuevoStock: number) => httpClient.patch(`/api/productos/${id}/stock?nuevoStock=${nuevoStock}`)

const eliminar = (id: number) => httpClient.delete(`/api/productos/${id}`)

const aplicarDescuentoPorCategoria = (categoriaId: number, porcentaje: number) =>
  httpClient.post(`/api/productos/descuento?categoriaId=${categoriaId}&porcentaje=${porcentaje}`)

export const productoServicio = {
  crearProducto,
  crearLote: crearProducto,
  obtenerTodos,
  obtenerPorId,
  actualizar,
  actualizarStock,
  eliminar,
  aplicarDescuentoPorCategoria,
}
