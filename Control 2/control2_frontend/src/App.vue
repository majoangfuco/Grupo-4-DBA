<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { onMounted, ref } from 'vue'
import { tareasApi } from '@/services/api'

const auth = useAuthStore()
const router = useRouter()
const notificaciones = ref<any[]>([])
const showNotif = ref(false)

onMounted(async () => {
  if (auth.isAuthenticated) {
    try {
      const res = await tareasApi.getNotificaciones()
      notificaciones.value = res.data
      if (notificaciones.value.length > 0) showNotif.value = true
    } catch {
      // silently ignore
    }
  }
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div id="app">
    <nav v-if="auth.isAuthenticated" class="navbar">
      <div class="nav-brand">Gestión de Tareas</div>
      <div class="nav-links">
        <router-link to="/dashboard">Mis Tareas</router-link>
        <router-link to="/estadisticas">Estadísticas</router-link>
        <span class="nav-user">{{ auth.user?.username }}</span>
        <button @click="logout" class="btn-logout">Salir</button>
      </div>
    </nav>

    <div
      v-if="showNotif && notificaciones.length > 0"
      class="notif-banner"
      @click="showNotif = false"
    >
      ⚠️ Tienes {{ notificaciones.length }} tarea(s) con vencimiento en las próximas 24 horas.
      <span class="notif-close">✕</span>
    </div>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
body {
  font-family: 'Segoe UI', sans-serif;
  background: #f0f2f5;
  color: #333;
}
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #2c3e50;
  color: white;
  padding: 0.8rem 2rem;
}
.nav-brand {
  font-size: 1.2rem;
  font-weight: 600;
}
.nav-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}
.nav-links a {
  color: #ecf0f1;
  text-decoration: none;
  padding: 0.3rem 0.6rem;
  border-radius: 4px;
  transition: background 0.2s;
}
.nav-links a:hover,
.nav-links a.router-link-active {
  background: #34495e;
}
.nav-user {
  color: #bdc3c7;
  font-size: 0.9rem;
}
.btn-logout {
  background: #e74c3c;
  color: white;
  border: none;
  padding: 0.3rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
}
.notif-banner {
  background: #f39c12;
  color: white;
  padding: 0.7rem 2rem;
  display: flex;
  justify-content: space-between;
  cursor: pointer;
  font-weight: 500;
}
.notif-close {
  font-weight: bold;
}
.main-content {
  padding: 2rem;
  max-width: 1100px;
  margin: 0 auto;
}
</style>
