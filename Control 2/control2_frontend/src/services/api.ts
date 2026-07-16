import axios from 'axios'

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const api = axios.create({
  baseURL: apiBaseURL,
  headers: { 
    'Content-Type': 'application/json; charset=UTF-8'
  },
  // withCredentials: permite al navegador enviar cookies en peticiones cross-origin
  // (necesario para que el browser envíe la cookie XSRF-TOKEN al backend)
  withCredentials: true,
  // Axios leerá automáticamente la cookie 'XSRF-TOKEN' que Spring genera
  // y la enviará como header 'X-XSRF-TOKEN' en cada POST/PUT/DELETE/PATCH
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Función para obtener/refrescar la cookie CSRF ───────────────────────────
// Hace un GET ligero al backend para que Spring genere la cookie XSRF-TOKEN.
// Axios la leerá automáticamente en la siguiente petición mutante.
export async function ensureCsrfCookie(): Promise<void> {
  try {
    await api.get('/auth/csrf', { timeout: 5000 })
  } catch {
    // Si /auth/csrf no existe, cualquier GET autenticado sirve (las tareas, etc.)
    // La cookie se genera en cualquier respuesta gracias al CsrfCookieFilter.
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // Si recibimos 403 en una petición mutante y NO es un reintento,
    // probablemente el CSRF token no estaba sincronizado.
    // La respuesta del 403 ya trae la cookie XSRF-TOKEN actualizada
    // (gracias al CsrfCookieFilter), así que reintentamos directamente.
    if (
      error.response?.status === 403 &&
      !originalRequest._csrfRetry &&
      ['post', 'put', 'delete', 'patch'].includes(originalRequest.method?.toLowerCase())
    ) {
      originalRequest._csrfRetry = true
      return api(originalRequest)
    }

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
