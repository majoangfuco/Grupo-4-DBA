<script setup lang="ts">
import { Icon } from '@iconify/vue'

/**
 * StatCard — Tarjeta de métrica/estadística reutilizable.
 *
 * Usado en: EstadisticasView.vue (Q2, Q3, Q4/Q8, Q7)
 *
 * Props:
 *   - label:    Texto superior pequeño (ej: "Tarea Más Cercana")
 *   - sublabel: Texto descriptivo secundario (opcional)
 *   - icon:     Nombre del icono Iconify (ej: "lucide:map-pin")
 *   - accent:   'blue' | 'green' | 'purple' | 'orange' (default: 'blue')
 *   - empty:    Mensaje cuando no hay datos (opcional)
 *
 * Slots:
 *   - default: contenido principal de la card (valor, nombre, badge, etc.)
 *
 * Uso:
 *   <StatCard label="Tarea más cercana" icon="lucide:map-pin" accent="blue">
 *     <p class="metric-main">{{ tarea.titulo }}</p>
 *   </StatCard>
 */

withDefaults(defineProps<{
  label: string
  sublabel?: string
  icon: string
  accent?: 'blue' | 'green' | 'purple' | 'orange'
  empty?: string
}>(), {
  accent: 'blue',
})
</script>

<template>
  <div :class="['metric-card', `accent-${accent}`]">
    <div class="metric-icon">
      <Icon :icon="icon" class="icon" />
    </div>
    <div class="metric-body">
      <p class="metric-label">{{ label }}</p>
      <p v-if="sublabel" class="metric-label-sub">{{ sublabel }}</p>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.metric-card {
  background: white;
  border-radius: 12px;
  padding: 1.4rem;
  box-shadow: 0 2px 12px rgba(0,0,0,0.07);
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  border-top: 4px solid transparent;
}

.accent-blue   { border-top-color: #3498db; }
.accent-green  { border-top-color: #2ecc71; }
.accent-purple { border-top-color: #9b59b6; }
.accent-orange { border-top-color: #e67e22; }

.metric-icon {
  font-size: 1.8rem;
  line-height: 1;
  flex-shrink: 0;
}

.metric-body {
  flex: 1;
  min-width: 0;
}

.metric-label {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #7f8c8d;
  margin: 0 0 0.3rem 0;
}

.metric-label-sub {
  font-size: 0.72rem;
  color: #95a5a6;
  margin: 0 0 0.5rem 0;
}

.icon {
  width: 1em; height: 1em;
  vertical-align: -0.15em; flex-shrink: 0; display: inline-block;
}
</style>
