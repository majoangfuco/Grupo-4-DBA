<script setup lang="ts">
// =====================================================
// Productos-PaginaAdmin.vue
// Página de gestión de herramientas (CRUD).
// Operaciones: Listar, Agregar, Editar tarifas, Ver detalle.
// =====================================================

import { ref, reactive, computed, onMounted } from 'vue'
import ListaProductos from '@/components/productos/ListaProductos.vue'
import FormularioAgregarProducto from '@/components/productos/FormularioAgregarProducto.vue'
import FormularioDescuentoCategoria from '@/components/productos/FormularioDescuentoCategoria.vue'
import { productoServicio } from '@/services/productoServicio'
import { categoriaServicio, type CategoriaEntidad } from '@/services/categoriaServicio'

// ===================== TIPOS ========================
interface Producto {
  producto_ID: number
  categoria_ID: number
  nombre_producto: string
  descripcion: string
  precio: number
  stock: number
  stock_reservado?: number
  sku: string
  activo?: boolean
}

// ==================== ESTADO ========================
const productos  = ref<Producto[]>([])
const cargando   = ref(true)
const error      = ref<string | null>(null)
const modalAbierto = ref(false)
const modalDescuentoAbierto = ref(false)
const categorias = ref<CategoriaEntidad[]>([])

// ==================== FILTROS =======================
const filtros = reactive({ nombre_producto: '', categoria_ID: '' })

// Categorías disponibles (se calculan desde los datos)
const opcionesCategorias = computed(() =>
  categorias.value.map(c => ({
    id: c.categoria_ID,
    nombre: c.estado_Categoria ? c.nombre_Categoria : `${c.nombre_Categoria} (inactiva)`,
  }))
)

const categoriasMap = computed(() => {
  const map = new Map<number, string>()
  categorias.value.forEach(c => map.set(c.categoria_ID, c.nombre_Categoria))
  return map
})

const limpiarFiltros = () => {
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
    const coincideCategoria = !filtros.categoria_ID  || p.categoria_ID === Number(filtros.categoria_ID)
    return coincideNombre && coincideCategoria
  })
)

// ================== ORDENAMIENTO ==================
const productosOrdenados = computed(() =>
  [...productosFiltrados.value].sort((a, b) => {
    const clave = configOrden.clave
    const valor = (p: Producto): string | number => {
      if (clave === 'stock_disponible') return (p.stock ?? 0) - (p.stock_reservado ?? 0)
      if (clave === 'activo') return p.activo ? 1 : 0
      return String((p as Record<string, unknown>)[clave] ?? '').toLowerCase()
    }
    const valA = valor(a)
    const valB = valor(b)
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

// ============= MANEJADORES DE EVENTOS ==============
const manejarProductoAgregado = () => {
  cargarProductos()
  modalAbierto.value = false
}

const manejarDescuentoAplicado = () => {
  cargarProductos()
  modalDescuentoAbierto.value = false
}

// Carga inicial
onMounted(cargarProductos)
</script>

<template>
  <div class="pagina">

    <!-- ===== ENCABEZADO ===== -->
    <div class="encabezado">
      <h1 class="titulo-pagina">Gestión de productos</h1>
      <div class="botones-acciones">
        <button class="btn-descuentos" @click="modalDescuentoAbierto = true">
          💰 Aplicar Descuento
        </button>
        <button class="btn-agregar" @click="modalAbierto = true">
          + Agregar Nuevo Producto
        </button>
      </div>
    </div>

    <!-- ===== MODAL: DESCUENTOS ===== -->
    <Teleport to="body">
      <div v-if="modalDescuentoAbierto" class="modal-overlay" @click.self="modalDescuentoAbierto = false">
        <div class="modal-contenido">
          <FormularioDescuentoCategoria 
            :categorias="categorias"
            @descuentoAplicado="manejarDescuentoAplicado" 
            @cancelado="modalDescuentoAbierto = false" 
          />
        </div>
      </div>
    </Teleport>

    <!-- ===== MODAL: AGREGAR HERRAMIENTA ===== -->
    <Teleport to="body">
      <div v-if="modalAbierto" class="modal-overlay" @click.self="modalAbierto = false">
        <div class="modal-contenido">
          <FormularioAgregarProducto 
            @productoAgregado="manejarProductoAgregado" 
            @cancelado="modalAbierto = false" 
          />
        </div>
      </div>
    </Teleport>

    <!-- ===== BARRA DE FILTROS ===== -->
    <div class="barra-filtros">
      <div class="filtros-grupo">
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
.encabezado { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
.botones-acciones { display: flex; gap: 10px; align-items: center; }
.btn-agregar { padding: 10px 20px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.9rem; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
.btn-agregar:hover { background-color: #0f5070; }
.btn-descuentos { padding: 10px 18px; background-color: #f57c00; color: white; border: none; border-radius: 8px; font-size: 0.9rem; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
.btn-descuentos:hover { background-color: #e65100; }
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
</style>
