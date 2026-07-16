<script setup lang="ts">
import { computed } from 'vue'
import { Icon } from '@iconify/vue'

/**
 * BarChart — Gráfico de barras horizontales proporcionales reutilizable.
 *
 * Usado en: EstadisticasView.vue (Q1 Mis tareas por sector, Q5 Sectores con pendientes)
 *
 * Props:
 *   - items:        Array de { label: string, value: number }
 *   - color:        'blue' | 'orange' | 'green' (default: 'blue')
 *   - emptyMessage: Mensaje cuando no hay items (opcional)
 *
 * Uso:
 *   <BarChart
 *     :items="tareasPorSector.map(i => ({ label: i.sector, value: Number(i.total) }))"
 *     color="blue"
 *     emptyMessage="No hay tareas registradas"
 *   />
 */

const props = withDefaults(defineProps<{
  items: { label: string; value: number }[]
  color?: 'blue' | 'orange' | 'green'
  emptyMessage?: string
}>(), {
  color: 'blue',
  emptyMessage: 'Sin datos disponibles.',
})

const maxValue = computed(() =>
  props.items.length ? Math.max(...props.items.map(i => i.value)) : 1
)
</script>

<template>
  <div v-if="items.length > 0" class="bar-chart">
    <div v-for="item in items" :key="item.label" class="bar-row">
      <span class="bar-label">{{ item.label }}</span>
      <div class="bar-track">
        <div
          :class="['bar-fill', `bar-fill--${color}`]"
          :style="{ width: `${(item.value / maxValue) * 100}%` }"
        />
      </div>
      <span class="bar-count">{{ item.value }}</span>
    </div>
  </div>
  <div v-else class="empty-state">
    <p><Icon icon="lucide:folder-open" class="icon" /> {{ emptyMessage }}</p>
  </div>
</template>

<style scoped>
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
.bar-fill--green  { background: linear-gradient(90deg, #27ae60, #2ecc71); }

.bar-count {
  font-size: 0.85rem;
  font-weight: 700;
  color: #2c3e50;
  text-align: right;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: #95a5a6;
  font-size: 0.9rem;
}

.icon {
  width: 1em; height: 1em;
  vertical-align: -0.15em; flex-shrink: 0; display: inline-block;
}

@media (max-width: 768px) {
  .bar-row { grid-template-columns: 140px 1fr 36px; }
}
</style>
