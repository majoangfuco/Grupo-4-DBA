<script setup lang="ts">
// =====================================================
// Ordenes-PaginaAdmin.vue
// Página de órdenes para el administrador.
// Muestra TODAS las órdenes del sistema sin filtro
// por usuario, siguiendo el patrón de Productos-PaginaAdmin.vue.
// =====================================================

import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ListaOrdenes from '@/components/ordenes/listaOrdenes-vistaAdmin.vue'
import { ordenesServicio, type OrdenAdmin } from '@/services/ordenesServicio'

// ==================== ESTADO ========================
const route = useRoute()
const router = useRouter()
const ordenes   = ref<Orden[]>([])
const cargando  = ref(true)
const error     = ref<string | null>(null)

// ==================== FILTROS =======================
const filtros = reactive({ rut_Empresa: '', estado: '' })
const route  = useRoute()
const router = useRouter()

const usuarioSeleccionado = computed<number | null>(() => {
  const valor = route.query.usuario_ID
  if (!valor) return null
  const id = Number(valor)
  return Number.isNaN(id) ? null : id
})

const correoClienteSeleccionado = computed<string | null>(() => {
  const valor = route.query.correo
  if (!valor) return null
  return String(valor)
})

const opcionesEstado = computed(() => {
  const set = new Set(ordenes.value.map(o => o.estado).filter(Boolean))
  return Array.from(set).sort()
})

const limpiarFiltros = () => {
  filtros.rut_Empresa = ''
  filtros.estado      = ''
  paginaActual.value  = 1
}

const tieneClienteSeleccionado = computed(() => correoClienteSeleccionado.value !== null)

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
    const coincideRut     = !filtros.rut_Empresa || o.rut_Empresa.includes(filtros.rut_Empresa)
    const coincideEstado  = !filtros.estado     || o.estado === filtros.estado
    const coincideUsuario = !usuarioSeleccionado.value || o.usuario_ID === usuarioSeleccionado.value
    return coincideRut && coincideEstado && coincideUsuario
  })
)

// ================== ORDENAMIENTO ==================
const ordenesOrdenadas = computed(() =>
  [...ordenesFiltradas.value].sort((a, b) => {
    const clave = configOrden.clave as keyof OrdenAdmin
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
    const respuesta = await ordenesServicio.obtenerVistaAdmin()
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

const aprobarOrden = async (ordenId: number) => {
  cargando.value = true
  error.value = null
  try {
    await ordenesServicio.aprobar(ordenId)
    await cargarOrdenes()
  } catch (err: unknown) {
    console.error('Error al aprobar orden:', err)
    const axiosErr = err as { response?: { data?: { message?: string } } }
    error.value = axiosErr.response?.data?.message ?? 'Error al aprobar la orden.'
  } finally {
    cargando.value = false
  }
}

const cancelarOrden = async (ordenId: number) => {
  cargando.value = true
  error.value = null
  try {
    await ordenesServicio.cancelar(ordenId)
    await cargarOrdenes()
  } catch (err: unknown) {
    console.error('Error al cancelar orden:', err)
    const axiosErr = err as { response?: { data?: { message?: string } } }
    error.value = axiosErr.response?.data?.message ?? 'Error al cancelar la orden.'
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
          v-model="filtros.rut_Empresa"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por RUT Empresa"
          @input="paginaActual = 1"
        />
        <select
          v-model="filtros.estado"
          class="filtro-entrada filtro-select"
          @change="paginaActual = 1"
        >
          <option value="">Todos los estados</option>
          <option v-for="est in opcionesEstado" :key="est" :value="est">{{ formatearEstado(est) }}</option>
        </select>
      </div>
      <button class="btn-limpiar" @click="limpiarFiltros" title="Limpiar filtros">✕</button>
    </div>
    <div v-if="tieneClienteSeleccionado" class="cliente-seleccionado">
      <span>Órdenes de: <strong>{{ correoClienteSeleccionado }}</strong></span>
    </div>

    <!-- ===== TABLA ===== -->
    <ListaOrdenes
      :ordenes="ordenesPaginaActual"
      :cargando="cargando"
      :error="error"
      :config-orden="configOrden"
      @ordenar="cambiarOrden"
      @reintentar="cargarOrdenes"
      @aprobar="aprobarOrden"
      @cancelar="cancelarOrden"
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
