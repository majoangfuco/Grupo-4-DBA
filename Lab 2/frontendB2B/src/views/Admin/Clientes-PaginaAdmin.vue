<script setup lang="ts">
// =====================================================
// Clientes-PaginaAdmin.vue
// Página de gestión de clientes para el administrador.
// Incluye: filtros, tabla paginada, modal y formulario.
// =====================================================

import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ListaClientes from '@/components/clientes/ListaClientes.vue'
import { usuarioServicio } from '@/services/usuarioServicio'
import { ordenesServicio, type Orden } from '@/services/ordenesServicio'

// ===================== TIPOS ========================
interface Cliente {
  usuario_ID: number
  nombre_Usuario: string
  correo: string
  rut_Empresa: string
  ultima_Compra: string | null
  ultima_Compra: string | number | null
  ordenes_Pendiente: number
  ordenes_Aprobada: number
  ordenes_Cancelada: number
}

interface ClienteApi {
  usuario_ID: number
  nombre_Usuario: string
  correo: string
  rut_Empresa: string
}

// ==================== ESTADO ========================

// Lista de clientes desde la API
const clientes       = ref<Cliente[]>([])
const cargando       = ref(true)
const error          = ref<string | null>(null)

// ==================== FILTROS =======================
const filtros = reactive({
  nombre_Usuario: '',
  correo: '',
  rut_Empresa: '',
})

const router = useRouter()

const limpiarFiltros = () => {
  filtros.nombre_Usuario = ''
  filtros.correo = ''
  filtros.rut_Empresa = ''
  paginaActual.value = 1
}

const verTodasOrdenes = () => {
  router.push({ name: 'Admin-Ordenes' })
}

const verOrdenesCliente = (cliente: Cliente) => {
  router.push({
    name: 'Admin-Ordenes',
    query: {
      usuario_ID: cliente.usuario_ID,
      correo: cliente.correo,
    },
  })
}

// =================== ORDENAMIENTO ==================
const configOrden = reactive<{ clave: string; direccion: 'asc' | 'desc' }>({
  clave:     'nombre_Usuario',
  direccion: 'asc',
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
const clientesFiltrados = computed(() => {
  return clientes.value.filter(c => {
    const coincideNombre   = !filtros.nombre_Usuario || c.nombre_Usuario.toLowerCase().includes(filtros.nombre_Usuario.toLowerCase())
    const coincideCorreo   = !filtros.correo         || c.correo.toLowerCase().includes(filtros.correo.toLowerCase())
    const coincideRut      = !filtros.rut_Empresa    || c.rut_Empresa.toLowerCase().includes(filtros.rut_Empresa.toLowerCase())
    return coincideNombre && coincideCorreo && coincideRut
  })
})

// ================== ORDENAMIENTO ==================
const clientesOrdenados = computed(() => {
  return [...clientesFiltrados.value].sort((a, b) => {
    const clave = configOrden.clave as keyof Cliente
    const valA  = String(a[clave] ?? '').toLowerCase()
    const valB  = String(b[clave] ?? '').toLowerCase()
    if (valA < valB) return configOrden.direccion === 'asc' ? -1 : 1
    if (valA > valB) return configOrden.direccion === 'asc' ?  1 : -1
    return 0
  })
})

// =================== PAGINACIÓN ====================
const paginaActual       = ref(1)
const elementosPorPagina = ref(10)

const totalPaginas = computed(() =>
  Math.max(1, Math.ceil(clientesOrdenados.value.length / elementosPorPagina.value))
)

const clientesPaginaActual = computed(() => {
  const inicio = (paginaActual.value - 1) * elementosPorPagina.value
  return clientesOrdenados.value.slice(inicio, inicio + elementosPorPagina.value)
})

const cambiarPagina = (pagina: number) => {
  if (pagina >= 1 && pagina <= totalPaginas.value) {
    paginaActual.value = pagina
  }
}

// Al cambiar filtros, volver a la primera página
const manejarCambioFiltro = () => {
  paginaActual.value = 1
}

// ================ CARGA DE DATOS ===================
const cargarClientes = async () => {
  cargando.value = true
  error.value    = null
  try {
    const [respuesta, ordenesResp] = await Promise.all([
      usuarioServicio.obtenerClientes(),
      ordenesServicio.obtenerTodas(),
    ])
    const resumenPorCliente = construirResumenOrdenes(ordenesResp.data)
    clientes.value = respuesta.data.clientes.map((cliente: ClienteApi) => {
      const resumen = resumenPorCliente.get(cliente.usuario_ID) ?? { pendiente: 0, aprobada: 0, cancelada: 0 }
      return {
        ...cliente,
        ordenes_Pendiente: resumen.pendiente,
        ordenes_Aprobada: resumen.aprobada,
        ordenes_Cancelada: resumen.cancelada,
      }
    })
  } catch (err: unknown) {
    console.error('Error al obtener clientes:', err)
    clientes.value = []
    const axiosErr = err as { response?: { data?: { message?: string } } }
    error.value    = axiosErr.response?.data?.message ?? 'Error al cargar los clientes.'
  } finally {
    cargando.value = false
  }
}

const construirResumenOrdenes = (ordenes: Orden[]) => {
  const map = new Map<number, { pendiente: number; aprobada: number; cancelada: number }>()
  ordenes.forEach((orden) => {
    const usuarioId = Number(orden.usuario_ID)
    if (!usuarioId || Number.isNaN(usuarioId)) return
    const estado = String(orden.estado ?? '').trim().toUpperCase()
    const actual = map.get(usuarioId) ?? { pendiente: 0, aprobada: 0, cancelada: 0 }
    if (estado === 'PENDIENTE') actual.pendiente += 1
    if (estado === 'APROBADA') actual.aprobada += 1
    if (estado === 'CANCELADA') actual.cancelada += 1
    map.set(usuarioId, actual)
  })
  return map
}

// Carga inicial
onMounted(cargarClientes)
</script>

<template>
  <div class="pagina">

    <!-- ===== ENCABEZADO ===== -->
    <div class="encabezado">
      <h1 class="titulo-pagina">Gestión de Clientes</h1>
      <button class="btn-ver-todas" @click="verTodasOrdenes">Ver todas las órdenes</button>
    </div>

    <!-- ===== BARRA DE FILTROS ===== -->
    <div class="barra-filtros">
      <div class="filtros-grupo">
        <input
          v-model="filtros.nombre_Usuario"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por Nombre"
          @input="manejarCambioFiltro"
        />
        <input
          v-model="filtros.correo"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por Correo"
          @input="manejarCambioFiltro"
        />
        <input
          v-model="filtros.rut_Empresa"
          class="filtro-entrada"
          type="text"
          placeholder="Buscar por RUT Empresa"
          @input="manejarCambioFiltro"
        />
      </div>

      <button class="btn-limpiar" @click="limpiarFiltros" title="Limpiar filtros">
        ✕
      </button>
    </div>

    <!-- ===== TABLA DE CLIENTES ===== -->
    <ListaClientes
      :clientes="clientesPaginaActual"
      :cargando="cargando"
      :error="error"
      :config-orden="configOrden"
      @ordenar="cambiarOrden"
      @reintentar="cargarClientes"
      @seleccionar="verOrdenesCliente"
    />

    <!-- ===== PAGINACIÓN ===== -->
    <div class="paginacion">
      <div class="paginacion-controles">
        <button
          class="btn-pagina"
          :disabled="paginaActual === 1"
          @click="cambiarPagina(paginaActual - 1)"
        >
          ‹
        </button>

        <button
          v-for="pagina in totalPaginas"
          :key="pagina"
          class="btn-pagina"
          :class="{ 'pagina-activa': pagina === paginaActual }"
          @click="cambiarPagina(pagina)"
        >
          {{ pagina }}
        </button>

        <button
          class="btn-pagina"
          :disabled="paginaActual === totalPaginas"
          @click="cambiarPagina(paginaActual + 1)"
        >
          ›
        </button>
      </div>

      <!-- Selector de filas por página -->
      <div class="selector-filas">
        <label for="filas-por-pagina" class="selector-etiqueta">Filas:</label>
        <select
          id="filas-por-pagina"
          v-model="elementosPorPagina"
          class="filtro-entrada filtro-select"
          style="min-width: 70px;"
          @change="paginaActual = 1"
        >
          <option :value="5">5</option>
          <option :value="10">10</option>
          <option :value="15">15</option>
        </select>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* ===== PÁGINA ===== */
.pagina {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== ENCABEZADO ===== */
.encabezado {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.titulo-pagina {
  font-size: 1.4rem;
  font-weight: 700;
  color: #1a1a2e;
}

.btn-agregar-cliente {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background-color: #156895;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-agregar-cliente:hover {
  background-color: #0f5070;
}

.btn-ver-todas {
  padding: 10px 18px;
  background-color: #156895;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s, transform 0.2s;
}

.btn-ver-todas:hover {
  background-color: #0f5070;
  transform: translateY(-1px);
}

/* ===== MODAL ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal-contenido {
  background: white;
  border-radius: 12px;
  padding: 28px;
  min-width: 380px;
  max-width: 90vw;
  position: relative;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
}

.modal-cerrar {
  position: absolute;
  top: 12px;
  right: 14px;
  background: none;
  border: none;
  font-size: 1.1rem;
  cursor: pointer;
  color: #666;
  transition: color 0.2s;
}

.modal-cerrar:hover { color: #333; }

/* ===== BARRA DE FILTROS ===== */
.barra-filtros {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filtros-grupo {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  flex: 1;
}

.filtro-entrada {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 0.875rem;
  outline: none;
  flex: 1;
  min-width: 140px;
  transition: border-color 0.2s;
}

.filtro-entrada:focus {
  border-color: #156895;
}

.filtro-select {
  background-color: white;
  cursor: pointer;
}

.btn-limpiar {
  padding: 8px 12px;
  background: none;
  border: 1px solid #ccc;
  border-radius: 8px;
  cursor: pointer;
  color: #666;
  font-size: 0.95rem;
  transition: border-color 0.2s, color 0.2s;
  white-space: nowrap;
}

.btn-limpiar:hover {
  border-color: #156895;
  color: #156895;
}

/* ===== PAGINACIÓN ===== */
.paginacion {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  flex-wrap: wrap;
}

.paginacion-controles {
  display: flex;
  gap: 4px;
}

.btn-pagina {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
  color: #333;
  transition: background-color 0.2s, border-color 0.2s;
}

.btn-pagina:hover:not(:disabled) {
  border-color: #156895;
  color: #156895;
}

.btn-pagina:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagina-activa {
  background-color: #156895;
  border-color: #156895;
  color: white;
  font-weight: 600;
}

.selector-filas {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selector-etiqueta {
  font-size: 0.875rem;
  color: #555;
}
</style>
