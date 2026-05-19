<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { tareasApi, sectoresApi } from '@/services/api'

interface Tarea {
  id: number
  titulo: string
  descripcion: string
  fechaVencimiento: string
  estadoCompletada: boolean
  sectorId: number
  sectorNombre: string
}

interface Sector {
  id: number
  nombre: string
  latitud: number
  longitud: number
}

const tareas = ref<Tarea[]>([])
const sectores = ref<Sector[]>([])
const loading = ref(false)
const error = ref('')

const filtroEstado = ref<string>('all')
const busqueda = ref('')
const showModal = ref(false)
const editingTarea = ref<Tarea | null>(null)

const form = ref({
  titulo: '',
  descripcion: '',
  fechaVencimiento: '',
  sectorId: null as number | null,
})

const tareasFiltradas = computed(() => {
  let lista = tareas.value
  if (filtroEstado.value === 'pendiente') lista = lista.filter((t) => !t.estadoCompletada)
  if (filtroEstado.value === 'completada') lista = lista.filter((t) => t.estadoCompletada)
  if (busqueda.value.trim()) {
    const kw = busqueda.value.toLowerCase()
    lista = lista.filter(
      (t) => t.titulo.toLowerCase().includes(kw) || t.descripcion?.toLowerCase().includes(kw),
    )
  }
  return lista
})

async function cargarTareas() {
  loading.value = true
  try {
    const res = await tareasApi.getAll()
    tareas.value = res.data
  } catch {
    error.value = 'Error al cargar tareas'
  } finally {
    loading.value = false
  }
}

async function cargarSectores() {
  const res = await sectoresApi.getAll()
  sectores.value = res.data
}

onMounted(() => {
  cargarTareas()
  cargarSectores()
})

function openCreate() {
  editingTarea.value = null
  form.value = { titulo: '', descripcion: '', fechaVencimiento: '', sectorId: null }
  showModal.value = true
}

function openEdit(tarea: Tarea) {
  editingTarea.value = tarea
  form.value = {
    titulo: tarea.titulo,
    descripcion: tarea.descripcion,
    fechaVencimiento: tarea.fechaVencimiento?.slice(0, 16),
    sectorId: tarea.sectorId,
  }
  showModal.value = true
}

async function guardarTarea() {
  if (!form.value.sectorId) {
    error.value = 'Selecciona un sector'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const payload = {
      titulo: form.value.titulo,
      descripcion: form.value.descripcion,
      fechaVencimiento: form.value.fechaVencimiento,
      sectorId: form.value.sectorId,
    }
    if (editingTarea.value) {
      await tareasApi.update(editingTarea.value.id, payload)
    } else {
      await tareasApi.create(payload)
    }
    showModal.value = false
    await cargarTareas()
  } catch {
    error.value = 'Error al guardar la tarea'
  } finally {
    loading.value = false
  }
}

async function toggleTarea(tarea: Tarea) {
  await tareasApi.toggleCompletada(tarea.id)
  await cargarTareas()
}

async function eliminarTarea(id: number) {
  if (!confirm('¿Eliminar esta tarea?')) return
  await tareasApi.delete(id)
  await cargarTareas()
}

function formatFecha(fecha: string) {
  return new Date(fecha).toLocaleDateString('es-CL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function isVencida(fecha: string) {
  return new Date(fecha) < new Date()
}
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Mis Tareas</h1>
      <button @click="openCreate" class="btn-primary">+ Nueva Tarea</button>
    </div>

    <div class="filters">
      <input v-model="busqueda" type="text" placeholder="Buscar por título o descripción..." />
      <select v-model="filtroEstado">
        <option value="all">Todas</option>
        <option value="pendiente">Pendientes</option>
        <option value="completada">Completadas</option>
      </select>
    </div>

    <p v-if="error" class="error-msg">{{ error }}</p>
    <p v-if="loading" class="loading-msg">Cargando...</p>

    <div v-if="tareasFiltradas.length === 0 && !loading" class="empty-state">
      No hay tareas para mostrar.
    </div>

    <div class="tareas-grid">
      <div
        v-for="tarea in tareasFiltradas"
        :key="tarea.id"
        class="tarea-card"
        :class="{
          completada: tarea.estadoCompletada,
          vencida: !tarea.estadoCompletada && isVencida(tarea.fechaVencimiento),
        }"
      >
        <div class="tarea-header">
          <input
            type="checkbox"
            :checked="tarea.estadoCompletada"
            @change="toggleTarea(tarea)"
            class="tarea-check"
          />
          <span class="tarea-titulo" :class="{ tachado: tarea.estadoCompletada }">
            {{ tarea.titulo }}
          </span>
          <span class="badge" :class="tarea.estadoCompletada ? 'badge-ok' : 'badge-pend'">
            {{ tarea.estadoCompletada ? 'Completada' : 'Pendiente' }}
          </span>
        </div>

        <p class="tarea-desc">{{ tarea.descripcion }}</p>

        <div class="tarea-meta">
          <span>📍 {{ tarea.sectorNombre }}</span>
          <span :class="{ 'text-red': !tarea.estadoCompletada && isVencida(tarea.fechaVencimiento) }">
            📅 {{ formatFecha(tarea.fechaVencimiento) }}
          </span>
        </div>

        <div class="tarea-actions">
          <button @click="openEdit(tarea)" class="btn-edit">Editar</button>
          <button @click="eliminarTarea(tarea.id)" class="btn-delete">Eliminar</button>
        </div>
      </div>
    </div>

    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <h3>{{ editingTarea ? 'Editar Tarea' : 'Nueva Tarea' }}</h3>

        <div class="form-group">
          <label>Título *</label>
          <input v-model="form.titulo" type="text" placeholder="Título de la tarea" required />
        </div>
        <div class="form-group">
          <label>Descripción</label>
          <textarea v-model="form.descripcion" placeholder="Descripción..." rows="3"></textarea>
        </div>
        <div class="form-group">
          <label>Fecha de Vencimiento *</label>
          <input v-model="form.fechaVencimiento" type="datetime-local" required />
        </div>
        <div class="form-group">
          <label>Sector *</label>
          <select v-model="form.sectorId" required>
            <option value="">Selecciona un sector</option>
            <option v-for="s in sectores" :key="s.id" :value="s.id">{{ s.nombre }}</option>
          </select>
        </div>

        <p v-if="error" class="error-msg">{{ error }}</p>

        <div class="modal-actions">
          <button @click="showModal = false" class="btn-cancel">Cancelar</button>
          <button @click="guardarTarea" :disabled="loading" class="btn-primary">
            {{ loading ? 'Guardando...' : 'Guardar' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}
h1 {
  color: #2c3e50;
}
.filters {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.filters input {
  flex: 1;
  padding: 0.6rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.95rem;
  outline: none;
}
.filters select {
  padding: 0.6rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.95rem;
}
.tareas-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1rem;
}
.tarea-card {
  background: white;
  border-radius: 10px;
  padding: 1.2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border-left: 4px solid #3498db;
  transition: transform 0.15s;
}
.tarea-card:hover {
  transform: translateY(-2px);
}
.tarea-card.completada {
  border-left-color: #27ae60;
  opacity: 0.8;
}
.tarea-card.vencida {
  border-left-color: #e74c3c;
}
.tarea-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.6rem;
}
.tarea-check {
  width: 18px;
  height: 18px;
  cursor: pointer;
}
.tarea-titulo {
  flex: 1;
  font-weight: 600;
  font-size: 1rem;
}
.tachado {
  text-decoration: line-through;
  color: #999;
}
.badge {
  font-size: 0.75rem;
  padding: 0.2rem 0.5rem;
  border-radius: 12px;
  font-weight: 600;
}
.badge-ok {
  background: #d5f5e3;
  color: #27ae60;
}
.badge-pend {
  background: #fef9e7;
  color: #f39c12;
}
.tarea-desc {
  color: #666;
  font-size: 0.9rem;
  margin-bottom: 0.7rem;
  line-height: 1.4;
}
.tarea-meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.82rem;
  color: #888;
  margin-bottom: 0.8rem;
}
.text-red {
  color: #e74c3c;
}
.tarea-actions {
  display: flex;
  gap: 0.5rem;
}
.btn-edit {
  background: #3498db;
  color: white;
  border: none;
  padding: 0.3rem 0.7rem;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.85rem;
}
.btn-delete {
  background: #e74c3c;
  color: white;
  border: none;
  padding: 0.3rem 0.7rem;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.85rem;
}
.btn-primary {
  background: #2c3e50;
  color: white;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.95rem;
}
.btn-primary:disabled {
  opacity: 0.6;
}
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 100;
}
.modal {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
}
.modal h3 {
  margin-bottom: 1.5rem;
  color: #2c3e50;
}
.form-group {
  margin-bottom: 1rem;
}
label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  margin-bottom: 0.3rem;
  color: #555;
}
input,
textarea,
select {
  width: 100%;
  padding: 0.6rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.95rem;
  outline: none;
}
textarea {
  resize: vertical;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1.5rem;
}
.btn-cancel {
  background: #ecf0f1;
  color: #555;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 6px;
  cursor: pointer;
}
.error-msg {
  color: #e74c3c;
  font-size: 0.85rem;
  margin-bottom: 0.5rem;
}
.loading-msg {
  text-align: center;
  color: #888;
  margin: 2rem 0;
}
.empty-state {
  text-align: center;
  padding: 3rem;
  color: #bbb;
  background: white;
  border-radius: 10px;
}
</style>
