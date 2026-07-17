import httpClient from '@/http-common'

export interface CategoriaEntidad {
  categoria_ID: number
  nombre_Categoria: string
  estado_Categoria: boolean
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

export const categoriaServicio = {
  crear,
  listar,
  obtenerPorId,
  actualizar,
  eliminar,
  buscarPorNombre,
}
