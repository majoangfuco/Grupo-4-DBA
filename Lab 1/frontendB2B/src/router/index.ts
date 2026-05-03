import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
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
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
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
          component: () => import('@/views/Admin/Reporte-PaginaAdmin.vue'),
          meta: { requiresRole: 'ADMIN' },
        },
        {
          path: 'productosAdmin',
          name: 'Admin-Productos',
          component: () => import('@/views/Admin/Productos-PaginaAdmin.vue'),
          meta: { requiresRole: 'ADMIN' },
        },
        {
          path: 'clientesAdmin',
          name: 'Admin-Clientes',
          component: () => import('@/views/Admin/Clientes-PaginaAdmin.vue'),
          meta: { requiresRole: 'ADMIN' },
        },
        {
          path: 'ordenesAdmin',
          name: 'Admin-Ordenes',
          component: () => import('@/views/Admin/Ordenes-PaginaAdmin.vue'),
          meta: { requiresRole: 'ADMIN' },
        },
        {
          path: 'productosCliente',
          name: 'Cliente-Productos',
          component: () => import('@/views/Customers/Productos-PaginaClientes.vue'),
          meta: { requiresRole: 'CLIENTE' },
        },
        {
          path: 'ordenesCliente',
          name: 'Cliente-Ordenes',
          component: () => import('@/views/Customers/Ordenes-PaginaClientes.vue'),
          meta: { requiresRole: 'CLIENTE' },
        },
        {
          path: 'carritoCliente',
          name: 'Cliente-Carrito',
          component: () => import('@/views/Customers/Carrito-PaginaClientes.vue'),
          meta: { requiresRole: 'CLIENTE' },
        },
        {
          path: 'perfil',
          name: 'Perfil',
          component: () => import('@/views/PerfilPagina.vue'),
          meta: { requiresAuth: true },
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
router.beforeEach((to) => {
  const autenticado = estaAutenticado()
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !autenticado) {
    return '/login'
  }

  if ((to.path === '/login' || to.path === '/register') && autenticado) {
    return '/'
  }

  // ─── Verificación de rol ─────────────────────────────────────
  if (to.meta.requiresRole && authStore.userRole !== to.meta.requiresRole) {
    if (authStore.isAdmin) {
      return '/productosAdmin'
    }
    if (authStore.isCliente) {
      return '/productosCliente'
    }
    return '/login'
  }

  return true
})

export default router
