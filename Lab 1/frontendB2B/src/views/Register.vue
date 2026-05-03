<script setup lang="ts">
defineOptions({ name: 'RegisterView' })

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthService from '@/services/Auth.service'

const router = useRouter()
const authStore = useAuthStore()

const nombre = ref('')
const correo = ref('')
const contrasena = ref('')
const rutEmpresa = ref('')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const showPass = ref(false)

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''
  loading.value = true

  try {
    await AuthService.register({
      nombre: nombre.value,
      correo: correo.value,
      contrasena: contrasena.value,
      rut_empresa: rutEmpresa.value,
      rol: 'CLIENTE',
    })

    successMsg.value = 'Registro exitoso. Iniciando sesión...'

    const loginResponse = await AuthService.login(correo.value, contrasena.value)
    const token = loginResponse.data.token

    if (!token) {
      throw new Error('Token JWT no recibido después del registro.')
    }

    // 🔧 IMPORTANTE: Guardar el token en localStorage antes de llamar a /buscar
    localStorage.setItem('jwt', token)

    const userResponse = await AuthService.getUserByEmail(correo.value)
    const usuario = userResponse.data

    authStore.setSession({
      token,
      userId: String(usuario.id),
      userEmail: usuario.correo,
      userName: usuario.nombre,
      userRole: usuario.rol,
      userRut: usuario.rut_empresa || 'No disponible',
    })

    router.push('/productosCliente')
  } catch (error: unknown) {
    const axiosError = error as { response?: { status: number; data?: { error?: string } }; code?: string }

    if (axiosError.response?.status === 409) {
      errorMsg.value = axiosError.response.data?.error || 'El correo ya está registrado.'
    } else if (axiosError.response?.status === 400) {
      errorMsg.value = axiosError.response.data?.error || 'Datos incompletos o incorrectos.'
    } else if (!axiosError.response || axiosError.code === 'ERR_NETWORK') {
      errorMsg.value = 'No se pudo conectar al backend. Revisa que el servidor esté activo.'
    } else {
      errorMsg.value = axiosError.response?.data?.error || 'Error inesperado. Intenta de nuevo.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-card">
      <h2>Registro de cliente</h2>
      <p>Si no tienes cuenta, regístrate como cliente.</p>

      <form class="register-form" @submit.prevent="handleRegister">
        <label>
          Nombre completo
          <input v-model="nombre" type="text" placeholder="Nombre" required />
        </label>

        <label>
          Correo electrónico
          <input v-model="correo" type="email" placeholder="ejemplo@empresa.com" required />
        </label>

        <label>
          Contraseña
          <div class="password-field">
            <input
              v-model="contrasena"
              :type="showPass ? 'text' : 'password'"
              placeholder="••••••••"
              required
            />
            <button type="button" @click="showPass = !showPass">
              {{ showPass ? 'Ocultar' : 'Mostrar' }}
            </button>
          </div>
        </label>

        <label>
          RUT empresa
          <input v-model="rutEmpresa" type="text" placeholder="12.345.678-9" required />
        </label>

        <Transition name="fade">
          <div v-if="errorMsg" class="alert error">⚠️ {{ errorMsg }}</div>
        </Transition>
        <Transition name="fade">
          <div v-if="successMsg" class="alert success">✅ {{ successMsg }}</div>
        </Transition>

        <button type="submit" class="btn-submit" :disabled="loading">
          {{ loading ? 'Registrando...' : 'Crear cuenta' }}
        </button>
      </form>

      <p class="form-footer">
        <RouterLink to="/login">Ya tengo cuenta</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4f7fb;
  padding: 32px;
}
.register-card {
  width: 100%;
  max-width: 420px;
  background: white;
  padding: 32px;
  border-radius: 18px;
  box-shadow: 0 18px 48px rgba(34, 61, 80, 0.12);
}
.register-card h2 {
  margin: 0 0 12px;
  color: #243b55;
}
.register-card p {
  margin: 0 0 24px;
  color: #55687d;
}
.register-form label {
  display: block;
  margin-bottom: 16px;
  color: #34495e;
  font-weight: 500;
}
.register-form input {
  width: 100%;
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid #d6e2ed;
  border-radius: 12px;
  font-size: 0.98rem;
}
.password-field {
  display: flex;
  gap: 10px;
}
.password-field button {
  border: none;
  background: #e8eef4;
  color: #33475b;
  border-radius: 12px;
  padding: 0 12px;
  cursor: pointer;
}
.alert {
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
  font-size: 0.95rem;
}
.alert.error {
  background: #ffe7e5;
  color: #8f1d15;
}
.alert.success {
  background: #e6f7ea;
  color: #1e5a25;
}
.btn-submit {
  width: 100%;
  padding: 14px 18px;
  background: #2b6cb0;
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
}
.btn-submit:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.form-footer {
  margin-top: 18px;
  text-align: center;
  color: #516f8c;
}
.form-footer a {
  color: #2b6cb0;
  text-decoration: none;
  font-weight: 600;
}
</style>
