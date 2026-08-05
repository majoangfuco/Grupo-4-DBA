import httpClient from '@/http-common'

export interface AlmacenEntidad {
  almacenId: number
  nombre: string
  direccion: string
  latitud: number
  longitud: number
}

export type AlmacenNuevo = Omit<AlmacenEntidad, 'almacenId'>

export interface StockAlmacenProducto {
  almacenId: number
  productoId: number
  nombreProducto: string
  stockDisponible: number
}

const listar = () => httpClient.get<AlmacenEntidad[]>(`/api/almacenes`)

const obtenerGeoJson = () => httpClient.get<string>(`/api/almacenes/geojson`)

const crear = (almacen: AlmacenNuevo) =>
  httpClient.post(`/api/almacenes`, almacen)

const actualizar = (id: number, almacen: AlmacenNuevo) =>
  httpClient.put(`/api/almacenes/${id}`, almacen)

const eliminar = (id: number) => httpClient.delete(`/api/almacenes/${id}`)

// Stock por almacén
const listarStock = (id: number) =>
  httpClient.get<StockAlmacenProducto[]>(`/api/almacenes/${id}/stock`)

const actualizarStock = (id: number, productoId: number, stockDisponible: number) =>
  httpClient.put(`/api/almacenes/${id}/stock`, { productoId, stockDisponible })

export const almacenServicio = {
  listar,
  obtenerGeoJson,
  crear,
  actualizar,
  eliminar,
  listarStock,
  actualizarStock,
}
