<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { reporteVentasServicio, type ReporteVentas } from '@/services/reporteVentasServicio'

// ===================== ESTADO ========================
const reportes       = ref<ReporteVentas[]>([])
const cargando       = ref(true)
const refrescando    = ref(false)
const error          = ref<string | null>(null)
const totalConsolidado = ref<any>(null)

// ===================== FILTROS ========================
const filtroMesAno    = ref('')
const filtroCategoria = ref('')
const filtroAnio      = ref('')

// ===================== ORDENAMIENTO =================
const ordenActual = reactive({ campo: 'mesAno', direccion: 'desc' as 'asc' | 'desc' })

const ordenar = (campo: string) => {
  if (ordenActual.campo === campo) {
    ordenActual.direccion = ordenActual.direccion === 'asc' ? 'desc' : 'asc'
  } else {
    ordenActual.campo     = campo
    ordenActual.direccion = 'desc'
  }
  paginaActual.value = 1
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

// ===================== FILTRADO ========================
const reportesFiltrados = computed(() =>
  reportes.value.filter(r => {
    const coincideMes      = !filtroMesAno.value    || r.mesAno          === filtroMesAno.value
    const coincideCategoria = !filtroCategoria.value || r.nombreCategoria === filtroCategoria.value
    const coincideAnio     = !filtroAnio.value      || r.anio            === parseInt(filtroAnio.value)
    return coincideMes && coincideCategoria && coincideAnio
  })
)

// ===================== ORDENAMIENTO ==================
const reportesOrdenados = computed(() => {
  const copia = [...reportesFiltrados.value]
  copia.sort((a, b) => {
    const campo = ordenActual.campo as keyof ReporteVentas
    const valA  = a[campo]
    const valB  = b[campo]
    if (typeof valA === 'number' && typeof valB === 'number') {
      return ordenActual.direccion === 'asc' ? valA - valB : valB - valA
    }
    const strA = String(valA).toLowerCase()
    const strB = String(valB).toLowerCase()
    if (strA < strB) return ordenActual.direccion === 'asc' ? -1 : 1
    if (strA > strB) return ordenActual.direccion === 'asc' ?  1 : -1
    return 0
  })
  return copia
})

// ===================== PAGINACIÓN ====================
const paginaActual       = ref(1)
const elementosPorPagina = ref(10)

const totalPaginas = computed(() =>
  Math.max(1, Math.ceil(reportesOrdenados.value.length / elementosPorPagina.value))
)

const reportesPaginados = computed(() => {
  const inicio = (paginaActual.value - 1) * elementosPorPagina.value
  return reportesOrdenados.value.slice(inicio, inicio + elementosPorPagina.value)
})

const cambiarPagina = (p: number) => {
  if (p >= 1 && p <= totalPaginas.value) paginaActual.value = p
}

// ===================== UTILITY FUNCTIONS ============
const formatearMoneda = (valor: number): string =>
  new Intl.NumberFormat('es-CL', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(valor)

// ===================== MÉTODOS ======================
const cargarDatos = async () => {
  cargando.value = true
  error.value    = null
  try {
    const [reportesData, totalData] = await Promise.all([
      reporteVentasServicio.obtenerTodosLosReportes(),
      reporteVentasServicio.obtenerTotalConsolidado()
    ])
    reportes.value         = reportesData
    totalConsolidado.value = totalData
  } catch (err: any) {
    error.value = err.message || 'Error al cargar los reportes'
    console.error('Error al obtener reportes:', err)
  } finally {
    cargando.value = false
  }
}

const limpiarFiltros = () => {
  filtroMesAno.value    = ''
  filtroCategoria.value = ''
  filtroAnio.value      = ''
  paginaActual.value    = 1
}

const refrescarDatos = async () => {
  refrescando.value = true
  error.value       = null
  try {
    await reporteVentasServicio.refrescarReportes()
    await cargarDatos()
  } catch (err: any) {
    error.value = 'Error al refrescar la vista materializada: ' + (err.message || '')
    console.error('Error al refrescar reportes:', err)
  } finally {
    refrescando.value = false
  }
}

onMounted(cargarDatos)
</script>

<template>
  <div class="pagina">

    <!-- ===== ENCABEZADO ===== -->
    <div class="encabezado">
      <h1 class="titulo-pagina"> Reporte de Ventas</h1>
      <button
        class="btn-refrescar"
        @click="refrescarDatos"
        :disabled="cargando || refrescando"
        title="Refrescar vista materializada"
      >
        <span v-if="refrescando"> Refrescando...</span>
        <span v-else> Refrescar</span>
      </button>
    </div>

    <!-- ===== KPIs ===== -->
    <div v-if="totalConsolidado && !cargando" class="kpis-section">
      <div class="kpi-card">
        <span class="kpi-icono">🧾</span>
        <p class="kpi-etiqueta">Total de Órdenes</p>
        <p class="kpi-valor">{{ totalConsolidado.cantidadOrdenes }}</p>
      </div>
      <div class="kpi-card">
        <span class="kpi-icono">📦</span>
        <p class="kpi-etiqueta">Productos Vendidos</p>
        <p class="kpi-valor">{{ totalConsolidado.cantidadProductos }}</p>
      </div>
      <div class="kpi-card kpi-destacado">
        <span class="kpi-icono">💰</span>
        <p class="kpi-etiqueta">Monto Total</p>
        <p class="kpi-valor">${{ formatearMoneda(totalConsolidado.totalVendido) }}</p>
      </div>
      <div class="kpi-card">
        <span class="kpi-icono">📈</span>
        <p class="kpi-etiqueta">Precio Promedio</p>
        <p class="kpi-valor">${{ formatearMoneda(totalConsolidado.precioPromedio) }}</p>
      </div>
    </div>

    <!-- ===== BARRA DE FILTROS ===== -->
    <div class="barra-filtros">
      <div class="filtros-grupo">
        <div class="filtro-campo">
          <label class="filtro-label">Mes</label>
          <input
            v-model="filtroMesAno"
            type="month"
            class="filtro-entrada"
            @change="paginaActual = 1"
          />
        </div>
        <div class="filtro-campo">
          <label class="filtro-label">Categoría</label>
          <select
            v-model="filtroCategoria"
            class="filtro-entrada filtro-select"
            @change="paginaActual = 1"
          >
            <option value="">Todas las categorías</option>
            <option v-for="cat in categorias" :key="cat" :value="cat">{{ cat }}</option>
          </select>
        </div>
        <div class="filtro-campo">
          <label class="filtro-label">Año</label>
          <select
            v-model="filtroAnio"
            class="filtro-entrada filtro-select"
            @change="paginaActual = 1"
          >
            <option value="">Todos los años</option>
            <option v-for="year in anios" :key="year" :value="year">{{ year }}</option>
          </select>
        </div>
      </div>
      <button class="btn-limpiar" @click="limpiarFiltros" title="Limpiar filtros">✕</button>
    </div>

    <!-- ===== ESTADO: CARGANDO / ERROR / VACÍO ===== -->
    <div v-if="cargando" class="estado-mensaje">⏳ Cargando reportes...</div>
    <div v-else-if="error" class="estado-error">❌ {{ error }}</div>
    <div v-else-if="reportesFiltrados.length === 0" class="estado-mensaje">
      No hay datos con los filtros seleccionados.
    </div>

    <!-- ===== TABLA ===== -->
    <div v-else class="tabla-section">
      <div class="tabla-encabezado">
        <span class="tabla-titulo">Ventas Mensuales por Categoría</span>
        <span class="tabla-contador">{{ reportesFiltrados.length }} registros</span>
      </div>

      <div class="tabla-wrapper">
        <table class="tabla-reporte">
          <thead>
            <tr>
              <th @click="ordenar('mesAno')" class="columna-ordenable">
                Mes/Año
                <span v-if="ordenActual.campo === 'mesAno'" class="icono-orden">
                  {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
                </span>
              </th>
              <th @click="ordenar('nombreCategoria')" class="columna-ordenable">
                Categoría
                <span v-if="ordenActual.campo === 'nombreCategoria'" class="icono-orden">
                  {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
                </span>
              </th>
              <th @click="ordenar('cantidadOrdenes')" class="columna-ordenable texto-derecha">
                Órdenes
                <span v-if="ordenActual.campo === 'cantidadOrdenes'" class="icono-orden">
                  {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
                </span>
              </th>
              <th @click="ordenar('cantidadProductos')" class="columna-ordenable texto-derecha">
                Productos
                <span v-if="ordenActual.campo === 'cantidadProductos'" class="icono-orden">
                  {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
                </span>
              </th>
              <th @click="ordenar('totalVendido')" class="columna-ordenable texto-derecha">
                Total Vendido
                <span v-if="ordenActual.campo === 'totalVendido'" class="icono-orden">
                  {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
                </span>
              </th>
              <th @click="ordenar('precioPromedio')" class="columna-ordenable texto-derecha">
                Precio Promedio
                <span v-if="ordenActual.campo === 'precioPromedio'" class="icono-orden">
                  {{ ordenActual.direccion === 'asc' ? '▲' : '▼' }}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="reporte in reportesPaginados"
              :key="`${reporte.mesAno}-${reporte.nombreCategoria}`"
              class="fila-datos"
            >
              <td><span class="badge-mes">{{ reporte.mesAno }}</span></td>
              <td>{{ reporte.nombreCategoria }}</td>
              <td class="texto-derecha numero">{{ reporte.cantidadOrdenes }}</td>
              <td class="texto-derecha numero">{{ reporte.cantidadProductos }}</td>
              <td class="texto-derecha monto">${{ formatearMoneda(reporte.totalVendido) }}</td>
              <td class="texto-derecha monto">${{ formatearMoneda(reporte.precioPromedio) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ===== PAGINACIÓN ===== -->
    <div v-if="!cargando && !error && totalPaginas > 1" class="paginacion">
      <div class="paginacion-controles">
        <button class="btn-pagina" :disabled="paginaActual === 1" @click="cambiarPagina(paginaActual - 1)">‹</button>
        <button
          v-for="p in totalPaginas"
          :key="p"
          class="btn-pagina"
          :class="{ 'pagina-activa': p === paginaActual }"
          @click="cambiarPagina(p)"
        >{{ p }}</button>
        <button class="btn-pagina" :disabled="paginaActual === totalPaginas" @click="cambiarPagina(paginaActual + 1)">›</button>
      </div>
      <div class="selector-filas">
        <label for="filas-reporte" class="selector-etiqueta">Filas:</label>
        <select id="filas-reporte" v-model="elementosPorPagina" class="filtro-entrada filtro-select" style="min-width:70px" @change="paginaActual = 1">
          <option :value="5">5</option>
          <option :value="10">10</option>
          <option :value="20">20</option>
        </select>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* ── Layout general ─────────────────────────────── */
.pagina {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── Encabezado ──────────────────────────────────── */
.encabezado {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.titulo-pagina {
  font-size: 1.4rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

/* ── Botón refrescar ─────────────────────────────── */
.btn-refrescar {
  padding: 8px 18px;
  background-color: #156895;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-refrescar:hover:not(:disabled) {
  background-color: #0f4f72;
}

.btn-refrescar:disabled {
  background-color: #a0b8c8;
  cursor: not-allowed;
}

/* ── KPIs ────────────────────────────────────────── */
.kpis-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.kpi-card {
  background: white;
  border-radius: 10px;
  padding: 20px;
  border-left: 4px solid #156895;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi-card.kpi-destacado {
  border-left-color: #1a9c5b;
}

.kpi-icono {
  font-size: 1.4rem;
}

.kpi-etiqueta {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: #666;
  font-weight: 600;
  margin: 0;
  letter-spacing: 0.05em;
}

.kpi-valor {
  font-size: 1.6rem;
  font-weight: 700;
  color: #156895;
  margin: 0;
}

.kpi-destacado .kpi-valor {
  color: #1a9c5b;
}

/* ── Barra de filtros ────────────────────────────── */
.barra-filtros {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.filtros-grupo {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  flex: 1;
}

.filtro-campo {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 150px;
}

.filtro-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.filtro-entrada {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 0.875rem;
  outline: none;
  transition: border-color 0.2s;
  background: white;
}

.filtro-entrada:focus {
  border-color: #156895;
}

.filtro-select {
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
  align-self: flex-end;
}

.btn-limpiar:hover {
  border-color: #156895;
  color: #156895;
}

/* ── Estados ─────────────────────────────────────── */
.estado-mensaje,
.estado-error {
  padding: 20px;
  border-radius: 10px;
  text-align: center;
  font-size: 0.95rem;
}

.estado-mensaje {
  background-color: #e8f4fd;
  color: #156895;
  border: 1px solid #b8d8ee;
}

.estado-error {
  background-color: #ffebee;
  color: #c62828;
  border: 1px solid #ffcdd2;
}

/* ── Sección tabla ───────────────────────────────── */
.tabla-section {
  background: white;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.tabla-encabezado {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #eee;
}

.tabla-titulo {
  font-size: 0.95rem;
  font-weight: 600;
  color: #1a1a2e;
}

.tabla-contador {
  font-size: 0.8rem;
  color: #888;
  background: #f0f4f8;
  padding: 3px 10px;
  border-radius: 20px;
}

.tabla-wrapper {
  overflow-x: auto;
}

/* ── Tabla ───────────────────────────────────────── */
.tabla-reporte {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.tabla-reporte thead {
  background-color: #f7f9fc;
}

.tabla-reporte th {
  padding: 12px 16px;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 700;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 2px solid #e8edf2;
  user-select: none;
  white-space: nowrap;
}

.columna-ordenable {
  cursor: pointer;
  transition: background-color 0.15s;
}

.columna-ordenable:hover {
  background-color: #edf2f7;
  color: #156895;
}

.icono-orden {
  font-size: 10px;
  color: #156895;
  margin-left: 4px;
}

.tabla-reporte td {
  padding: 12px 16px;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
  color: #333;
}

.fila-datos:hover {
  background-color: #f7fbff;
}

.fila-datos:last-child td {
  border-bottom: none;
}

.badge-mes {
  display: inline-block;
  background-color: #e8f4fd;
  color: #156895;
  font-weight: 700;
  font-size: 0.8rem;
  padding: 3px 10px;
  border-radius: 20px;
  font-family: monospace;
  letter-spacing: 0.03em;
}

.texto-derecha {
  text-align: center;
}

.numero {
  font-family: monospace;
  color: #1a1a2e;
  font-weight: 600;
}

.monto {
  font-family: monospace;
  font-weight: 600;
  color: #156895;
}

/* ── Paginación ──────────────────────────────────── */
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

/* ── Responsive ──────────────────────────────────── */
@media (max-width: 768px) {
  .encabezado {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .kpis-section {
    grid-template-columns: 1fr 1fr;
  }

  .filtros-grupo {
    flex-direction: column;
  }

  .kpi-valor {
    font-size: 1.3rem;
  }
}
</style>