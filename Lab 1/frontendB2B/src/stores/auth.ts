// ============================================================
// stores/auth.ts
// Store Pinia para el estado global de autenticación.
//
// Guarda en localStorage para persistir la sesión al recargar.
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  // ─── Estado ─────────────────────────────────────────────────
  const token    = ref<string | null>(localStorage.getItem('jwt'))
  const userId   = ref<string | null>(localStorage.getItem('userId'))
  const userEmail= ref<string | null>(localStorage.getItem('userEmail'))
  const userName = ref<string | null>(localStorage.getItem('userName'))
  const userRole = ref<string | null>(localStorage.getItem('userRole'))
  const userRut  = ref<string | null>(localStorage.getItem('userRut'))

  // ─── Getters ────────────────────────────────────────────────
  const isAuthenticated = computed(() => !!token.value)
  const isAdmin         = computed(() => userRole.value === 'ADMIN')
  const isCliente       = computed(() => userRole.value === 'CLIENTE')

  // ─── Acciones ───────────────────────────────────────────────

  /** Llamado después de un login exitoso */
  function setSession(payload: {
    token: string
    userId: string
    userEmail: string
    userName: string
    userRole: string
    userRut: string
  }) {
    token.value     = payload.token
    userId.value    = payload.userId
    userEmail.value = payload.userEmail
    userName.value  = payload.userName
    userRole.value  = payload.userRole
    userRut.value   = payload.userRut

    // Persistir en localStorage
    localStorage.setItem('jwt',       payload.token)
    localStorage.setItem('userId',    payload.userId)
    localStorage.setItem('userEmail', payload.userEmail)
    localStorage.setItem('userName',  payload.userName)
    localStorage.setItem('userRole',  payload.userRole)
    localStorage.setItem('userRut',   payload.userRut)
  }

  /** Limpia la sesión y redirige al login */
  function clearSession() {
    token.value     = null
    userId.value    = null
    userEmail.value = null
    userName.value  = null
    userRole.value  = null
    userRut.value   = null

    localStorage.removeItem('jwt')
    localStorage.removeItem('userId')
    localStorage.removeItem('userEmail')
    localStorage.removeItem('userName')
    localStorage.removeItem('userRole')
    localStorage.removeItem('userRut')
  }

  return {
    token,
    userId,
    userEmail,
    userName,
    userRole,
    userRut,
    isAuthenticated,
    isAdmin,
    isCliente,
    setSession,
    clearSession,
  }
})
