<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const latitud = ref<number | null>(null)
const longitud = ref<number | null>(null)
const error = ref('')
const loading = ref(false)

function useCurrentLocation() {
  if (!navigator.geolocation) {
    error.value = 'Tu navegador no soporta geolocalización'
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      latitud.value = pos.coords.latitude
      longitud.value = pos.coords.longitude
    },
    () => {
      error.value = 'No se pudo obtener la ubicación'
    },
  )
}

async function handleRegister() {
  error.value = ''
  if (!latitud.value || !longitud.value) {
    error.value = 'Debes ingresar o detectar tu ubicación'
    return
  }
  loading.value = true
  try {
    await auth.register(username.value, password.value, latitud.value, longitud.value)
    await auth.login(username.value, password.value)
    router.push('/dashboard')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Error al registrar usuario'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-container">
    <div class="auth-card">
      <h2>Crear Cuenta</h2>
      <p class="subtitle">Gestión de Tareas Urbanas</p>

      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label>Usuario</label>
          <input v-model="username" type="text" placeholder="Nombre de usuario" required />
        </div>
        <div class="form-group">
          <label>Contraseña</label>
          <input v-model="password" type="password" placeholder="Contraseña" required />
        </div>

        <div class="form-group">
          <label>Ubicación Geográfica</label>
          <button type="button" class="btn-geo" @click="useCurrentLocation">
            📍 Usar mi ubicación actual
          </button>
          <div class="coords-row">
            <input
              v-model.number="latitud"
              type="number"
              step="any"
              placeholder="Latitud (ej: -33.45)"
              required
            />
            <input
              v-model.number="longitud"
              type="number"
              step="any"
              placeholder="Longitud (ej: -70.65)"
              required
            />
          </div>
          <p v-if="latitud && longitud" class="coords-info">
            📌 {{ latitud?.toFixed(6) }}, {{ longitud?.toFixed(6) }}
          </p>
        </div>

        <p v-if="error" class="error-msg">{{ error }}</p>

        <button type="submit" :disabled="loading" class="btn-primary">
          {{ loading ? 'Registrando...' : 'Registrarse' }}
        </button>
      </form>

      <p class="auth-switch">
        ¿Ya tienes cuenta?
        <router-link to="/login">Inicia sesión</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
}
.auth-card {
  background: white;
  padding: 2.5rem;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 420px;
}
h2 {
  margin-bottom: 0.3rem;
  color: #2c3e50;
}
.subtitle {
  color: #7f8c8d;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
}
.form-group {
  margin-bottom: 1rem;
}
label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  margin-bottom: 0.3rem;
  color: #555;
}
input[type='text'],
input[type='password'],
input[type='number'] {
  width: 100%;
  padding: 0.6rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.2s;
}
input:focus {
  border-color: #3498db;
}
.btn-geo {
  width: 100%;
  background: #27ae60;
  color: white;
  border: none;
  padding: 0.5rem;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}
.coords-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}
.coords-info {
  font-size: 0.8rem;
  color: #27ae60;
  margin-top: 0.3rem;
}
.btn-primary {
  width: 100%;
  background: #2c3e50;
  color: white;
  border: none;
  padding: 0.75rem;
  border-radius: 6px;
  font-size: 1rem;
  cursor: pointer;
  margin-top: 0.5rem;
  transition: background 0.2s;
}
.btn-primary:hover:not(:disabled) {
  background: #34495e;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error-msg {
  color: #e74c3c;
  font-size: 0.85rem;
  margin-bottom: 0.5rem;
}
.auth-switch {
  text-align: center;
  margin-top: 1.2rem;
  font-size: 0.9rem;
  color: #666;
}
.auth-switch a {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}
</style>
