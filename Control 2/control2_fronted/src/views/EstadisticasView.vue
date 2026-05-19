<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { tareasApi } from '@/services/api'

const loading = ref(false)
const error = ref('')

const tareasPorSector = ref<any[]>([])
const tareaMasCercana = ref<any>(null)
const sectorRadio2km = ref<any>(null)
const promedioDistancia = ref<number | null>(null)
const sectoresPendientes = ref<any[]>([])
const tareasPorUsuario = ref<any[]>([])
const sectorRadio5km = ref<any>(null)

onMounted(async () => {
  loading.value = true
  try {
    const [r1, r2, r3, r4, r5, r6, r7] = await Promise.all([
      tareasApi.estadisticas.porSector(),
      tareasApi.estadisticas.masCercana(),
      tareasApi.estadisticas.sectorRadio2km(),
      tareasApi.estadisticas.promedioDistancia(),
      tareasApi.estadisticas.sectoresPendientes(),
      tareasApi.estadisticas.porUsuarioSector(),
      tareasApi.estadisticas.sectorRadio5km(),
    ])
    tareasPorSector.value = r1.data
    tareaMasCercana.value = r2.data
    sectorRadio2km.value = r3.data
    promedioDistancia.value = r4.data?.promedioMetros
    sectoresPendientes.value = r5.data
    tareasPorUsuario.value = r6.data
    sectorRadio5km.value = r7.data
  } catch {
    error.value = 'Error al cargar estadísticas'
  } finally {
    loading.value = false
  }
})

function formatMetros(metros: number | null) {
  if (!metros) return 'Sin datos'
  if (metros < 1000) return `${metros.toFixed(0)} m`
  return `${(metros / 1000).toFixed(2)} km`
}
</script>

<template>
  <div>
    <h1>Estadísticas Geoespaciales</h1>
    <p class="subtitle">Consultas espaciales con PostGIS sobre tus tareas</p>

    <p v-if="error" class="error-msg">{{ error }}</p>
    <p v-if="loading" class="loading-msg">Calculando estadísticas...</p>

    <div v-if="!loading" class="stats-grid">
      <!-- Q1: Tareas por sector del usuario -->
      <div class="stat-card">
        <h3>📊 Mis Tareas por Sector</h3>
        <p class="stat-desc">¿Cuántas tareas has hecho en cada sector?</p>
        <div v-if="tareasPorSector.length > 0">
          <div v-for="item in tareasPorSector" :key="item.sector" class="stat-row">
            <span class="stat-label">{{ item.sector }}</span>
            <span class="stat-value">{{ item.total }}</span>
          </div>
        </div>
        <p v-else class="no-data">Sin datos</p>
      </div>

      <!-- Q2: Tarea más cercana pendiente -->
      <div class="stat-card">
        <h3>📍 Tarea Más Cercana</h3>
        <p class="stat-desc">La tarea pendiente más cercana a tu ubicación</p>
        <div v-if="tareaMasCercana && tareaMasCercana.titulo">
          <p class="highlight">{{ tareaMasCercana.titulo }}</p>
          <p>Sector: <strong>{{ tareaMasCercana.sectorNombre }}</strong></p>
          <p>Distancia: <strong>{{ formatMetros(tareaMasCercana.distanciaMetros) }}</strong></p>
        </div>
        <p v-else class="no-data">Sin tareas pendientes</p>
      </div>

      <!-- Q3: Sector con más completadas en 2km -->
      <div class="stat-card">
        <h3>🔵 Sector Más Activo (2 km)</h3>
        <p class="stat-desc">Sector con más tareas completadas en un radio de 2 km</p>
        <div v-if="sectorRadio2km && sectorRadio2km.sector">
          <p class="highlight">{{ sectorRadio2km.sector }}</p>
          <p>Tareas completadas: <strong>{{ sectorRadio2km.total }}</strong></p>
        </div>
        <p v-else class="no-data">Sin datos en 2 km</p>
      </div>

      <!-- Q4 & Q8: Promedio de distancia -->
      <div class="stat-card">
        <h3>📏 Promedio de Distancia</h3>
        <p class="stat-desc">Distancia promedio de tus tareas completadas</p>
        <p class="highlight-big">{{ formatMetros(promedioDistancia) }}</p>
      </div>

      <!-- Q5: Sectores con más pendientes -->
      <div class="stat-card wide">
        <h3>⏳ Concentración de Tareas Pendientes</h3>
        <p class="stat-desc">¿En qué sectores se concentran más las tareas pendientes? (agrupación espacial)</p>
        <div v-if="sectoresPendientes.length > 0">
          <div v-for="item in sectoresPendientes" :key="item.sector" class="stat-row">
            <span class="stat-label">{{ item.sector }}</span>
            <div class="bar-container">
              <div
                class="bar"
                :style="{
                  width: `${(item.total / sectoresPendientes[0].total) * 100}%`,
                }"
              ></div>
              <span class="bar-value">{{ item.total }}</span>
            </div>
          </div>
        </div>
        <p v-else class="no-data">Sin tareas pendientes</p>
      </div>

      <!-- Q6: Tareas por usuario y sector -->
      <div class="stat-card wide">
        <h3>👥 Tareas por Usuario y Sector</h3>
        <p class="stat-desc">Resumen global de actividad por usuario</p>
        <table v-if="tareasPorUsuario.length > 0" class="stat-table">
          <thead>
            <tr>
              <th>Usuario</th>
              <th>Sector</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, i) in tareasPorUsuario" :key="i">
              <td>{{ item.usuario }}</td>
              <td>{{ item.sector }}</td>
              <td>{{ item.total }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="no-data">Sin datos</p>
      </div>

      <!-- Q7: Sector con más completadas en 5km -->
      <div class="stat-card">
        <h3>🟢 Sector Más Activo (5 km)</h3>
        <p class="stat-desc">Sector con más tareas completadas en un radio de 5 km</p>
        <div v-if="sectorRadio5km && sectorRadio5km.sector">
          <p class="highlight">{{ sectorRadio5km.sector }}</p>
          <p>Tareas completadas: <strong>{{ sectorRadio5km.total }}</strong></p>
        </div>
        <p v-else class="no-data">Sin datos en 5 km</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
h1 {
  color: #2c3e50;
  margin-bottom: 0.3rem;
}
.subtitle {
  color: #7f8c8d;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.2rem;
}
.stat-card {
  background: white;
  border-radius: 10px;
  padding: 1.4rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.07);
}
.stat-card.wide {
  grid-column: 1 / -1;
}
.stat-card h3 {
  font-size: 1rem;
  color: #2c3e50;
  margin-bottom: 0.3rem;
}
.stat-desc {
  font-size: 0.8rem;
  color: #95a5a6;
  margin-bottom: 1rem;
}
.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.4rem 0;
  border-bottom: 1px solid #f5f6fa;
}
.stat-label {
  font-size: 0.9rem;
  color: #555;
}
.stat-value {
  font-weight: 700;
  color: #2c3e50;
  background: #eaf2ff;
  padding: 0.2rem 0.6rem;
  border-radius: 12px;
  font-size: 0.85rem;
}
.highlight {
  font-size: 1.2rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 0.4rem;
}
.highlight-big {
  font-size: 2rem;
  font-weight: 700;
  color: #3498db;
  text-align: center;
  padding: 1rem 0;
}
.no-data {
  color: #bdc3c7;
  font-size: 0.9rem;
  text-align: center;
  padding: 1rem 0;
}
.bar-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  margin-left: 1rem;
}
.bar {
  height: 20px;
  background: #3498db;
  border-radius: 4px;
  transition: width 0.4s ease;
  min-width: 4px;
}
.bar-value {
  font-size: 0.85rem;
  font-weight: 600;
  color: #2c3e50;
}
.stat-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.stat-table th {
  background: #f5f6fa;
  padding: 0.6rem 0.8rem;
  text-align: left;
  font-size: 0.85rem;
  color: #666;
}
.stat-table td {
  padding: 0.55rem 0.8rem;
  border-bottom: 1px solid #f0f0f0;
}
.stat-table tr:last-child td {
  border-bottom: none;
}
.error-msg {
  color: #e74c3c;
  font-size: 0.9rem;
}
.loading-msg {
  text-align: center;
  color: #888;
  padding: 3rem;
}
</style>
