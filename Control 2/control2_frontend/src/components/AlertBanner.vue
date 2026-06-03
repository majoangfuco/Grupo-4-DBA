<script setup lang="ts">
import { Icon } from '@iconify/vue'

/**
 * Props:
 *   - message: texto del mensaje (string)
 *   - type: 'success' | 'error' | 'warning' (default: 'success')
 *   - closeable: si muestra botón X para cerrar (default: true)
 */

const props = withDefaults(defineProps<{
  message: string
  type?: 'success' | 'error' | 'warning'
  closeable?: boolean
}>(), {
  type: 'success',
  closeable: true,
})

const emit = defineEmits<{ close: [] }>()

const iconMap = {
  success: 'lucide:circle-check',
  error: 'lucide:triangle-alert',
  warning: 'lucide:triangle-alert',
}
</script>

<template>
  <transition name="alert-fade">
    <div v-if="message" :class="['alert-banner', `alert-${type}`]">
      <span class="alert-content">
        <Icon :icon="iconMap[type]" class="icon" />
        {{ message }}
      </span>
      <button v-if="closeable" @click="emit('close')" class="close-btn" aria-label="Cerrar">
        <Icon icon="lucide:x" class="icon" />
      </button>
    </div>
  </transition>
</template>

<style scoped>
.alert-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.8rem 1.2rem;
  border-radius: 8px;
  margin-bottom: 1.2rem;
  font-weight: 500;
  font-size: 0.9rem;
  gap: 0.8rem;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
}

/* Variantes de color */
.alert-success {
  background: linear-gradient(135deg, #d5f5e3, #a9dfbf);
  border: 1px solid #27ae60;
  color: #1e8449;
}

.alert-error {
  background: #fdecea;
  border: 1px solid #e74c3c;
  color: #c0392b;
}

.alert-warning {
  background: #fef9e7;
  border: 1px solid #f39c12;
  color: #856404;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  color: inherit;
  padding: 0;
  opacity: 0.7;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.close-btn:hover { opacity: 1; }

.icon {
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
  flex-shrink: 0;
  display: inline-block;
}

.alert-fade-enter-active, .alert-fade-leave-active { transition: opacity 0.3s ease; }
.alert-fade-enter-from, .alert-fade-leave-to { opacity: 0; }
</style>
