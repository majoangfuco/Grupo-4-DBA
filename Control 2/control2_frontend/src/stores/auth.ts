import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/services/api'

export interface Usuario {
  id: number
  username: string
  latitud: number
  longitud: number
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<Usuario | null>(JSON.parse(localStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!token.value && !isTokenExpired(token.value))

  function getTokenPayload(rawToken: string) {
    try {
      const payload = rawToken.split('.')[1]
      if (!payload) return null

      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
      return JSON.parse(atob(base64))
    } catch {
      return null
    }
  }

  function isTokenExpired(rawToken: string | null) {
    if (!rawToken) return true

    const payload = getTokenPayload(rawToken)
    if (!payload?.exp) return true

    return payload.exp * 1000 <= Date.now()
  }

  function clearSession() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function clearExpiredSession() {
    if (token.value && isTokenExpired(token.value)) {
      clearSession()
    }
  }

  async function login(username: string, password: string) {
    const response = await authApi.login(username, password)
    token.value = response.data.token
    localStorage.setItem('token', token.value!)

    await fetchCurrentUser()
  }

  async function fetchCurrentUser() {
    if (!token.value || isTokenExpired(token.value)) {
      clearSession()
      throw new Error('Sesión expirada')
    }

    const meResponse = await authApi.me()
    user.value = meResponse.data
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  async function register(
    username: string,
    password: string,
    latitud: number,
    longitud: number,
  ) {
    await authApi.register(username, password, latitud, longitud)
  }

  function logout() {
    clearSession()
  }

  function updateSession(newUser: Usuario, newToken: string) {
    token.value = newToken
    user.value = newUser
    localStorage.setItem('token', newToken)
    localStorage.setItem('user', JSON.stringify(newUser))
  }

  return {
    token,
    user,
    isAuthenticated,
    clearExpiredSession,
    fetchCurrentUser,
    login,
    register,
    logout,
    updateSession,
  }
})
