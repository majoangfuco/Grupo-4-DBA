<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router    = useRouter()
const route     = useRoute()
const authStore = useAuthStore()

// --- Nombre del usuario (viene del store de auth) ---
// 🔧 BACKEND: authStore.userName se llena en Login.vue después de
//             llamar a GET /usuario/buscar y recibir { nombre, ... }
const userName = computed(() => authStore.userName ?? 'Usuario')

// --- Menú de usuario ---
const menuOpen = ref(false)
const toggleMenu = () => { menuOpen.value = !menuOpen.value }
const closeMenu  = () => { menuOpen.value = false }

const handleLogout = () => {
  closeMenu()
  authStore.clearSession()   // limpia JWT y datos del usuario
  router.push('/login')
}

const handleProfile = () => {
  closeMenu()
  router.push('/perfil')
}


// --- Breadcrumbs ---
const routeLabels: Record<string, string> = {
  '/': 'Inicio',
  '/productos': 'Productos',
}

const routeParents: Record<string, string> = {
  // Ejemplo: '/productos/detalle': '/productos',
}

const breadcrumbs = computed(() => {
  const pathname = route.path

  if (routeLabels[pathname]) {
    if (pathname === '/') return [{ label: 'Inicio', path: '/' }]
    return [
      { label: 'Inicio', path: '/' },
      { label: routeLabels[pathname], path: pathname },
    ]
  }

  for (const [segment, parentPath] of Object.entries(routeParents)) {
    if (pathname.startsWith(segment)) {
      return [
        { label: 'Inicio', path: '/' },
        { label: routeLabels[parentPath] ?? parentPath, path: parentPath },
        { label: pathname, path: pathname },
      ]
    }
  }

  return [{ label: 'Inicio', path: '/' }]
})

const isHome = computed(() => route.path === '/')

// --- Ítems de navegación según rol ---
const navItems = computed(() => {
  const baseItems = [
    { label: 'Inicio', path: '/', icon: '🏠' },
  ]

  if (authStore.isAdmin) {
    return [
      ...baseItems,
      { label: 'Productos', path: '/productosAdmin', icon: '📦' },
      { label: 'Clientes', path: '/clientesAdmin', icon: '👥' },
    ]
  } else if (authStore.isCliente) {
    return [
      { label: 'Productos', path: '/productosCliente', icon: '📦' },
      { label: 'Órdenes', path: '/ordenesCliente', icon: '📋' },
    ]
  }

  return baseItems
})
</script>

<template>
  <div class="layout">

    <!-- ===== SIDEBAR ===== -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <img src="/icon.png" alt="Logo" width="100" height="80" />
      </div>

      <nav class="sidebar-nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <!-- ===== CONTENIDO PRINCIPAL ===== -->
    <div class="main-wrapper">

      <!-- Panel blanco con borde redondeado -->
      <div class="content-panel">

        <!-- APP BAR -->
        <header class="app-bar">
          <!-- Botón atrás (oculto en home) -->
          <button
            class="btn-back"
            :class="{ hidden: isHome }"
            @click="router.go(-1)"
            aria-label="Volver"
          >
            ←
          </button>

          <!-- Breadcrumbs -->
          <nav class="breadcrumbs" aria-label="breadcrumb">
            <span
              v-for="(crumb, index) in breadcrumbs"
              :key="crumb.path"
              class="breadcrumb-item"
            >
              <span
                class="crumb"
                :class="{ last: index === breadcrumbs.length - 1 }"
                @click="index < breadcrumbs.length - 1 && router.push(crumb.path)"
              >
                {{ crumb.label }}
              </span>
              <span v-if="index < breadcrumbs.length - 1" class="crumb-sep">›</span>
            </span>
          </nav>

          <!-- Usuario + menú -->
          <div class="user-menu-wrapper">
            <button class="btn-user" @click="toggleMenu">
              <span class="user-name">{{ userName }}</span>
              <span class="user-icon">👤</span>
            </button>

            <div v-if="menuOpen" class="dropdown-menu" @click.stop>
              <button class="dropdown-item" @click="handleProfile">Mi Perfil</button>
              <button class="dropdown-item" @click="handleLogout">Cerrar Sesión</button>
            </div>

            <!-- Overlay para cerrar menú al hacer clic afuera -->
            <div v-if="menuOpen" class="overlay" @click="closeMenu" />
          </div>
        </header>

        <!-- ÁREA DE CONTENIDO (equivalente a <Outlet />) -->
        <main class="page-content">
          <RouterView />
        </main>

      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== VARIABLES DE COLOR Y TAMAÑO ===== */
:root {
  --sidebar-width: 180px;
  --bg: #7ca4c4;
  --green-primary: #156895;
  --green-hover: #297ea6;
}

/* ===== DISEÑO BASE ===== */
.layout {
  display: flex;
  height: 100vh;
  background-color: #7ca4c4;
  font-family: 'Inter', 'Segoe UI', sans-serif;
}

/* ===== BARRA LATERAL ===== */
.sidebar {
  width: 180px;
  min-width: 100px;
  background-color: #7ca4c4;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
}

.logo-text {
  font-size: 1.5rem;
  font-weight: 800;
  color: #4E7D10;
  letter-spacing: 2px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 17px;
  text-decoration: none;
  color: #333;
  font-size: 0.9rem;
  font-weight: 500;
  transition: background-color 0.2s, filter 0.2s;
}

.nav-item:hover {
  background-color: rgba(78, 125, 16, 0.12);
}

.nav-item.active {
  background-color: #156895;
  color: white;
  filter: drop-shadow(0 0 8px #156895);
}

.nav-item.active .nav-icon {
  filter: brightness(10);
}

.nav-icon {
  font-size: 1.1rem;
}

/* ===== CONTENEDOR PRINCIPAL ===== */
.main-wrapper {
  flex: 1;
  padding: 12px;
  box-sizing: border-box;
  min-width: 0;
}

/* ===== PANEL DE CONTENIDO (fondo blanco) ===== */
.content-panel {
  width: 100%;
  height: 100%;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* ===== BARRA SUPERIOR ===== */
.app-bar {
  display: flex;
  align-items: center;
  padding: 0 16px;
  height: 56px;
  min-height: 56px;
  border-bottom: 1px solid #E0E0D8;
  background: #ffffff;
  gap: 8px;
}

.btn-back {
  background: none;
  border: none;
  font-size: 1.3rem;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  color: #333;
  transition: background 0.2s;
}

.btn-back:hover { background: #f0f0e8; }
.btn-back.hidden { visibility: hidden; }

/* ===== MIGAS DE PAN (ruta de navegación) ===== */
.breadcrumbs {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.crumb {
  font-size: 0.85rem;
  color: #888;
  cursor: pointer;
  transition: text-decoration 0.1s;
}

.crumb:hover:not(.last) { text-decoration: underline; }

.crumb.last {
  color: #156895;
  font-weight: 700;
  cursor: default;
}

.crumb-sep {
  color: #bbb;
  font-size: 0.85rem;
}

/* ===== MENÚ DE USUARIO ===== */
.user-menu-wrapper {
  position: relative;
}

.btn-user {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  color: #333;
  transition: background 0.2s;
}

.btn-user:hover { background: #f0f0e8; }

.user-icon { font-size: 1.3rem; }

.dropdown-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  min-width: 160px;
  z-index: 100;
  overflow: hidden;
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 12px 16px;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  font-size: 0.9rem;
  color: #333;
  transition: background 0.15s;
}

.dropdown-item:hover { background: #f5f5f0; }

.overlay {
  position: fixed;
  inset: 0;
  z-index: 99;
}

/* ===== ÁREA DE CONTENIDO DE PÁGINAS ===== */
.page-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
