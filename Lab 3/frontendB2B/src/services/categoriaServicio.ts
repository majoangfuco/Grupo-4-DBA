import httpClient from '@/http-common'

export interface CategoriaEntidad {
  categoria_ID: number
  nombre_Categoria: string
  estado_Categoria: boolean
  restringida_Zona_Residencial: boolean
}

export interface CategoriaRequest {
  nombre_Categoria: string
}

const crear = (categoria: CategoriaRequest) => httpClient.post('/api/categorias', categoria)

const listar = (incluirInactivas = false) =>
  httpClient.get<CategoriaEntidad[]>(`/api/categorias?incluirInactivas=${incluirInactivas}`)

const obtenerPorId = (id: number) => httpClient.get<CategoriaEntidad>(`/api/categorias/${id}`)

const actualizar = (id: number, categoria: CategoriaRequest) =>
  httpClient.put(`/api/categorias/${id}`, categoria)

const eliminar = (id: number) => httpClient.delete(`/api/categorias/${id}`)

const buscarPorNombre = (nombre: string, incluirInactivas = false) =>
  httpClient.get<CategoriaEntidad[]>(
    `/api/categorias/buscar?nombre=${encodeURIComponent(nombre)}&incluirInactivas=${incluirInactivas}`,
  )

// Nota: vive bajo /api/admin/categorias (no /api/categorias) — endpoint
// aparte en el backend por requerir rol ADMIN explícito vía el catch-all
// de /api/admin/**; el interceptor de httpClient adjunta el JWT igual.
const actualizarRestringidaZonaResidencial = (id: number, restringidaZonaResidencial: boolean) =>
  httpClient.patch(`/api/admin/categorias/${id}/restringida-zona-residencial`, {
    restringidaZonaResidencial,
  })

export const categoriaServicio = {
  crear,
  listar,
  obtenerPorId,
  actualizar,
  eliminar,
  buscarPorNombre,
  actualizarRestringidaZonaResidencial,
}
