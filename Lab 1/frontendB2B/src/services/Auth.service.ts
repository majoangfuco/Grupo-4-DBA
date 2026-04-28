// ============================================================
// services/Auth.service.ts
// Servicio de autenticación — llama al backend con JWT.
//
// 🔧 BACKEND PENDIENTE — Endpoints que debes implementar:
//
//   POST  /usuario/login
//     Body:    { correo: string, contrasena: string }
//     Returns: string (el token JWT)
//
//   POST  /usuario/register
//     Body:    { nombre: string, correo: string, contrasena: string, rol: string }
//     Returns: string ("Usuario registrado exitosamente")
//
//   GET   /usuario/buscar?correo=xxx
//     Headers: Authorization: Bearer <token>
//     Returns: { id: number, nombre: string, correo: string, rol: string }
//
// ============================================================

import httpClient from '@/http-common'

// ─── Tipos ────────────────────────────────────────────────────
export interface LoginPayload {
  correo: string
  contrasena: string
}

export interface RegisterPayload {
  nombre: string
  correo: string
  contrasena: string
  rol?: string          // 'ADMIN' | 'CLIENTE' — según tu backend
}

export interface UsuarioDTO {
  id: number
  nombre: string
  correo: string
  rol: string
}

// ─── Llamadas al backend ──────────────────────────────────────

/**
 * 🔧 BACKEND: POST /usuario/login
 * El backend debe devolver el token JWT como string plano o como JSON.
 * Ajusta `response.data` según lo que retorne tu backend.
 */
const login = (correo: string, contrasena: string) => {
  return httpClient.post<string>('/usuario/login', { correo, contrasena })
}

/**
 * 🔧 BACKEND: POST /usuario/register
 * El backend debe crear el usuario y devolver un mensaje de confirmación.
 */
const register = (usuario: RegisterPayload) => {
  return httpClient.post<string>('/usuario/register', usuario)
}

/**
 * 🔧 BACKEND: GET /usuario/buscar?correo=xxx
 * El token ya va en el header automáticamente por el interceptor de http-common.
 * Devuelve los datos del usuario autenticado (id, nombre, rol, etc.)
 */
const getUserByEmail = (correo: string) => {
  return httpClient.get<UsuarioDTO>(`/usuario/buscar?correo=${correo}`)
}

export default { login, register, getUserByEmail }
