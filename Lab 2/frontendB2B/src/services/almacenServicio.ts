import httpClient from '@/http-common'

export interface AlmacenEntidad {
  almacenId: number
  nombre: string
  direccion: string
  latitud: number
  longitud: number
}

const listar = () => httpClient.get<AlmacenEntidad[]>(`/api/almacenes`)

const obtenerGeoJson = () => httpClient.get<string>(`/api/almacenes/geojson`)

export const almacenServicio = {
  listar,
  obtenerGeoJson,
}
