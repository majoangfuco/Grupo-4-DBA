<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    router.push('/dashboard')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Credenciales incorrectas'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-container">
    <div class="auth-card">
      <h2>Iniciar Sesión</h2>
      <p class="subtitle">Gestión de Tareas Urbanas</p>

      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>Usuario</label>
          <input v-model="username" type="text" placeholder="Nombre de usuario" required />
        </div>
        <div class="form-group">
          <label>Contraseña</label>
          <input v-model="password" type="password" placeholder="Contraseña" required />
        </div>

        <p v-if="error" class="error-msg">{{ error }}</p>

        <button type="submit" :disabled="loading" class="btn-primary">
          {{ loading ? 'Ingresando...' : 'Ingresar' }}
        </button>
      </form>

      <p class="auth-switch">
        ¿No tienes cuenta?
        <router-link to="/register">Regístrate aquí</router-link>
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
  max-width: 400px;
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
input {
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
