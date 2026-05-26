<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Icon } from '@iconify/vue'
import { sectoresApi, tareasApi } from '@/services/api'

interface Sector {
  id: number
  nombre: string
  latitud: number
  longitud: number
}

interface SectorStats {
  sector: string
  total: number | string
}

const sectores = ref<Sector[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const successMsg = ref('')
const showModal = ref(false)
const confirmDeleteId = ref<number | null>(null)
const editingSector = ref<Sector | null>(null)
const tareasPorSector = ref<SectorStats[]>([])
const busqueda = ref('')

const errors = ref({
  nombre: '',
  latitud: '',
  longitud: '',
})

const form = ref({
  nombre: '',
  latitud: null as number | null,
  longitud: null as number | null,
})

const sectoresFiltrados = computed(() => {
  const q = busqueda.value.trim().toLowerCase()
  if (!q) return sectores.value
  return sectores.value.filter((s) => s.nombre.toLowerCase().includes(q))
})

async function cargarSectores() {
  loading.value = true
  error.value = ''
  try {
    const res = await sectoresApi.getAll()
    sectores.value = res.data
  } catch {
    error.value = 'Error al cargar sectores'
  } finally {
    loading.value = false
  }
}

async function cargarStats() {
  try {
    const res = await tareasApi.estadisticas.porSector()
    tareasPorSector.value = res.data
  } catch {
    tareasPorSector.value = []
  }
}

onMounted(() => {
  cargarSectores()
  cargarStats()
})

function clearErrors() {
  error.value = ''
  errors.value = {
    nombre: '',
    latitud: '',
    longitud: '',
  }
}

function openCreate() {
  editingSector.value = null
  form.value = { nombre: '', latitud: null, longitud: null }
  clearErrors()
  showModal.value = true
}

function openEdit(sector: Sector) {
  editingSector.value = sector
  form.value = {
    nombre: sector.nombre,
    latitud: sector.latitud,
    longitud: sector.longitud,
  }
  clearErrors()
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  clearErrors()
}

function useMyLocation() {
  if (!navigator.geolocation) {
    error.value = 'Tu navegador no soporta geolocalización'
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      form.value.latitud = parseFloat(pos.coords.latitude.toFixed(6))
      form.value.longitud = parseFloat(pos.coords.longitude.toFixed(6))
      errors.value.latitud = ''
      errors.value.longitud = ''
    },
    () => {
      error.value = 'No se pudo obtener la ubicación'
    },
  )
}

function showSuccess(msg: string) {
  successMsg.value = msg
  setTimeout(() => (successMsg.value = ''), 3000)
}

async function guardarSector() {
  clearErrors()
  let hasErrors = false

  if (!form.value.nombre.trim()) {
    errors.value.nombre = 'El nombre del sector es obligatorio'
    hasErrors = true
  }
  if (form.value.latitud === null || isNaN(Number(form.value.latitud))) {
    errors.value.latitud = 'La latitud es obligatoria y debe ser un número'
    hasErrors = true
  }
  if (form.value.longitud === null || isNaN(Number(form.value.longitud))) {
    errors.value.longitud = 'La longitud es obligatoria y debe ser un número'
    hasErrors = true
  }

  if (hasErrors) {
    return
  }

  saving.value = true
  try {
    const payload = {
      nombre: form.value.nombre.trim(),
      latitud: form.value.latitud,
      longitud: form.value.longitud,
    }
    if (editingSector.value) {
      await sectoresApi.update(editingSector.value.id, payload)
      showSuccess('Sector actualizado correctamente')
    } else {
      await sectoresApi.create(payload)
      showSuccess('Sector creado correctamente')
    }
    showModal.value = false
    await cargarSectores()
    await cargarStats()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Error al guardar el sector'
  } finally {
    saving.value = false
  }
}

function pedirConfirmacion(id: number) {
  confirmDeleteId.value = id
}

async function confirmarEliminar() {
  if (confirmDeleteId.value === null) return
  saving.value = true
  try {
    await sectoresApi.delete(confirmDeleteId.value)
    showSuccess('Sector eliminado correctamente')
    confirmDeleteId.value = null
    await cargarSectores()
    await cargarStats()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'No se pudo eliminar. El sector puede tener tareas asociadas.'
    confirmDeleteId.value = null
  } finally {
    saving.value = false
  }
}

function getTareasCount(nombreSector: string): number | string {
  const found = tareasPorSector.value.find(
    (s) => s.sector.toLowerCase() === nombreSector.toLowerCase(),
  )
  return found ? found.total : 0
}

function openGoogleMaps(lat: number, lng: number) {
  window.open(`https://www.google.com/maps?q=${lat},${lng}`, '_blank')
}
</script>

<template>
  <div class="sectores-page">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1> Gestión de Sectores</h1>
        <p class="subtitle">Administra los sectores geográficos del sistema</p>
      </div>
      <button @click="openCreate" class="btn-primary" id="btn-nuevo-sector">
        <span class="btn-icon">+</span> Nuevo Sector
      </button>
    </div>

    <!-- Success Banner -->
    <transition name="fade">
      <div v-if="successMsg" class="success-banner">
        {{ successMsg }}
      </div>
    </transition>

    <!-- Error global -->
    <div v-if="error && !showModal && confirmDeleteId === null" class="error-banner">
       {{ error }}
      <button @click="error = ''" class="close-btn">✕</button>
    </div>

    <!-- Buscador y contador -->
    <div class="toolbar">
      <div class="search-wrapper">
        <input
          v-model="busqueda"
          type="text"
          placeholder="Buscar sector por nombre..."
          class="search-input"
          id="input-buscar-sector"
        />
      </div>
      <div class="count-badge">
        {{ sectoresFiltrados.length }} sector{{ sectoresFiltrados.length !== 1 ? 'es' : '' }}
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>Cargando sectores...</p>
    </div>

    <!-- Empty State -->
    <div v-else-if="sectoresFiltrados.length === 0 && !loading" class="empty-state">
      <div class="empty-icon"><Icon icon="lucide:map" class="icon" /></div>
      <p class="empty-title">
        {{ busqueda ? 'No se encontraron sectores' : 'No hay sectores registrados' }}
      </p>
      <p class="empty-desc">
        {{ busqueda ? 'Intenta con otro término de búsqueda' : 'Crea el primer sector para comenzar' }}
      </p>
      <button v-if="!busqueda" @click="openCreate" class="btn-primary">+ Crear Primer Sector</button>
    </div>

    <!-- Grid de Sectores -->
    <div v-else class="sectores-grid">
      <div
        v-for="sector in sectoresFiltrados"
        :key="sector.id"
        class="sector-card"
        :id="`sector-card-${sector.id}`"
      >
        <!-- Badge de tipo -->
        <div class="sector-badge"> Sector</div>

        <!-- Nombre -->
        <h3 class="sector-nombre">{{ sector.nombre }}</h3>

        <!-- Coordenadas -->
        <div class="sector-coords">
          <div class="coord-item">
            <span class="coord-label">Latitud</span>
            <span class="coord-val">{{ sector.latitud?.toFixed(5) ?? '—' }}</span>
          </div>
          <div class="coord-sep">|</div>
          <div class="coord-item">
            <span class="coord-label">Longitud</span>
            <span class="coord-val">{{ sector.longitud?.toFixed(5) ?? '—' }}</span>
          </div>
        </div>

        <!-- Tareas asociadas -->
        <div class="sector-stats">
          <div class="stat-item">
            <span class="stat-num">{{ getTareasCount(sector.nombre) }}</span>
            <span class="stat-lbl">tareas tuyas</span>
          </div>
        </div>

        <!-- Acciones -->
        <div class="sector-actions">
          <button
            @click="openGoogleMaps(sector.latitud, sector.longitud)"
            class="btn-maps"
            title="Ver en Google Maps"
          >
            Ver mapa
          </button>
          <button
            @click="openEdit(sector)"
            class="btn-edit"
            :id="`btn-edit-sector-${sector.id}`"
          >
             Editar
          </button>
          <button
            @click="pedirConfirmacion(sector.id)"
            class="btn-delete"
            :id="`btn-delete-sector-${sector.id}`"
          >
            Eliminar
          </button>
        </div>
      </div>
    </div>

    <!-- ===== MODAL CREAR / EDITAR ===== -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h3>{{ editingSector ? ' Editar Sector' : '' }}<template v-if="!editingSector"><Icon icon="lucide:plus" class="icon" /> Nuevo Sector</template></h3>
          <button @click="closeModal" class="modal-close"><Icon icon="lucide:x" class="icon" /></button>
        </div>

        <div class="modal-body">
          <!-- Nombre -->
          <div class="form-group">
            <label for="input-nombre-sector">Nombre del Sector *</label>
            <input
              id="input-nombre-sector"
              v-model="form.nombre"
              type="text"
              placeholder="Ej: Construcción Centro, Reparación Semáforos..."
              maxlength="100"
              :class="{ 'input-error': errors.nombre }"
              @input="errors.nombre = ''"
            />
            <span v-if="errors.nombre" class="field-error">{{ errors.nombre }}</span>
            <span class="char-count">{{ form.nombre.length }}/100</span>
          </div>

          <!-- Ubicación -->
          <div class="form-group">
            <label>Ubicación Geoespacial *</label>
            <button type="button" class="btn-geo" @click="useMyLocation">
              <Icon icon="lucide:map-pin" class="icon" /> Usar mi ubicación actual
            </button>
            <div class="coords-grid">
              <div>
                <label for="input-lat" class="sub-label">Latitud</label>
                <input
                  id="input-lat"
                  v-model.number="form.latitud"
                  type="number"
                  step="any"
                  placeholder="-33.45694"
                  :class="{ 'input-error': errors.latitud }"
                  @input="errors.latitud = ''"
                />
                <span v-if="errors.latitud" class="field-error">{{ errors.latitud }}</span>
              </div>
              <div>
                <label for="input-lng" class="sub-label">Longitud</label>
                <input
                  id="input-lng"
                  v-model.number="form.longitud"
                  type="number"
                  step="any"
                  placeholder="-70.64827"
                  :class="{ 'input-error': errors.longitud }"
                  @input="errors.longitud = ''"
                />
                <span v-if="errors.longitud" class="field-error">{{ errors.longitud }}</span>
              </div>
            </div>
            <p v-if="form.latitud && form.longitud" class="coords-preview">
              <Icon icon="lucide:map-pin" class="icon" /> {{ form.latitud }}, {{ form.longitud }}
              <a
                :href="`https://www.google.com/maps?q=${form.latitud},${form.longitud}`"
                target="_blank"
                class="preview-link"
              >Ver en mapa ↗</a>
            </p>
            <p class="coords-hint">
              <Icon icon="lucide:lightbulb" class="icon" /> Ingresa las coordenadas manualmente o usa el botón para detectar tu posición
            </p>
          </div>

          <p v-if="error" class="error-msg"><Icon icon="lucide:triangle-alert" class="icon" /> {{ error }}</p>
        </div>

        <div class="modal-footer">
          <button @click="closeModal" class="btn-cancel">Cancelar</button>
          <button
            @click="guardarSector"
            :disabled="saving"
            class="btn-primary"
            id="btn-guardar-sector"
          >
            {{ saving ? 'Guardando...' : editingSector ? 'Actualizar' : 'Crear Sector' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ===== MODAL CONFIRMAR ELIMINAR ===== -->
    <div v-if="confirmDeleteId !== null" class="modal-overlay" @click.self="confirmDeleteId = null">
      <div class="modal modal-confirm" role="dialog">
        <div class="confirm-icon"><Icon icon="lucide:trash-2" class="icon" style="font-size:2.5rem" /></div>
        <h3>¿Eliminar sector?</h3>
        <p class="confirm-desc">
          Esta acción no se puede deshacer. Si el sector tiene tareas asociadas, la eliminación
          fallará.
        </p>
        <p v-if="error" class="error-msg"><Icon icon="lucide:triangle-alert" class="icon" /> {{ error }}</p>
        <div class="modal-footer">
          <button @click="confirmDeleteId = null" class="btn-cancel">Cancelar</button>
          <button
            @click="confirmarEliminar"
            :disabled="saving"
            class="btn-delete-confirm"
            id="btn-confirmar-eliminar"
          >
            {{ saving ? 'Eliminando...' : 'Sí, eliminar' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== Layout ===== */
.sectores-page {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.header-left h1 {
  font-size: 1.7rem;
  color: #2c3e50;
  margin: 0 0 0.2rem 0;
}

.subtitle {
  color: #7f8c8d;
  font-size: 0.9rem;
  margin: 0;
}

/* ===== Botones principales ===== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  background: linear-gradient(135deg, #2c3e50, #3498db);
  color: white;
  border: none;
  padding: 0.65rem 1.3rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 600;
  transition: opacity 0.2s, transform 0.15s;
  box-shadow: 0 3px 10px rgba(52, 152, 219, 0.3);
}
.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}
.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}
.btn-icon {
  font-size: 1.1rem;
  font-weight: 400;
}

/* ===== Banners ===== */
.success-banner {
  background: linear-gradient(135deg, #d5f5e3, #a9dfbf);
  border: 1px solid #27ae60;
  color: #1e8449;
  padding: 0.8rem 1.2rem;
  border-radius: 8px;
  margin-bottom: 1.2rem;
  font-weight: 500;
}

.error-banner {
  background: #fdecea;
  border: 1px solid #e74c3c;
  color: #c0392b;
  padding: 0.8rem 1.2rem;
  border-radius: 8px;
  margin-bottom: 1.2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  color: #c0392b;
  padding: 0;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.4s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== Toolbar ===== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}

.search-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: white;
  border: 1px solid #dde1e7;
  border-radius: 8px;
  padding: 0.5rem 0.9rem;
  flex: 1;
  min-width: 220px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  transition: border-color 0.2s;
}
.search-wrapper:focus-within {
  border-color: #3498db;
}
.search-icon { font-size: 0.95rem; color: #aaa; }
.search-input {
  border: none;
  outline: none;
  font-size: 0.95rem;
  width: 100%;
  background: transparent;
  color: #333;
}

.count-badge {
  background: #eaf2ff;
  color: #2980b9;
  font-size: 0.85rem;
  font-weight: 600;
  padding: 0.4rem 0.9rem;
  border-radius: 20px;
  white-space: nowrap;
}

/* ===== Estados vacío / carga ===== */
.loading-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #7f8c8d;
}
.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e0e0e0;
  border-top-color: #3498db;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 1rem;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.empty-icon { font-size: 3rem; margin-bottom: 0.8rem; }
.empty-title { font-size: 1.1rem; font-weight: 600; color: #2c3e50; margin-bottom: 0.4rem; }
.empty-desc { color: #95a5a6; font-size: 0.9rem; margin-bottom: 1.5rem; }

/* ===== Grid de tarjetas ===== */
.sectores-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.2rem;
}

.sector-card {
  background: white;
  border-radius: 12px;
  padding: 1.4rem;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.07);
  border-top: 4px solid #3498db;
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}
.sector-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(52, 152, 219, 0.18);
}

.sector-badge {
  display: inline-block;
  background: #eaf2ff;
  color: #2980b9;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.7rem;
  border-radius: 20px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  width: fit-content;
}

.sector-nombre {
  font-size: 1.1rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 0;
  line-height: 1.3;
}

.sector-coords {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  background: #f8f9fa;
  padding: 0.6rem 0.9rem;
  border-radius: 8px;
  font-size: 0.82rem;
}
.coord-item {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.coord-label { color: #95a5a6; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.4px; }
.coord-val { color: #2c3e50; font-weight: 600; font-family: 'Courier New', monospace; }
.coord-sep { color: #ddd; }

.sector-stats {
  display: flex;
  gap: 1rem;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #f0f8ff;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  flex: 1;
}
.stat-num {
  font-size: 1.4rem;
  font-weight: 800;
  color: #3498db;
  line-height: 1;
}
.stat-lbl {
  font-size: 0.72rem;
  color: #7f8c8d;
  margin-top: 0.2rem;
}

.sector-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: auto;
}

.btn-maps {
  background: #f0f3f4;
  color: #555;
  border: 1px solid #ddd;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.82rem;
  transition: background 0.2s;
}
.btn-maps:hover { background: #e8ecee; }

.btn-edit {
  background: #eaf2ff;
  color: #2980b9;
  border: 1px solid #aed6f1;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 600;
  transition: background 0.2s;
}
.btn-edit:hover { background: #d6eaf8; }

.btn-delete {
  background: #fdf2f2;
  color: #e74c3c;
  border: 1px solid #f5a9a9;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 600;
  transition: background 0.2s;
}
.btn-delete:hover { background: #fadbd8; }

/* ===== Modal ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 200;
  padding: 1rem;
  animation: overlayIn 0.2s ease;
}
@keyframes overlayIn { from { opacity: 0; } to { opacity: 1; } }

.modal {
  background: white;
  border-radius: 14px;
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0,0,0,0.25);
  animation: modalIn 0.25s ease;
}
@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95) translateY(10px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.4rem 1.6rem 0;
}
.modal-header h3 {
  font-size: 1.15rem;
  color: #2c3e50;
  margin: 0;
}
.modal-close {
  background: #f0f0f0;
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 0.85rem;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal-close:hover { background: #e0e0e0; }

.modal-body {
  padding: 1.2rem 1.6rem;
}

.form-group {
  margin-bottom: 1.2rem;
}
.form-group > label {
  display: block;
  font-size: 0.85rem;
  font-weight: 700;
  color: #555;
  margin-bottom: 0.4rem;
}
.sub-label {
  display: block;
  font-size: 0.78rem;
  color: #7f8c8d;
  margin-bottom: 0.25rem;
}
.form-group input[type='text'],
.form-group input[type='number'] {
  width: 100%;
  padding: 0.6rem 0.9rem;
  border: 1.5px solid #dde1e7;
  border-radius: 8px;
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.form-group input:focus {
  border-color: #3498db;
}
.char-count {
  font-size: 0.75rem;
  color: #aaa;
  display: block;
  text-align: right;
  margin-top: 0.2rem;
}

.btn-geo {
  width: 100%;
  background: linear-gradient(135deg, #27ae60, #2ecc71);
  color: white;
  border: none;
  padding: 0.55rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 0.8rem;
  transition: opacity 0.2s;
}
.btn-geo:hover { opacity: 0.9; }

.coords-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.7rem;
}

.coords-preview {
  font-size: 0.82rem;
  color: #27ae60;
  margin-top: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}
.preview-link {
  color: #3498db;
  text-decoration: none;
  font-weight: 600;
}
.preview-link:hover { text-decoration: underline; }

.coords-hint {
  font-size: 0.78rem;
  color: #bdc3c7;
  margin-top: 0.5rem;
}

.error-msg {
  color: #e74c3c;
  font-size: 0.85rem;
  background: #fdf2f2;
  padding: 0.5rem 0.8rem;
  border-radius: 6px;
  border-left: 3px solid #e74c3c;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.8rem;
  padding: 1rem 1.6rem 1.4rem;
  border-top: 1px solid #f0f0f0;
}

.btn-cancel {
  background: #f0f3f4;
  color: #555;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background 0.2s;
}
.btn-cancel:hover { background: #e0e6e8; }

/* ===== Modal Confirmar Eliminar ===== */
.modal-confirm {
  max-width: 380px;
  text-align: center;
  padding: 2rem 1.8rem;
}
.confirm-icon {
  font-size: 2.5rem;
  margin-bottom: 0.8rem;
}
.modal-confirm h3 {
  color: #2c3e50;
  font-size: 1.2rem;
  margin-bottom: 0.5rem;
}
.confirm-desc {
  color: #7f8c8d;
  font-size: 0.88rem;
  line-height: 1.5;
  margin-bottom: 1rem;
}
.btn-delete-confirm {
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: white;
  border: none;
  padding: 0.6rem 1.4rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  transition: opacity 0.2s;
}
.btn-delete-confirm:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-delete-confirm:hover:not(:disabled) { opacity: 0.9; }
.input-error {
  border-color: #e74c3c !important;
  background-color: #fdf2f2 !important;
  box-shadow: 0 0 5px rgba(231, 76, 60, 0.2) !important;
}
.field-error {
  color: #e74c3c;
  font-size: 0.8rem;
  margin-top: 0.25rem;
  display: block;
}

.icon {
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
  flex-shrink: 0;
  display: inline-block;
}
</style>
