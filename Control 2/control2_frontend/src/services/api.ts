import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: { 
    'Content-Type': 'application/json; charset=UTF-8',
    'Accept-Charset': 'UTF-8'
  },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export const authApi = {
  login: (username: string, password: string) =>
    api.post('/auth/login', { username, password }),
  register: (username: string, password: string, latitud: number, longitud: number) =>
    api.post('/usuarios/register', { username, password, latitud, longitud }),
  me: () => api.get('/usuarios/me'),
  updateProfile: (data: object) => api.put('/usuarios/me', data),
  deleteAccount: (password: string) => api.delete('/usuarios/me', { data: { password } }),
}

export const tareasApi = {
  getAll: (params?: { estado?: boolean; keyword?: string }) =>
    api.get('/tareas', { params }),
  getById: (id: number) => api.get(`/tareas/${id}`),
  create: (data: object) => api.post('/tareas', data),
  update: (id: number, data: object) => api.put(`/tareas/${id}`, data),
  delete: (id: number) => api.delete(`/tareas/${id}`),
  toggleCompletada: (id: number) => api.patch(`/tareas/${id}/completar`),
  getNotificaciones: () => api.get('/tareas/notificaciones'),

  estadisticas: {
    porSector: () => api.get('/tareas/estadisticas/por-sector'),
    masCercana: () => api.get('/tareas/estadisticas/mas-cercana'),
    sectorRadio2km: () => api.get('/tareas/estadisticas/sector-radio-2km'),
    promedioDistancia: () => api.get('/tareas/estadisticas/promedio-distancia'),
    sectoresPendientes: () => api.get('/tareas/estadisticas/sectores-pendientes'),
    porUsuarioSector: () => api.get('/tareas/estadisticas/por-usuario-sector'),
    sectorRadio5km: () => api.get('/tareas/estadisticas/sector-radio-5km'),
  },
}

export const sectoresApi = {
  getAll: () => api.get('/sectores'),
  create: (data: object) => api.post('/sectores', data),
  update: (id: number, data: object) => api.put(`/sectores/${id}`, data),
  delete: (id: number) => api.delete(`/sectores/${id}`),
}

export default api
