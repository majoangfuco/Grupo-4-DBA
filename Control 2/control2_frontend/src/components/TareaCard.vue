<script setup lang="ts">
import { Icon } from '@iconify/vue'

/**
 * TareaCard — Tarjeta de tarea reutilizable.
 *
 * Usado en: DashboardView.vue
 *
 * Props:
 *   - tarea: objeto Tarea con campos id, titulo, descripcion,
 *            fechaVencimiento, estadoCompletada, sectorNombre
 *
 * Emits:
 *   - toggle: cuando el usuario marca/desmarca la tarea
 *   - edit:   cuando el usuario pulsa Editar
 *   - delete: cuando el usuario pulsa Eliminar
 *
 * Uso:
 *   <TareaCard :tarea="t" @toggle="toggleTarea(t)" @edit="openEdit(t)" @delete="eliminar(t.id)" />
 */

interface Tarea {
  id: number
  titulo: string
  descripcion: string
  fechaVencimiento: string
  estadoCompletada: boolean
  sectorId: number
  sectorNombre: string
}

defineProps<{ tarea: Tarea }>()
const emit = defineEmits<{ toggle: []; edit: []; delete: [] }>()

function formatFecha(fecha: string) {
  return new Date(fecha).toLocaleDateString('es-CL', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function isVencida(fecha: string) {
  return new Date(fecha) < new Date()
}
</script>

<template>
  <div
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
        @change="emit('toggle')"
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
      <span><Icon icon="lucide:map-pin" class="icon" /> {{ tarea.sectorNombre }}</span>
      <span :class="{ 'text-red': !tarea.estadoCompletada && isVencida(tarea.fechaVencimiento) }">
        <Icon icon="lucide:calendar" class="icon" /> {{ formatFecha(tarea.fechaVencimiento) }}
      </span>
    </div>

    <div class="tarea-actions">
      <button @click="emit('edit')" class="btn-edit">
        <Icon icon="lucide:pencil" class="icon" /> Editar
      </button>
      <button @click="emit('delete')" class="btn-delete">
        <Icon icon="lucide:trash-2" class="icon" /> Eliminar
      </button>
    </div>
  </div>
</template>

<style scoped>
.tarea-card {
  background: white;
  border-radius: 10px;
  padding: 1.2rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  border-left: 4px solid #3498db;
  transition: transform 0.15s;
}
.tarea-card:hover { transform: translateY(-2px); }
.tarea-card.completada { border-left-color: #27ae60; opacity: 0.8; }
.tarea-card.vencida    { border-left-color: #e74c3c; }

.tarea-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.6rem;
}
.tarea-check { width: 18px; height: 18px; cursor: pointer; }
.tarea-titulo { flex: 1; font-weight: 600; font-size: 1rem; }
.tachado { text-decoration: line-through; color: #999; }

.badge { font-size: 0.75rem; padding: 0.2rem 0.5rem; border-radius: 12px; font-weight: 600; }
.badge-ok   { background: #d5f5e3; color: #27ae60; }
.badge-pend { background: #fef9e7; color: #f39c12; }

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
.text-red { color: #e74c3c; }

.tarea-actions { display: flex; gap: 0.5rem; }
.btn-edit {
  background: #3498db; color: white; border: none;
  padding: 0.3rem 0.7rem; border-radius: 5px; cursor: pointer; font-size: 0.85rem;
  display: inline-flex; align-items: center; gap: 0.3rem;
}
.btn-delete {
  background: #e74c3c; color: white; border: none;
  padding: 0.3rem 0.7rem; border-radius: 5px; cursor: pointer; font-size: 0.85rem;
  display: inline-flex; align-items: center; gap: 0.3rem;
}

.icon {
  width: 1em; height: 1em;
  vertical-align: -0.15em; flex-shrink: 0; display: inline-block;
}
</style>
