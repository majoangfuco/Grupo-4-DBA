<script setup lang="ts">
// =====================================================
// Productos-PaginaClientes.vue
// Página de visualización de productos para clientes.
// =====================================================

import { ref, reactive, computed, onMounted } from 'vue'
import ListaProductos from '@/components/productos/ListaProductos-VistaCliente.vue'
import { productoServicio } from '@/services/productoServicio'
import { categoriaServicio, type CategoriaEntidad } from '@/services/categoriaServicio'
import { carritoServicio } from '@/services/carritoServicio'
import { carritoProductoServicio } from '@/services/carritoProductoServicio'
import { useAuthStore } from '@/stores/auth'

// ===================== TIPOS ========================
interface Producto {
  producto_ID: number
  categoria_ID: number
  nombre_producto: string
  descripcion: string
  precio: number
  stock: number
  sku: string
}

// ==================== ESTADO ========================
const productos  = ref<Producto[]>([])
const cargando   = ref(true)
const error      = ref<string | null>(null)
const categorias = ref<CategoriaEntidad[]>([])
const authStore = useAuthStore()
const carritoId = ref<number | null>(null)
const toastMensaje = ref<string | null>(null)
const toastTipo = ref<'ok' | 'error'>('ok')
let toastTimer: number | null = null
const modalAbierto = ref(false)
const modalProducto = ref<Producto | null>(null)
const modalCantidad = ref(1)

// ==================== FILTROS =======================
const filtros = reactive({ producto_ID: '', nombre_producto: '', categoria_ID: '' })

// Categorías disponibles (se calculan desde los datos)
const opcionesCategorias = computed(() =>
  categorias.value.map(c => ({
    id: c.categoria_ID,
    nombre: c.nombre_Categoria,
  }))
)

const categoriasMap = computed(() => {
  const map = new Map<number, string>()
  categorias.value.forEach(c => map.set(c.categoria_ID, c.nombre_Categoria))
  return map
})

const limpiarFiltros = () => {
  filtros.producto_ID = ''
  filtros.nombre_producto     = ''
  filtros.categoria_ID  = ''
  paginaActual.value = 1
}

// =================== ORDENAMIENTO ==================
const configOrden = reactive<{ clave: string; direccion: 'asc' | 'desc' }>({
  clave: 'nombre_producto', direccion: 'asc',
})

const cambiarOrden = (clave: string) => {
  if (configOrden.clave === clave) {
    configOrden.direccion = configOrden.direccion === 'asc' ? 'desc' : 'asc'
  } else {
    configOrden.clave     = clave
    configOrden.direccion = 'asc'
  }
}

// ==================== FILTRADO =====================
const productosFiltrados = computed(() =>
  productos.value.filter(p => {
    const coincideNombre    = !filtros.nombre_producto     || p.nombre_producto.toLowerCase().includes(filtros.nombre_producto.toLowerCase())
    const coincideCategoria = !filtros.categoria_ID  || String(p.categoria_ID).includes(filtros.categoria_ID)
    const coincideId        = !filtros.producto_ID || String(p.producto_ID).includes(filtros.producto_ID)
    return coincideNombre && coincideCategoria && coincideId
  })
)

// ================== ORDENAMIENTO ==================
const productosOrdenados = computed(() =>
  [...productosFiltrados.value].sort((a, b) => {
    const clave = configOrden.clave as keyof Producto
    const valA  = String(a[clave] ?? '').toLowerCase()
    const valB  = String(b[clave] ?? '').toLowerCase()
    if (valA < valB) return configOrden.direccion === 'asc' ? -1 : 1
    if (valA > valB) return configOrden.direccion === 'asc' ?  1 : -1
    return 0
  })
)

// =================== PAGINACIÓN ====================
const paginaActual        = ref(1)
const elementosPorPagina  = ref(10)

const totalPaginas = computed(() =>
  Math.max(1, Math.ceil(productosOrdenados.value.length / elementosPorPagina.value))
)

const productosPaginaActual = computed(() => {
  const inicio = (paginaActual.value - 1) * elementosPorPagina.value
  return productosOrdenados.value.slice(inicio, inicio + elementosPorPagina.value)
})

const cambiarPagina = (pagina: number) => {
  if (pagina >= 1 && pagina <= totalPaginas.value) paginaActual.value = pagina
}

// ================ CARGA DE DATOS ===================
const cargarProductos = async () => {
  cargando.value = true
  error.value    = null
  try {
    const [productosResp, categoriasResp] = await Promise.all([
      productoServicio.obtenerTodos(),
      categoriaServicio.listar(true),
    ])
    productos.value = productosResp.data
    categorias.value = categoriasResp.data
  } catch (err: unknown) {
    console.error('Error al obtener productos:', err)
    productos.value = []
    // ====================================================
    // 🔌 BACKEND — Manejo de error de la API
    // ====================================================
    const axiosErr = err as { response?: { data?: { message?: string } } }
    error.value = axiosErr.response?.data?.message ?? 'Error al cargar los productos.'
  } finally {
    cargando.value = false
  }
}

// Carga inicial
onMounted(cargarProductos)

const obtenerCarritoId = async () => {
  if (carritoId.value) return carritoId.value
  const userId = Number(authStore.userId)
  if (!userId || Number.isNaN(userId)) {
    throw new Error('Usuario no valido')
  }
  const respuesta = await carritoServicio.obtenerOCrearPorCliente(userId)
  carritoId.value = respuesta.data.carrito_ID
  return carritoId.value
}

const agregarAlCarrito = async (producto: Producto, cantidad: number) => {
  try {
    const idCarrito = await obtenerCarritoId()
    await carritoProductoServicio.agregarProducto({
      carritoId: idCarrito,
      productoId: producto.producto_ID,
      cantidad,
    })
    toastTipo.value = 'ok'
    toastMensaje.value = `Agregado: ${cantidad} x ${producto.nombre_producto}`
  } catch (err: unknown) {
    console.error('Error al agregar al carrito:', err)
    toastTipo.value = 'error'
    toastMensaje.value = 'No se pudo agregar al carrito'
  } finally {
    if (toastTimer) window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => {
      toastMensaje.value = null
    }, 1800)
  }
}

const abrirConfirmacionAgregar = (producto: Producto, cantidad: number) => {
  modalProducto.value = producto
  modalCantidad.value = cantidad
  modalAbierto.value = true
}

const cerrarModal = () => {
  modalAbierto.value = false
  modalProducto.value = null
}

const confirmarAgregar = async () => {
  if (!modalProducto.value) return
  const producto = modalProducto.value
  const cantidad = modalCantidad.value
  cerrarModal()
  await agregarAlCarrito(producto, cantidad)
}
</script>

<template>
  <div class="pagina">
    <div v-if="modalAbierto" class="modal-overlay" @click.self="cerrarModal">
      <div class="modal-box" role="dialog" aria-modal="true">
        <div class="modal-header">
          <span class="modal-check">✓</span>
          <h3 class="modal-title">Producto agregado a tu Carro</h3>
          <button class="modal-close" @click="cerrarModal">×</button>
        </div>
        <div class="modal-body">
          <div class="modal-info">
            <div class="modal-nombre">{{ modalProducto?.nombre_producto }}</div>
            <div class="modal-cantidad">Cantidad: {{ modalCantidad }}</div>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-link" @click="cerrarModal">Seguir comprando</button>
          <button class="btn-solid" @click="confirmarAgregar">Agregar</button>
        </div>
      </div>
    </div>

    <div v-if="toastMensaje" class="toast" :class="toastTipo">
      {{ toastMensaje }}
    </div>

    <!-- ===== ENCABEZADO ===== -->
    <div class="encabezado">
      <h1 class="titulo-pagina">Gestión de productos</h1>
    </div>

    <!-- ===== BARRA DE FILTROS ===== -->
    <div class="barra-filtros">
      <div class="filtros-grupo">
        <input
          v-model="filtros.producto_ID"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por ID"
          @input="paginaActual = 1"
        />
        <input
          v-model="filtros.nombre_producto"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por Nombre"
          @input="paginaActual = 1"
        />
        <!-- Categorías dinámicas desde los datos -->
        <select
          v-model="filtros.categoria_ID"
          class="filtro-entrada filtro-select"
          @change="paginaActual = 1"
        >
          <option value="">Todas las categorías</option>
          <option v-for="cat in opcionesCategorias" :key="cat.id" :value="cat.id">{{ cat.nombre }}</option>
        </select>
      </div>
      <button class="btn-limpiar" @click="limpiarFiltros" title="Limpiar filtros">✕</button>
    </div>

    <!-- ===== TABLA ===== -->
    <ListaProductos
      :productos="productosPaginaActual"
      :cargando="cargando"
      :error="error"
      :config-orden="configOrden"
      :categorias-map="categoriasMap"
      @ordenar="cambiarOrden"
      @reintentar="cargarProductos"
      @actualizar="cargarProductos"
      @agregar="abrirConfirmacionAgregar"
    />

    <!-- ===== PAGINACIÓN ===== -->
    <div class="paginacion">
      <div class="paginacion-controles">
        <button class="btn-pagina" :disabled="paginaActual === 1" @click="cambiarPagina(paginaActual - 1)">‹</button>
        <button
          v-for="p in totalPaginas" :key="p"
          class="btn-pagina" :class="{ 'pagina-activa': p === paginaActual }"
          @click="cambiarPagina(p)"
        >{{ p }}</button>
        <button class="btn-pagina" :disabled="paginaActual === totalPaginas" @click="cambiarPagina(paginaActual + 1)">›</button>
      </div>

      <div class="selector-filas">
        <label for="filas-prod" class="selector-etiqueta">Filas:</label>
        <select id="filas-prod" v-model="elementosPorPagina" class="filtro-entrada filtro-select" style="min-width:70px" @change="paginaActual = 1">
          <option :value="5">5</option>
          <option :value="10">10</option>
          <option :value="15">15</option>
        </select>
      </div>
    </div>

  </div>
</template>

<style scoped>
.pagina { display: flex; flex-direction: column; gap: 20px; }
.toast {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 300;
  padding: 10px 14px;
  border-radius: 10px;
  color: #fff;
  font-size: 0.9rem;
  box-shadow: 0 6px 18px rgba(0,0,0,0.18);
  animation: fadeout 1.8s ease-in-out;
}
.toast.ok { background: #156895; }
.toast.error { background: #b00020; }
@keyframes fadeout {
  0% { opacity: 0; transform: translateY(-6px); }
  10% { opacity: 1; transform: translateY(0); }
  80% { opacity: 1; }
  100% { opacity: 0; }
}
.encabezado { display: flex; justify-content: space-between; align-items: center; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal-contenido { background: white; border-radius: 12px; padding: 28px; min-width: 380px; max-width: 90vw; position: relative; box-shadow: 0 8px 32px rgba(0,0,0,0.18); }
.modal-cerrar { position: absolute; top: 12px; right: 14px; background: none; border: none; font-size: 1.1rem; cursor: pointer; color: #666; }
.modal-cerrar:hover { color: #333; }
.barra-filtros { display: flex; align-items: center; gap: 12px; }
.filtros-grupo { display: flex; flex-wrap: wrap; gap: 10px; flex: 1; }
.filtro-entrada { padding: 8px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.875rem; outline: none; flex: 1; min-width: 140px; transition: border-color 0.2s; }
.filtro-entrada:focus { border-color: #156895; }
.filtro-select { background-color: white; cursor: pointer; }
.btn-limpiar { padding: 8px 12px; background: none; border: 1px solid #ccc; border-radius: 8px; cursor: pointer; color: #666; font-size: 0.95rem; transition: border-color 0.2s, color 0.2s; }
.btn-limpiar:hover { border-color: #156895; color: #156895; }
.paginacion { display: flex; align-items: center; justify-content: center; gap: 24px; flex-wrap: wrap; }
.paginacion-controles { display: flex; gap: 4px; }
.btn-pagina { min-width: 36px; height: 36px; padding: 0 8px; border: 1px solid #ddd; border-radius: 8px; background: white; cursor: pointer; font-size: 0.9rem; color: #333; transition: background-color 0.2s, border-color 0.2s; }
.btn-pagina:hover:not(:disabled) { border-color: #156895; color: #156895; }
.btn-pagina:disabled { opacity: 0.4; cursor: not-allowed; }
.pagina-activa { background-color: #156895; border-color: #156895; color: white; font-weight: 600; }
.selector-filas { display: flex; align-items: center; gap: 8px; }
.selector-etiqueta { font-size: 0.875rem; color: #555; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; z-index: 300; }
.modal-box { background: #fff; border-radius: 14px; padding: 18px 20px; width: 520px; max-width: 92vw; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }
.modal-header { display: flex; align-items: center; gap: 10px; position: relative; }
.modal-check { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border: 2px solid #2e7d32; color: #2e7d32; border-radius: 50%; font-size: 0.85rem; }
.modal-title { font-size: 1rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.modal-close { position: absolute; right: 0; top: -4px; background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #777; }
.modal-body { margin-top: 12px; }
.modal-info { display: flex; flex-direction: column; gap: 4px; color: #333; }
.modal-nombre { font-weight: 600; }
.modal-cantidad { color: #666; font-size: 0.9rem; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 16px; }
.btn-link { background: none; border: none; color: #156895; cursor: pointer; }
.btn-solid { background: #156895; color: #fff; border: none; border-radius: 22px; padding: 8px 16px; cursor: pointer; }
.btn-solid:hover { background: #1b76a5; }
</style>
