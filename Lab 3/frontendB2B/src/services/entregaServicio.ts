import axios from 'axios'
import httpClient from '@/http-common'

export interface InformacionEntregaEntidad {
  info_Entrega_ID: number
  usuarioId: number
  ordenId: number
  direccion: string
  numero: string
  rut_Recibe_Entrega: string
  rut_Empresa?: string
  estado_Entrega: string
  activa: boolean
  comuna: string
  latitud: number
  longitud: number
}

export interface DireccionGeocodificada {
  direccion: string
  numero: string
  comuna: string
  textoCompleto: string
  latitud: number
  longitud: number
}

interface NominatimAddress {
  house_number?: string
  road?: string
  pedestrian?: string
  footway?: string
  residential?: string
  neighbourhood?: string
  suburb?: string
  city_district?: string
  municipality?: string
  city?: string
  town?: string
  village?: string
  state_district?: string
  county?: string
}

interface NominatimReverseResponse {
  display_name?: string
  address?: NominatimAddress
}

export const obtenerEntregasPorUsuario = (
  usuarioId: string | number,
) => {
  return httpClient.get<InformacionEntregaEntidad[]>(
    `/api/entregas/usuario/${usuarioId}`,
  )
}

export const crearEntrega = (
  entrega: Partial<InformacionEntregaEntidad>,
) => {
  return httpClient.post('/api/entregas', entrega)
}

export const obtenerComunasDisponibles = () => {
  return httpClient.get<string[]>('/api/entregas/comunas')
}

/**
 * Convierte las coordenadas entregadas por el navegador en una dirección
 * legible mediante geocodificación inversa.
 */
export const obtenerDireccionDesdeCoordenadas = async (
  latitud: number,
  longitud: number,
): Promise<DireccionGeocodificada> => {
  const response = await axios.get<NominatimReverseResponse>(
    'https://nominatim.openstreetmap.org/reverse',
    {
      params: {
        format: 'jsonv2',
        lat: latitud,
        lon: longitud,
        zoom: 18,
        addressdetails: 1,
        'accept-language': 'es',
      },
      timeout: 12000,
    },
  )

  const address = response.data.address ?? {}

  const direccion =
    address.road ??
    address.pedestrian ??
    address.footway ??
    address.residential ??
    address.neighbourhood ??
    response.data.display_name?.split(',')[0]?.trim() ??
    ''

  const comuna =
    address.municipality ??
    address.city_district ??
    address.town ??
    address.city ??
    address.village ??
    address.suburb ??
    address.state_district ??
    address.county ??
    ''

  return {
    direccion,
    numero: address.house_number ?? '',
    comuna,
    textoCompleto: response.data.display_name ?? '',
    latitud,
    longitud,
  }
}
