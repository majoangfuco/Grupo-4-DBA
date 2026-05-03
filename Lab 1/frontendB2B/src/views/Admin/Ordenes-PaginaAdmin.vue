<script setup lang="ts">
// =====================================================
// Ordenes-PaginaAdmin.vue
// Página de órdenes para el administrador.
// Muestra TODAS las órdenes del sistema sin filtro
// por usuario, siguiendo el patrón de Productos-PaginaAdmin.vue.
// =====================================================

import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import ListaOrdenes from '@/components/ordenes/listaOrdenes-vistaAdmin.vue'
import { ordenesServicio, type Orden } from '@/services/ordenesServicio'

// ==================== ESTADO ========================
const route = useRoute()
const ordenes   = ref<Orden[]>([])
const cargando  = ref(true)
const error     = ref<string | null>(null)

// ==================== FILTROS =======================
const filtros = reactive({ orden_ID: '', usuario_ID: (route.query.usuario_ID as string) || '', estado: '' })

watch(
  () => route.query.usuario_ID,
  (newVal) => {
    if (newVal !== undefined) {
      filtros.usuario_ID = newVal as string
    }
  }
)

const opcionesEstado = computed(() => {
  const set = new Set(ordenes.value.map(o => o.estado).filter(Boolean))
  return Array.from(set).sort()
})

const limpiarFiltros = () => {
  filtros.orden_ID   = ''
  filtros.usuario_ID = ''
  filtros.estado     = ''
  paginaActual.value = 1
}

// =================== ORDENAMIENTO ==================
const configOrden = reactive<{ clave: string; direccion: 'asc' | 'desc' }>({
  clave: 'fecha_Orden', direccion: 'desc',
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
const ordenesFiltradas = computed(() =>
  ordenes.value.filter(o => {
    const coincideId      = !filtros.orden_ID   || String(o.orden_ID).includes(filtros.orden_ID)
    const coincideUsuario = !filtros.usuario_ID || String(o.usuario_ID).includes(filtros.usuario_ID)
    const coincideEstado  = !filtros.estado     || o.estado === filtros.estado
    return coincideId && coincideUsuario && coincideEstado
  })
)

// ================== ORDENAMIENTO ==================
const ordenesOrdenadas = computed(() =>
  [...ordenesFiltradas.value].sort((a, b) => {
    const clave = configOrden.clave as keyof Orden
    const valA  = String(a[clave] ?? '').toLowerCase()
    const valB  = String(b[clave] ?? '').toLowerCase()
    if (valA < valB) return configOrden.direccion === 'asc' ? -1 : 1
    if (valA > valB) return configOrden.direccion === 'asc' ?  1 : -1
    return 0
  })
)

// =================== PAGINACIÓN ====================
const paginaActual       = ref(1)
const elementosPorPagina = ref(10)

const totalPaginas = computed(() =>
  Math.max(1, Math.ceil(ordenesOrdenadas.value.length / elementosPorPagina.value))
)

const ordenesPaginaActual = computed(() => {
  const inicio = (paginaActual.value - 1) * elementosPorPagina.value
  return ordenesOrdenadas.value.slice(inicio, inicio + elementosPorPagina.value)
})

const cambiarPagina = (pagina: number) => {
  if (pagina >= 1 && pagina <= totalPaginas.value) paginaActual.value = pagina
}

// ================ CARGA DE DATOS ===================
const cargarOrdenes = async () => {
  cargando.value = true
  error.value    = null
  try {
    const respuesta = await ordenesServicio.obtenerTodas()
    ordenes.value = respuesta.data
  } catch (err: unknown) {
    console.error('Error al obtener órdenes:', err)
    ordenes.value = []
    const axiosErr = err as { response?: { data?: { message?: string } } }
    error.value = axiosErr.response?.data?.message ?? 'Error al cargar las órdenes.'
  } finally {
    cargando.value = false
  }
}

onMounted(cargarOrdenes)
</script>

<template>
  <div class="pagina">

    <!-- ===== ENCABEZADO ===== -->
    <div class="encabezado">
      <h1 class="titulo-pagina">Gestión de órdenes</h1>
    </div>

    <!-- ===== BARRA DE FILTROS ===== -->
    <div class="barra-filtros">
      <div class="filtros-grupo">
        <input
          v-model="filtros.orden_ID"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por N° Orden"
          @input="paginaActual = 1"
        />
        <input
          v-model="filtros.usuario_ID"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por ID Usuario"
          @input="paginaActual = 1"
        />
        <select
          v-model="filtros.estado"
          class="filtro-entrada filtro-select"
          @change="paginaActual = 1"
        >
          <option value="">Todos los estados</option>
          <option v-for="est in opcionesEstado" :key="est" :value="est">{{ est }}</option>
        </select>
      </div>
      <button class="btn-limpiar" @click="limpiarFiltros" title="Limpiar filtros">✕</button>
    </div>

    <!-- ===== TABLA ===== -->
    <ListaOrdenes
      :ordenes="ordenesPaginaActual"
      :cargando="cargando"
      :error="error"
      :config-orden="configOrden"
      @ordenar="cambiarOrden"
      @reintentar="cargarOrdenes"
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
        <label for="filas-ord-admin" class="selector-etiqueta">Filas:</label>
        <select id="filas-ord-admin" v-model="elementosPorPagina" class="filtro-entrada filtro-select" style="min-width:70px" @change="paginaActual = 1">
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
.encabezado { display: flex; justify-content: space-between; align-items: center; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
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
