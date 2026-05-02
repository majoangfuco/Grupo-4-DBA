// ============================================================
// http-common.ts
// Cliente HTTP base con Axios.
//
// 🔧 BACKEND:
//   - Ajusta VITE_API_BASE_URL en tu .env
//     para que apunte a tu servidor Spring Boot / Node / etc.
//   - El interceptor de request adjunta automáticamente el JWT
//     almacenado en localStorage en cada petición autenticada.
// ============================================================

import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

if (!apiBaseUrl) {
  throw new Error('VITE_API_BASE_URL no esta definido en el .env del frontend')
}

const httpClient = axios.create({
  baseURL: apiBaseUrl,
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
    // Si la llamada fue a /login, NO hacemos la redirección global
    // para permitir que Login.vue muestre el mensaje de "Credenciales incorrectas".
    const isLogin = error.config?.url?.includes('/usuario/login')

    if (!isLogin && (error.response?.status === 401 || error.response?.status === 403)) {
      // Token expirado o inválido → limpiamos sesión y redirigimos a login
      localStorage.removeItem('jwt')
      localStorage.removeItem('userId')
      localStorage.removeItem('userEmail')
      localStorage.removeItem('userName')
      localStorage.removeItem('userRole')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default httpClient
