<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { tareasApi } from '@/services/api'
import { Icon } from '@iconify/vue'

const auth = useAuthStore()
const router = useRouter()
const notificaciones = ref<any[]>([])
const showNotif = ref(false)
const showUserMenu = ref(false)
let pollInterval: any = null

async function checkNotificaciones() {
  if (!auth.isAuthenticated) {
    notificaciones.value = []
    showNotif.value = false
    return
  }
  try {
    const res = await tareasApi.getNotificaciones()
    const previousLength = notificaciones.value.length
    notificaciones.value = res.data
    if (notificaciones.value.length > 0) {
      if (notificaciones.value.length > previousLength || previousLength === 0) {
        showNotif.value = true
      }
    } else {
      showNotif.value = false
    }
  } catch {
    // silently ignore
  }
}

function startPolling() {
  stopPolling()
  checkNotificaciones()
  pollInterval = setInterval(checkNotificaciones, 60000)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

watch(
  () => auth.isAuthenticated,
  (isAuth) => {
    if (isAuth) {
      startPolling()
    } else {
      stopPolling()
      notificaciones.value = []
      showNotif.value = false
    }
  }
)

onMounted(() => {
  if (auth.isAuthenticated) {
    startPolling()
  }
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('click', handleClickOutside)
})

function handleClickOutside(event: MouseEvent) {
  const menu = document.getElementById('user-menu-wrapper')
  if (menu && !menu.contains(event.target as Node)) {
    showUserMenu.value = false
  }
}

function logout() {
  showUserMenu.value = false
  auth.logout()
  router.push('/login')
}

function goToPerfil() {
  showUserMenu.value = false
  router.push('/perfil')
}
</script>

<template>
  <div id="app">
    <nav v-if="auth.isAuthenticated" class="navbar">
      <div class="nav-brand"><Icon icon="lucide:check-square" class="icon" /> Gestión de Tareas</div>
      <div class="nav-links">
        <router-link to="/dashboard"><Icon icon="lucide:list-todo" class="icon" /> Mis Tareas</router-link>
        <router-link to="/sectores"><Icon icon="lucide:map" class="icon" /> Sectores</router-link>
        <router-link to="/estadisticas"><Icon icon="lucide:pie-chart" class="icon" /> Estadísticas</router-link>

        <!-- Dropdown de usuario -->
        <div id="user-menu-wrapper" class="user-menu-wrapper">
          <button
            class="user-menu-trigger"
            @click.stop="showUserMenu = !showUserMenu"
            :class="{ active: showUserMenu }"
            aria-haspopup="true"
            :aria-expanded="showUserMenu"
          >
            <span class="user-avatar">{{ auth.user?.username?.charAt(0).toUpperCase() }}</span>
            <span class="user-name">{{ auth.user?.username }}</span>
            <span class="chevron" :class="{ open: showUserMenu }">▾</span>
          </button>

          <transition name="dropdown">
            <div v-if="showUserMenu" class="user-dropdown" role="menu">
              <div class="dropdown-header">
                <span class="dropdown-avatar">{{ auth.user?.username?.charAt(0).toUpperCase() }}</span>
                <span class="dropdown-username">{{ auth.user?.username }}</span>
              </div>
              <hr class="dropdown-divider" />
              <button class="dropdown-item" @click="goToPerfil" role="menuitem">
                <Icon icon="lucide:user" class="icon" /> Perfil
              </button>
              <button class="dropdown-item dropdown-item--danger" @click="logout" role="menuitem">
                <Icon icon="lucide:log-out" class="icon" /> Salir
              </button>
            </div>
          </transition>
        </div>
      </div>
    </nav>

    <div
      v-if="showNotif && notificaciones.length > 0"
      class="notif-banner"
      @click="showNotif = false"
    >
      <Icon icon="lucide:triangle-alert" class="icon" /> Tienes {{ notificaciones.length }} tarea(s) con vencimiento en las próximas 24 horas.
      <span class="notif-close"><Icon icon="lucide:x" class="icon" /></span>
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
  position: relative;
  z-index: 100;
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

/* ===== USER MENU DROPDOWN ===== */
.user-menu-wrapper {
  position: relative;
}

.user-menu-trigger {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #ecf0f1;
  padding: 0.35rem 0.75rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 500;
  transition: background 0.2s, border-color 0.2s;
  white-space: nowrap;
}

.user-menu-trigger:hover,
.user-menu-trigger.active {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.3);
}

.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3498db, #2980b9);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
  font-weight: 700;
  color: white;
  flex-shrink: 0;
}

.chevron {
  font-size: 0.75rem;
  opacity: 0.7;
  transition: transform 0.2s ease;
  display: inline-block;
}

.chevron.open {
  transform: rotate(180deg);
}

/* Dropdown panel */
.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: white;
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.18);
  min-width: 190px;
  overflow: hidden;
  z-index: 200;
  border: 1px solid #e8ecef;
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.85rem 1rem;
  background: #f8f9fa;
}

.dropdown-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3498db, #2980b9);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 700;
  color: white;
  flex-shrink: 0;
}

.dropdown-username {
  font-size: 0.88rem;
  font-weight: 600;
  color: #2c3e50;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-divider {
  border: none;
  border-top: 1px solid #e8ecef;
  margin: 0;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.7rem 1rem;
  background: none;
  border: none;
  color: #2c3e50;
  font-size: 0.88rem;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.dropdown-item:hover {
  background: #f0f4f8;
}

.dropdown-item--danger {
  color: #e74c3c;
}

.dropdown-item--danger:hover {
  background: #fdf2f2;
}

.item-icon {
  font-size: 1rem;
  width: 20px;
  text-align: center;
}

/* Animación del dropdown */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* Notificaciones */
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
.icon {
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
  flex-shrink: 0;
  display: inline-block;
}
.main-content {
  padding: 2rem;
  max-width: 1100px;
  margin: 0 auto;
}
</style>
