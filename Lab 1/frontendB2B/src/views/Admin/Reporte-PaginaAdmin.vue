<template>
  <div class="reporte-container">
    <h1 class="titulo">📊 Dashboard Financiero - Reporte de Ventas</h1>

    <!-- Filtros y Controles -->
    <div class="filtros-section">
      <div class="filtro-grupo">
        <label>Filtrar por Mes:</label>
        <input 
          v-model="filtroMesAno" 
          type="month" 
          class="input-mes"
          @change="aplicarFiltroMes"
        />
        <button @click="limpiarFiltros" class="btn btn-secondary">Limpiar</button>
      </div>

      <div class="filtro-grupo">
        <label>Filtrar por Categoría:</label>
        <select v-model="filtroCategoria" class="input-select" @change="aplicarFiltroCategoria">
          <option value="">-- Todas las categorías --</option>
          <option v-for="cat in categorias" :key="cat" :value="cat">{{ cat }}</option>
        </select>
      </div>

      <div class="filtro-grupo">
        <label>Filtrar por Año:</label>
        <select v-model="filtroAnio" class="input-select" @change="aplicarFiltroAnio">
          <option value="">-- Todos los años --</option>
          <option v-for="year in anios" :key="year" :value="year">{{ year }}</option>
        </select>
      </div>

      <button @click="refrescarDatos" class="btn btn-refresh" :disabled="cargando">
        <span v-if="!cargando">🔄 Refrescar</span>
        <span v-else>Cargando...</span>
      </button>
    </div>

    <!-- KPIs Generales -->
    <div v-if="totalConsolidado" class="kpis-section">
      <div class="kpi-card">
        <h3>Total de Órdenes</h3>
        <p class="kpi-valor">{{ totalConsolidado.cantidadOrdenes }}</p>
      </div>
      <div class="kpi-card">
        <h3>Productos Vendidos</h3>
        <p class="kpi-valor">{{ totalConsolidado.cantidadProductos }}</p>
      </div>
      <div class="kpi-card">
        <h3>Monto Total Vendido</h3>
        <p class="kpi-valor">${{ formatearMoneda(totalConsolidado.totalVendido) }}</p>
      </div>
      <div class="kpi-card">
        <h3>Precio Promedio</h3>
        <p class="kpi-valor">${{ formatearMoneda(totalConsolidado.precioPromedio) }}</p>
      </div>
    </div>

    <!-- Estados de carga y error -->
    <div v-if="cargando" class="estado-mensaje">⏳ Cargando reportes...</div>
    <div v-else-if="error" class="estado-error">❌ {{ error }}</div>
    <div v-else-if="reportesFiltrados.length === 0" class="estado-mensaje">
      No hay datos disponibles con los filtros seleccionados
    </div>

    <!-- Tabla de Reportes -->
    <div v-else class="tabla-section">
      <h2>Ventas Mensuales por Categoría</h2>
      <table class="tabla-reporte">
        <thead>
          <tr>
            <th @click="ordenar('mesAno')" class="columna-ordenable">
              Mes/Año
              <span class="icon-orden" v-if="ordenActual.campo === 'mesAno'">
                {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
              </span>
            </th>
            <th @click="ordenar('nombreCategoria')" class="columna-ordenable">
              Categoría
              <span class="icon-orden" v-if="ordenActual.campo === 'nombreCategoria'">
                {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
              </span>
            </th>
            <th @click="ordenar('cantidadOrdenes')" class="columna-ordenable">
              Órdenes
              <span class="icon-orden" v-if="ordenActual.campo === 'cantidadOrdenes'">
                {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
              </span>
            </th>
            <th @click="ordenar('cantidadProductos')" class="columna-ordenable">
              Productos
              <span class="icon-orden" v-if="ordenActual.campo === 'cantidadProductos'">
                {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
              </span>
            </th>
            <th @click="ordenar('totalVendido')" class="columna-ordenable">
              Total Vendido
              <span class="icon-orden" v-if="ordenActual.campo === 'totalVendido'">
                {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
              </span>
            </th>
            <th>Precio Promedio</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="reporte in reportesOrdenados" :key="`${reporte.mesAno}-${reporte.nombreCategoria}`" class="fila-datos">
            <td class="destacado">{{ reporte.mesAno }}</td>
            <td>{{ reporte.nombreCategoria }}</td>
            <td class="numero">{{ reporte.cantidadOrdenes }}</td>
            <td class="numero">{{ reporte.cantidadProductos }}</td>
            <td class="monto">${{ formatearMoneda(reporte.totalVendido) }}</td>
            <td class="monto">${{ formatearMoneda(reporte.precioPromedio) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Paginación -->
      <div v-if="totalPaginas > 1" class="paginacion">
        <button 
          @click="paginaActual = Math.max(1, paginaActual - 1)" 
          :disabled="paginaActual === 1"
          class="btn-paginacion"
        >
          ← Anterior
        </button>
        
        <span class="info-paginacion">
          Página {{ paginaActual }} de {{ totalPaginas }}
        </span>

        <button 
          @click="paginaActual = Math.min(totalPaginas, paginaActual + 1)" 
          :disabled="paginaActual === totalPaginas"
          class="btn-paginacion"
        >
          Siguiente →
        </button>
      </div>
    </div>

    <div class="enlace-section">
      <RouterLink to="/productosAdmin" class="btn btn-link">
        ← Volver a Productos
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { reporteVentasServicio, type ReporteVentas } from '@/services/reporteVentasServicio'

// ===================== TIPOS ========================
interface ReporteFiltrado extends ReporteVentas {
  // Extiende con métodos si es necesario
}

// ===================== ESTADO ========================
const reportes = ref<ReporteVentas[]>([])
const cargando = ref(true)
const error = ref<string | null>(null)
const totalConsolidado = ref<any>(null)

// ===================== FILTROS ========================
const filtroMesAno = ref('')
const filtroCategoria = ref('')
const filtroAnio = ref('')

// ===================== ORDENAMIENTO =================
const ordenActual = reactive({ campo: 'mesAno', direccion: 'desc' as 'asc' | 'desc' })

const ordenar = (campo: string) => {
  if (ordenActual.campo === campo) {
    ordenActual.direccion = ordenActual.direccion === 'asc' ? 'desc' : 'asc'
  } else {
    ordenActual.campo = campo
    ordenActual.direccion = 'desc'
  }
}

// ===================== FILTRADO ========================
const reportesFiltrados = computed(() =>
  reportes.value.filter(r => {
    const coincideMes = !filtroMesAno.value || r.mesAno === filtroMesAno.value
    const coincideCategoria = !filtroCategoria.value || r.nombreCategoria === filtroCategoria.value
    const coincideAnio = !filtroAnio.value || r.anio === parseInt(filtroAnio.value)
    return coincideMes && coincideCategoria && coincideAnio
  })
)

// ===================== ORDENAMIENTO ==================
const reportesOrdenados = computed(() => {
  const copia = [...reportesFiltrados.value]
  copia.sort((a, b) => {
    const campo = ordenActual.campo as keyof ReporteVentas
    const valA = a[campo]
    const valB = b[campo]

    if (typeof valA === 'number' && typeof valB === 'number') {
      return ordenActual.direccion === 'asc' ? valA - valB : valB - valA
    }

    const strA = String(valA).toLowerCase()
    const strB = String(valB).toLowerCase()
    if (strA < strB) return ordenActual.direccion === 'asc' ? -1 : 1
    if (strA > strB) return ordenActual.direccion === 'asc' ? 1 : -1
    return 0
  })
  return copia
})

// ===================== PAGINACIÓN ====================
const paginaActual = ref(1)
const elementosPorPagina = ref(10)

const totalPaginas = computed(() =>
  Math.max(1, Math.ceil(reportesOrdenados.value.length / elementosPorPagina.value))
)

const reportesPaginados = computed(() => {
  const inicio = (paginaActual.value - 1) * elementosPorPagina.value
  return reportesOrdenados.value.slice(inicio, inicio + elementosPorPagina.value)
})

// ===================== UTILITY FUNCTIONS ============
const formatearMoneda = (valor: number): string => {
  return new Intl.NumberFormat('es-CL', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(valor)
}

// ===================== DATOS DINÁMICOS ===============
const categorias = computed(() => {
  const set = new Set(reportes.value.map(r => r.nombreCategoria))
  return Array.from(set).sort()
})

const anios = computed(() => {
  const set = new Set(reportes.value.map(r => r.anio))
  return Array.from(set).sort((a, b) => b - a)
})

// ===================== MÉTODOS ======================
const cargarDatos = async () => {
  cargando.value = true
  error.value = null
  try {
    // Cargar reportes completos
    reportes.value = await reporteVentasServicio.obtenerTodosLosReportes()
    
    // Cargar total consolidado
    totalConsolidado.value = await reporteVentasServicio.obtenerTotalConsolidado()
  } catch (err: any) {
    error.value = err.message || 'Error al cargar los reportes'
    console.error(err)
  } finally {
    cargando.value = false
  }
}

const aplicarFiltroMes = async () => {
  if (filtroMesAno.value) {
    cargando.value = true
    try {
      reportes.value = await reporteVentasServicio.obtenerPorMesAno(filtroMesAno.value)
    } catch (err: any) {
      error.value = err.message || 'Error al filtrar por mes'
    } finally {
      cargando.value = false
    }
  }
}

const aplicarFiltroCategoria = async () => {
  if (filtroCategoria.value) {
    cargando.value = true
    try {
      reportes.value = await reporteVentasServicio.obtenerPorCategoria(filtroCategoria.value)
    } catch (err: any) {
      error.value = err.message || 'Error al filtrar por categoría'
    } finally {
      cargando.value = false
    }
  }
}

const aplicarFiltroAnio = async () => {
  if (filtroAnio.value) {
    cargando.value = true
    try {
      reportes.value = await reporteVentasServicio.obtenerPorAnio(parseInt(filtroAnio.value))
    } catch (err: any) {
      error.value = err.message || 'Error al filtrar por año'
    } finally {
      cargando.value = false
    }
  }
}

const limpiarFiltros = () => {
  filtroMesAno.value = ''
  filtroCategoria.value = ''
  filtroAnio.value = ''
  paginaActual.value = 1
  cargarDatos()
}

const refrescarDatos = async () => {
  await reporteVentasServicio.refrescarReportes()
  await cargarDatos()
}

// ===================== CICLO DE VIDA =================
onMounted(() => {
  cargarDatos()
})
</script>

<style scoped>
.reporte-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  background-color: #f5f5f5;
  border-radius: 8px;
}

.titulo {
  color: #333;
  margin-bottom: 30px;
  font-size: 28px;
  font-weight: bold;
}

/* FILTROS */
.filtros-section {
  display: flex;
  gap: 20px;
  margin-bottom: 30px;
  flex-wrap: wrap;
  align-items: flex-end;
  background-color: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.filtro-grupo {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.filtro-grupo label {
  font-weight: bold;
  color: #333;
  font-size: 14px;
}

.input-mes, .input-select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.input-mes:focus, .input-select:focus {
  outline: none;
  border-color: #42b883;
  box-shadow: 0 0 5px rgba(66, 184, 131, 0.3);
}

/* BOTONES */
.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-secondary {
  background-color: #999;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #777;
}

.btn-refresh {
  background-color: #42b883;
  color: white;
}

.btn-refresh:hover:not(:disabled) {
  background-color: #369870;
}

.btn-refresh:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.btn-link {
  background-color: #42b883;
  color: white;
  text-decoration: none;
  display: inline-block;
}

.btn-link:hover {
  background-color: #369870;
}

/* KPIs */
.kpis-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.kpi-card {
  background-color: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  text-align: center;
  border-left: 4px solid #42b883;
}

.kpi-card h3 {
  color: #666;
  font-size: 14px;
  margin-bottom: 10px;
  text-transform: uppercase;
}

.kpi-valor {
  color: #42b883;
  font-size: 32px;
  font-weight: bold;
  margin: 0;
}

/* ESTADOS */
.estado-mensaje, .estado-error {
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  font-size: 16px;
  margin-bottom: 20px;
}

.estado-mensaje {
  background-color: #e3f2fd;
  color: #1976d2;
}

.estado-error {
  background-color: #ffebee;
  color: #c62828;
}

/* TABLA */
.tabla-section {
  background-color: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.tabla-section h2 {
  margin-top: 0;
  color: #333;
}

.tabla-reporte {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 20px;
}

.tabla-reporte thead {
  background-color: #f0f0f0;
}

.tabla-reporte th {
  padding: 12px;
  text-align: left;
  font-weight: bold;
  color: #333;
  border-bottom: 2px solid #ddd;
  user-select: none;
}

.columna-ordenable {
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.columna-ordenable:hover {
  background-color: #e8e8e8;
}

.icon-orden {
  font-size: 12px;
  color: #42b883;
}

.tabla-reporte td {
  padding: 12px;
  border-bottom: 1px solid #eee;
}

.fila-datos:hover {
  background-color: #f9f9f9;
}

.destacado {
  font-weight: bold;
  color: #42b883;
}

.numero, .monto {
  text-align: right;
  font-family: monospace;
}

/* PAGINACIÓN */
.paginacion {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 15px;
  margin-top: 20px;
}

.btn-paginacion {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background-color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-paginacion:hover:not(:disabled) {
  background-color: #42b883;
  color: white;
  border-color: #42b883;
}

.btn-paginacion:disabled {
  background-color: #f0f0f0;
  color: #999;
  cursor: not-allowed;
}

.info-paginacion {
  font-weight: bold;
  color: #333;
}

/* ENLACE */
.enlace-section {
  margin-top: 30px;
  text-align: center;
}

@media (max-width: 768px) {
  .filtros-section {
    flex-direction: column;
  }

  .kpis-section {
    grid-template-columns: 1fr 1fr;
  }

  .tabla-reporte {
    font-size: 12px;
  }

  .tabla-reporte th, .tabla-reporte td {
    padding: 8px;
  }

  .kpi-valor {
    font-size: 24px;
  }
}
</style>
