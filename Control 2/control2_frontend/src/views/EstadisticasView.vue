<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Icon } from '@iconify/vue'
import { tareasApi } from '@/services/api'
import StatCard from '@/components/StatCard.vue'
import BarChart from '@/components/BarChart.vue'

const loading = ref(false)
const error = ref('')

const tareasPorSector     = ref<any[]>([])
const tareaMasCercana     = ref<any>(null)
const sectorRadio2km      = ref<any>(null)
const promedioDistancia   = ref<number | null>(null)
const sectoresPendientes  = ref<any[]>([])
const tareasPorUsuario    = ref<any[]>([])
const sectorRadio5km      = ref<any>(null)

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const results = await Promise.allSettled([
      tareasApi.estadisticas.porSector(),
      tareasApi.estadisticas.masCercana(),
      tareasApi.estadisticas.sectorRadio2km(),
      tareasApi.estadisticas.promedioDistancia(),
      tareasApi.estadisticas.sectoresPendientes(),
      tareasApi.estadisticas.porUsuarioSector(),
      tareasApi.estadisticas.sectorRadio5km(),
    ])

    const data = (r: PromiseSettledResult<any>) =>
      r.status === 'fulfilled' ? r.value.data : null

    tareasPorSector.value    = data(results[0]) ?? []
    tareaMasCercana.value    = data(results[1]) ?? null
    sectorRadio2km.value     = data(results[2]) ?? null
    promedioDistancia.value  = data(results[3])?.promedioMetros ?? null
    sectoresPendientes.value = data(results[4]) ?? []
    tareasPorUsuario.value   = data(results[5]) ?? []
    sectorRadio5km.value     = data(results[6]) ?? null

    if (results.some(r => r.status === 'rejected')) {
      error.value = 'Algunas estadísticas no pudieron cargarse. Verifica tu ubicación en Perfil.'
    }
  } catch {
    error.value = 'Error al cargar estadísticas'
  } finally {
    loading.value = false
  }
})

function formatMetros(metros: number | null) {
  if (metros === null || metros === undefined) return 'Sin datos'
  if (metros < 1000) return `${metros.toFixed(0)} m`
  return `${(metros / 1000).toFixed(2)} km`
}

// Máximo de tareas por sector (para barra proporcional)
const maxTareasSector = computed(() =>
  tareasPorSector.value.length ? Math.max(...tareasPorSector.value.map((i: any) => Number(i.total))) : 1
)
const maxPendientes = computed(() =>
  sectoresPendientes.value.length ? Math.max(...sectoresPendientes.value.map((i: any) => Number(i.total))) : 1
)

// Agrupar tareas por usuario para la tabla Q6
const usuariosUnicos = computed(() => {
  const set = new Set(tareasPorUsuario.value.map((i: any) => i.usuario))
  return Array.from(set)
})
const sectoresUnicos = computed(() => {
  const set = new Set(tareasPorUsuario.value.map((i: any) => i.sector))
  return Array.from(set)
})
function getCellValue(usuario: string, sector: string) {
  const found = tareasPorUsuario.value.find((i: any) => i.usuario === usuario && i.sector === sector)
  return found ? found.total : 0
}
</script>

<template>
  <div class="estadisticas-page">
    <div class="page-header">
      <h1><Icon icon="lucide:bar-chart-2" class="icon" /> Estadísticas Geoespaciales</h1>
      <p class="subtitle">Análisis espacial de tareas basado en tu ubicación geográfica registrada</p>
    </div>

    <!-- Estado de carga -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>Calculando estadísticas geoespaciales...</p>
    </div>

    <!-- Error parcial -->
    <div v-if="error && !loading" class="warn-banner">
      <Icon icon="lucide:triangle-alert" class="icon" /> {{ error }}
    </div>

    <div v-if="!loading" class="stats-layout">

      <!-- ===== FILA 1: Cards métricas clave — usando StatCard (src/components/StatCard.vue) ===== -->
      <div class="metrics-row">

        <!-- Q2: Tarea más cercana -->
        <StatCard label="Tarea Pendiente Más Cercana" icon="lucide:map-pin" accent="blue">
          <template v-if="tareaMasCercana && tareaMasCercana.titulo">
            <p class="metric-main">{{ tareaMasCercana.titulo }}</p>
            <p class="metric-sub">Sector: <strong>{{ tareaMasCercana.sectorNombre }}</strong></p>
            <p class="metric-distance">
              <span class="distance-badge"><Icon icon="lucide:ruler" class="icon" /> {{ formatMetros(tareaMasCercana.distanciaMetros) }}</span>
            </p>
          </template>
          <p v-else class="metric-empty">Sin tareas pendientes</p>
        </StatCard>

        <!-- Q4/Q8: Promedio distancia -->
        <StatCard
          label="Promedio de Distancia"
          sublabel="Tareas completadas respecto a tu ubicación"
          icon="lucide:ruler"
          accent="green"
        >
          <p class="metric-big">{{ formatMetros(promedioDistancia) }}</p>
        </StatCard>

        <!-- Q3: Sector 2km -->
        <StatCard
          label="Sector Más Activo (2 km)"
          sublabel="Con más tareas completadas cerca de ti"
          icon="lucide:circle-dot"
          accent="purple"
        >
          <template v-if="sectorRadio2km && sectorRadio2km.sector">
            <p class="metric-main">{{ sectorRadio2km.sector }}</p>
            <p class="metric-sub">{{ sectorRadio2km.total }} tarea(s) completada(s)</p>
          </template>
          <p v-else class="metric-empty">Sin datos en 2 km</p>
        </StatCard>

        <!-- Q7: Sector 5km -->
        <StatCard
          label="Sector Más Activo (5 km)"
          sublabel="Con más tareas completadas cerca de ti"
          icon="lucide:circle-dot"
          accent="orange"
        >
          <template v-if="sectorRadio5km && sectorRadio5km.sector">
            <p class="metric-main">{{ sectorRadio5km.sector }}</p>
            <p class="metric-sub">{{ sectorRadio5km.total }} tarea(s) completada(s)</p>
          </template>
          <p v-else class="metric-empty">Sin datos en 5 km</p>
        </StatCard>
      </div>

      <!-- ===== FILA 2: Q1 — usando BarChart (src/components/BarChart.vue) ===== -->
      <div class="chart-card">
        <div class="card-header">
          <div>
            <h2><Icon icon="lucide:bar-chart-2" class="icon" /> Mis Tareas por Sector</h2>
            <p class="card-desc">¿Cuántas tareas has realizado en cada sector geográfico?</p>
          </div>
          <span class="badge badge-blue">{{ tareasPorSector.length }} sector(es)</span>
        </div>
        <!-- BarChart: componente reutilizable en src/components/BarChart.vue -->
        <BarChart
          :items="tareasPorSector.map((i: any) => ({ label: i.sector, value: Number(i.total) }))"
          color="blue"
          emptyMessage="Aún no tienes tareas registradas en ningún sector."
        />
      </div>

      <!-- ===== FILA 3: Q5 — usando BarChart (src/components/BarChart.vue) ===== -->
      <div class="chart-card">
        <div class="card-header">
          <div>
            <h2><Icon icon="lucide:hourglass" class="icon" /> Concentración de Tareas Pendientes</h2>
            <p class="card-desc">
              Agrupación espacial (DBSCAN) de todos los sectores con tareas pendientes.
              Los sectores cercanos entre sí se agrupan en un mismo "grupo geográfico".
            </p>
          </div>
          <span class="badge badge-orange">Agrupación espacial</span>
        </div>
        <!-- BarChart: componente reutilizable en src/components/BarChart.vue -->
        <BarChart
          :items="sectoresPendientes.map((i: any) => ({ label: i.sector, value: Number(i.total) }))"
          color="orange"
          emptyMessage="No hay tareas pendientes registradas."
        />
      </div>

      <!-- ===== FILA 4: Q6 Tareas por usuario y sector (tabla cruzada) ===== -->
      <div class="chart-card">
        <div class="card-header">
          <div>
            <h2><Icon icon="lucide:users" class="icon" /> Actividad Global por Usuario y Sector</h2>
            <p class="card-desc">¿Cuántas tareas ha realizado cada usuario en cada sector geográfico? (todos los usuarios del sistema)</p>
          </div>
          <span class="badge badge-green">Vista global</span>
        </div>

        <div v-if="tareasPorUsuario.length > 0" class="table-scroll">
          <table class="cross-table">
            <thead>
              <tr>
                <th class="th-user">Usuario</th>
                <th v-for="sector in sectoresUnicos" :key="sector">{{ sector }}</th>
                <th class="th-total">Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="usuario in usuariosUnicos" :key="usuario">
                <td class="td-user">
                  <span class="user-chip">{{ (usuario as string).charAt(0).toUpperCase() }}</span>
                  {{ usuario }}
                </td>
                <td
                  v-for="sector in sectoresUnicos"
                  :key="sector"
                  :class="{ 'td-zero': getCellValue(usuario as string, sector as string) === 0 }"
                >
                  <span v-if="getCellValue(usuario as string, sector as string) > 0" class="cell-badge">
                    {{ getCellValue(usuario as string, sector as string) }}
                  </span>
                  <span v-else class="cell-empty">—</span>
                </td>
                <td class="td-total">
                  {{
                    (sectoresUnicos as string[]).reduce((acc, s) => acc + Number(getCellValue(usuario as string, s)), 0)
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <p><Icon icon="lucide:clipboard-list" class="icon" /> No hay datos disponibles aún.</p>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* ===== PÁGINA ===== */
.estadisticas-page {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

.page-header {
  margin-bottom: 2rem;
}

.page-header h1 {
  font-size: 1.7rem;
  color: #2c3e50;
  margin: 0 0 0.3rem 0;
}

.subtitle {
  color: #7f8c8d;
  font-size: 0.9rem;
  margin: 0;
}

/* ===== LOADING ===== */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4rem 0;
  color: #7f8c8d;
  gap: 1rem;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e0e0e0;
  border-top-color: #3498db;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.warn-banner {
  background: #fef9e7;
  border: 1px solid #f39c12;
  color: #856404;
  padding: 0.8rem 1.2rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
  font-size: 0.88rem;
}
/* ===== LAYOUT ===== */
.stats-layout {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* ===== FILA DE MÉTRICAS ===== */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
}

/* Estilos de contenido interno de StatCard */
.metric-main {
  font-size: 1.05rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 0 0 0.25rem 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-sub {
  font-size: 0.82rem;
  color: #555;
  margin: 0 0 0.4rem 0;
}

.metric-big {
  font-size: 1.9rem;
  font-weight: 800;
  color: #2ecc71;
  margin: 0.4rem 0 0 0;
  line-height: 1;
}

.metric-empty {
  font-size: 0.85rem;
  color: #bdc3c7;
  margin: 0.5rem 0 0 0;
}

.distance-badge {
  display: inline-block;
  background: #eaf4fe;
  color: #2980b9;
  padding: 0.25rem 0.65rem;
  border-radius: 20px;
  font-size: 0.82rem;
  font-weight: 700;
}

/* ===== CARDS DE GRÁFICO ===== */
.chart-card {
  background: white;
  border-radius: 12px;
  padding: 1.6rem;
  box-shadow: 0 2px 12px rgba(0,0,0,0.07);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1.4rem;
  flex-wrap: wrap;
}

.card-header h2 {
  font-size: 1.05rem;
  color: #2c3e50;
  margin: 0 0 0.25rem 0;
  font-weight: 700;
}

.card-desc {
  font-size: 0.8rem;
  color: #7f8c8d;
  margin: 0;
  line-height: 1.4;
  max-width: 600px;
}

.badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.badge-blue   { background: #eaf4fe; color: #2980b9; }
.badge-orange { background: #fef5e7; color: #e67e22; }
.badge-green  { background: #eafaf1; color: #27ae60; }

/* ===== BARRAS ===== */
.bar-chart {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.bar-row {
  display: grid;
  grid-template-columns: 200px 1fr 40px;
  align-items: center;
  gap: 0.75rem;
}

.bar-label {
  font-size: 0.85rem;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-track {
  background: #f0f2f5;
  border-radius: 6px;
  height: 22px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.6s ease;
  min-width: 4px;
}

.bar-fill--blue   { background: linear-gradient(90deg, #3498db, #5dade2); }
.bar-fill--orange { background: linear-gradient(90deg, #e67e22, #f0a04b); }

.bar-count {
  font-size: 0.85rem;
  font-weight: 700;
  color: #2c3e50;
  text-align: right;
}

/* ===== TABLA CRUZADA Q6 ===== */
.table-scroll {
  overflow-x: auto;
}

.cross-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  min-width: 400px;
}

.cross-table th {
  background: #f8f9fa;
  padding: 0.6rem 0.9rem;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 700;
  color: #555;
  border-bottom: 2px solid #e8ecef;
  white-space: nowrap;
}

.th-user  { text-align: left; }
.th-total { background: #eaf4fe; color: #2980b9; }

.cross-table td {
  padding: 0.55rem 0.9rem;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
}

.cross-table tr:last-child td {
  border-bottom: none;
}

.td-user {
  text-align: left;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: #2c3e50;
}

.user-chip {
  width: 26px;
  height: 26px;
  background: linear-gradient(135deg, #3498db, #2980b9);
  color: white;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  flex-shrink: 0;
}

.cell-badge {
  display: inline-block;
  background: #eaf4fe;
  color: #2980b9;
  padding: 0.2rem 0.55rem;
  border-radius: 10px;
  font-weight: 700;
  font-size: 0.82rem;
}

.cell-empty {
  color: #d5d8dc;
}

.td-total {
  font-weight: 800;
  color: #2c3e50;
  background: #f8f9fa;
}

/* ===== EMPTY STATE ===== */
.empty-state {
  text-align: center;
  padding: 2rem;
  color: #95a5a6;
  font-size: 0.9rem;
}

/* ===== ICONIFY ICONS ===== */
.icon {
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
  flex-shrink: 0;
  display: inline-block;
}

/* ===== RESPONSIVE ===== */
@media (max-width: 768px) {
  .metrics-row {
    grid-template-columns: 1fr;
  }
  .bar-row {
    grid-template-columns: 140px 1fr 36px;
  }
}
</style>
