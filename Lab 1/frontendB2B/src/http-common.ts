// ============================================================
// http-common.ts
// Cliente HTTP base con Axios.
//
// 🔧 BACKEND PENDIENTE:
//   - Ajusta VITE_BACKEND_SERVER y VITE_BACKEND_PORT en tu .env
//     para que apunten a tu servidor Spring Boot / Node / etc.
//   - El interceptor de request adjunta automáticamente el JWT
//     almacenado en localStorage en cada petición autenticada.
// ============================================================

import axios from 'axios'

const BackendServer = import.meta.env.VITE_BACKEND_SERVER ?? 'localhost'
const BackendPort   = import.meta.env.VITE_BACKEND_PORT   ?? '8090'

const httpClient = axios.create({
  baseURL: `http://${BackendServer}:${BackendPort}`,
  headers: {
    'Content-Type': 'application/json',
  },
})

// -----------------------------------------------------------------
// Interceptor de REQUEST → agrega el token JWT en cada llamada
// -----------------------------------------------------------------
httpClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('jwt')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// -----------------------------------------------------------------
// Interceptor de RESPONSE → manejo global de errores 401 / 403
// -----------------------------------------------------------------
httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      // Token expirado o inválido → limpiamos sesión y redirigimos a login
      localStorage.removeItem('jwt')
      localStorage.removeItem('userId')
      localStorage.removeItem('userEmail')
      localStorage.removeItem('userRole')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default httpClient
