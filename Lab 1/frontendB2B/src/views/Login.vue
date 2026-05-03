<script setup lang="ts">
defineOptions({ name: 'LoginView' })

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthService from '@/services/Auth.service'

const router    = useRouter()
const authStore = useAuthStore()

// ─── Estado del formulario ───────────────────────────────────
const correo     = ref('')
const contrasena = ref('')
const loading    = ref(false)
const errorMsg   = ref('')
const showPass   = ref(false)

// ─── Acción de login ─────────────────────────────────────────
async function handleLogin() {
  errorMsg.value = ''
  loading.value  = true

  try {
    const loginResponse = await AuthService.login(correo.value, contrasena.value)
    const token = loginResponse.data.token

    if (!token) {
      throw new Error('Token JWT no recibido desde el backend')
    }

    // Guardar el token en localStorage antes de llamar a /buscar
    // para que el interceptor de Axios (http-common) lo envíe en la cabecera
    localStorage.setItem('jwt', token)

    const userResponse = await AuthService.getUserByEmail(correo.value)
    const usuario = userResponse.data

    authStore.setSession({
      token,
      userId:    String(usuario.usuario_id || usuario.id),
      userEmail: usuario.correo,
      userName:  usuario.nombre,
      userRole:  usuario.rol,
      userRut:   usuario.rut_empresa || 'No disponible',
    })

    if (authStore.isAdmin) {
      router.push('/productosAdmin')
    } else if (authStore.isCliente) {
      router.push('/productosCliente')
    } else {
      router.push('/login')
    }

  } catch (error: unknown) {
    const axiosError = error as { response?: { status: number; data?: { error?: string } }; code?: string }

    // Si hubo un error en medio del proceso (ej. falló getUserByEmail), limpiamos el token parcial
    localStorage.removeItem('jwt')

    if (axiosError.response?.status === 401) {
      // Backend respondió: credenciales incorrectas
      errorMsg.value = axiosError.response.data?.error || 'Correo o contraseña incorrectos.'
    } else if (!axiosError.response || axiosError.code === 'ERR_NETWORK') {
      errorMsg.value = 'No se pudo conectar con el servidor. Verifica tu conexión.'
    } else {
      errorMsg.value = axiosError.response?.data?.error || 'Error inesperado. Intenta de nuevo.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- Panel izquierdo: branding -->
    <div class="branding-panel">
      <div class="branding-content">
        <div class="brand-logo">B2B</div>
        <h1 class="brand-title">Plataforma B2B</h1>
        <p class="brand-subtitle">
          Gestiona clientes, productos y órdenes desde un solo lugar.
        </p>
        <ul class="brand-features">
          <li>
            <span class="feat-icon">📦</span>
            <span>Control de inventario en tiempo real</span>
          </li>
          <li>
            <span class="feat-icon">👥</span>
            <span>Gestión de clientes y pedidos</span>
          </li>
          <li>
            <span class="feat-icon">📊</span>
            <span>Reportes y métricas de negocio</span>
          </li>
        </ul>
      </div>
      <!-- Decoración de fondo -->
      <div class="blob blob-1" />
      <div class="blob blob-2" />
    </div>

    <!-- Panel derecho: formulario -->
    <div class="form-panel">
      <div class="form-card">
        <!-- Cabecera -->
        <div class="form-header">
          <div class="form-logo">B2B</div>
          <h2 class="form-title">Bienvenido de vuelta</h2>
          <p class="form-subtitle">Inicia sesión para continuar</p>
        </div>

        <!-- Formulario -->
        <form class="login-form" @submit.prevent="handleLogin" novalidate>


          <!-- Correo -->
          <div class="field-group">
            <label for="login-email" class="field-label">Correo electrónico</label>
            <div class="input-wrapper">
              <span class="input-icon">✉️</span>
              <input
                id="login-email"
                v-model="correo"
                type="email"
                class="field-input"
                placeholder="ejemplo@empresa.com"
                autocomplete="email"
                required
              />
            </div>
          </div>

          <!-- Contraseña -->
          <div class="field-group">
            <label for="login-password" class="field-label">Contraseña</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                id="login-password"
                v-model="contrasena"
                :type="showPass ? 'text' : 'password'"
                class="field-input"
                placeholder="••••••••"
                autocomplete="current-password"
                required
              />
              <button
                type="button"
                class="toggle-pass"
                :aria-label="showPass ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                @click="showPass = !showPass"
              >
                {{ showPass ? '🙈' : '👁️' }}
              </button>
            </div>
          </div>

          <!-- Error -->
          <Transition name="fade">
            <div v-if="errorMsg" class="error-banner" role="alert">
              ⚠️ {{ errorMsg }}
            </div>
          </Transition>

          <!-- Botón submit -->
          <button
            id="btn-login"
            type="submit"
            class="btn-login"
            :disabled="loading"
          >
            <span v-if="loading" class="spinner" />
            <span>{{ loading ? 'Verificando...' : 'Iniciar sesión' }}</span>
          </button>
        </form>

        <!-- Footer del formulario -->
        <p class="form-footer">
          <RouterLink to="/register" class="register-link">¿No tienes cuenta? Regístrate aquí.</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== PÁGINA COMPLETA ===== */
.login-page {
  display: flex;
  height: 100vh;
  width: 100vw;
  font-family: 'Inter', 'Segoe UI', sans-serif;
  overflow: hidden;
}

/* ===== PANEL IZQUIERDO — BRANDING ===== */
.branding-panel {
  flex: 1;
  position: relative;
  background: linear-gradient(135deg, #156895 0%, #0d4a6b 60%, #092e43 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 48px;
}

.branding-content {
  position: relative;
  z-index: 1;
  color: white;
  max-width: 420px;
}

.brand-logo {
  font-size: 2.4rem;
  font-weight: 900;
  letter-spacing: 4px;
  color: #a8d8f0;
  margin-bottom: 20px;
}

.brand-title {
  font-size: 2.2rem;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 16px;
}

.brand-subtitle {
  font-size: 1.05rem;
  color: rgba(255, 255, 255, 0.75);
  line-height: 1.6;
  margin-bottom: 40px;
}

.brand-features {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.85);
}

.feat-icon {
  font-size: 1.3rem;
  background: rgba(255, 255, 255, 0.12);
  padding: 8px;
  border-radius: 10px;
}

/* Blobs decorativos */
.blob {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
  filter: blur(60px);
}

.blob-1 {
  width: 400px;
  height: 400px;
  background: #5ab7e0;
  top: -100px;
  right: -100px;
}

.blob-2 {
  width: 300px;
  height: 300px;
  background: #a8d8f0;
  bottom: -80px;
  left: -60px;
}

/* ===== PANEL DERECHO — FORMULARIO ===== */
.form-panel {
  width: 480px;
  min-width: 380px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
}

.form-card {
  width: 100%;
  max-width: 400px;
  background: white;
  border-radius: 20px;
  padding: 40px 36px;
  box-shadow: 0 8px 40px rgba(21, 104, 149, 0.12);
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ─── Cabecera ──────────────────────── */
.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.form-logo {
  display: inline-block;
  font-size: 1.4rem;
  font-weight: 900;
  letter-spacing: 3px;
  color: white;
  background: linear-gradient(135deg, #156895, #0d4a6b);
  padding: 10px 18px;
  border-radius: 12px;
  margin-bottom: 18px;
}

.form-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a2a3a;
  margin-bottom: 6px;
}

.form-subtitle {
  font-size: 0.9rem;
  color: #7a8fa6;
}

/* ─── Campos ──────────────────────────── */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: #3d5268;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  font-size: 1rem;
  pointer-events: none;
  user-select: none;
}

.field-input {
  width: 100%;
  padding: 12px 44px 12px 42px;
  border: 1.5px solid #d0dce8;
  border-radius: 10px;
  font-size: 0.95rem;
  color: #1a2a3a;
  background: #f8fafd;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.field-input:focus {
  border-color: #156895;
  box-shadow: 0 0 0 3px rgba(21, 104, 149, 0.12);
  background: white;
}

.toggle-pass {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
}

.toggle-pass:hover {
  background: #eef4f9;
}

/* ─── Banner de error ─────────────────── */
.error-banner {
  background: #fef2f2;
  border: 1px solid #fca5a5;
  color: #b91c1c;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 0.875rem;
  font-weight: 500;
}

/* ─── Botón principal ─────────────────── */
.btn-login {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #156895 0%, #0d4a6b 100%);
  color: white;
  font-size: 1rem;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s, box-shadow 0.2s;
  box-shadow: 0 4px 16px rgba(21, 104, 149, 0.35);
  margin-top: 4px;
}

.btn-login:hover:not(:disabled) {
  opacity: 0.92;
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(21, 104, 149, 0.45);
}

.btn-login:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

/* ─── Spinner ─────────────────────────── */
.spinner {
  width: 18px;
  height: 18px;
  border: 2.5px solid rgba(255, 255, 255, 0.4);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ─── Footer del form ─────────────────── */
.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 0.82rem;
  color: #9dafc2;
}

/* ─── Transición fade para el error ───── */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== RESPONSIVE ===== */
@media (max-width: 768px) {
  .branding-panel { display: none; }
  .form-panel {
    width: 100%;
    min-width: unset;
    background: linear-gradient(135deg, #156895 0%, #0d4a6b 100%);
  }
  .form-card {
    box-shadow: none;
    background: white;
  }
}
</style>
