import { createRouter, createWebHistory } from 'vue-router'
import BarraLateral from '@/layouts/BarraLateral.vue'

// ─── Guard de autenticación ───────────────────────────────────
// Lee el token directamente de localStorage para que funcione
// incluso antes de que Pinia esté inicializado.
function estaAutenticado(): boolean {
  return !!localStorage.getItem('jwt')
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ── Rutas públicas ─────────────────────────────────────
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false },
    },

    // ── Rutas protegidas (requieren JWT) ───────────────────
    {
      path: '/',
      component: BarraLateral,   // Layout padre (sidebar + AppBar)
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'Pagina-Principal',
          // 🔧 PENDIENTE — Rol: ADMIN
          component: () => import('@/views/Admin/Reporte-PaginaAdmin.vue'),
        },
        {
          path: 'productosAdmin',
          name: 'Admin-Productos',
          // 🔧 PENDIENTE — Rol: ADMIN
          component: () => import('@/views/Admin/Productos-PaginaAdmin.vue'),
        },
        {
          path: 'clientesAdmin',
          name: 'Admin-Clientes',
          // 🔧 PENDIENTE — Rol: ADMIN
          component: () => import('@/views/Admin/Clientes-PaginaAdmin.vue'),
        },
        {
          path: 'productosCliente',
          name: 'Cliente-Productos',
          // 🔧 PENDIENTE — Rol: CLIENTE
          component: () => import('@/views/Customers/Productos-PaginaClientes.vue'),
        },
        {
          path: 'ordenesCliente',
          name: 'Cliente-Ordenes',
          // 🔧 PENDIENTE — Rol: CLIENTE
          component: () => import('@/views/Customers/Ordenes-PaginaClientes.vue'),
        },
      ],
    },

    // ── Catch-all: redirige al login ───────────────────────
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login',
    },
  ],
})

// ─── Guard global de navegación ──────────────────────────────
router.beforeEach((to, _from, next) => {
  const autenticado = estaAutenticado()

  if (to.meta.requiresAuth && !autenticado) {
    // Ruta protegida sin sesión → ir al login
    next('/login')
  } else if (to.path === '/login' && autenticado) {
    // Ya está logueado → no dejar entrar al login de nuevo
    next('/')
  } else {
    next()
  }

  // ════════════════════════════════════════════════════════════
  // 🔧 BACKEND PENDIENTE — Guard basado en ROL
  //
  // Cuando tengas roles definidos en el token / store, puedes
  // agregar aquí una verificación adicional por rol. Ejemplo:
  //
  //   import { useAuthStore } from '@/stores/auth'
  //   const authStore = useAuthStore()
  //
  //   if (to.meta.requiresRole === 'ADMIN' && !authStore.isAdmin) {
  //     next('/')         // redirige si no tiene el rol correcto
  //     return
  //   }
  // ════════════════════════════════════════════════════════════
})

export default router
