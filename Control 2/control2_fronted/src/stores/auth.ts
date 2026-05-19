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

  const isAuthenticated = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const response = await authApi.login(username, password)
    token.value = response.data.token
    localStorage.setItem('token', token.value!)

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
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isAuthenticated, login, register, logout }
})
