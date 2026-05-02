<script setup lang="ts">
// =====================================================
// FormularioEliminarProducto.vue
// Confirmación antes de eliminar un producto.
// Muestra el nombre del producto y pide confirmación
// explícita antes de ejecutar la eliminación.
// =====================================================

import { reactive } from 'vue'
import { productoServicio } from '@/services/productoServicio'

// --- Props: datos del producto a eliminar ---
const props = defineProps<{
  idProducto: number
  nombreProducto: string
}>()

// --- Evento que emite al padre cuando se elimina ---
const emit = defineEmits<{
  (e: 'eliminado'): void
  (e: 'cancelado'): void
}>()

// --- Estado ---
const eliminando = reactive({ enProceso: false })
const alerta     = reactive({ visible: false, mensaje: '', tipo: 'exito' as 'exito' | 'error' })

// --- Confirmar eliminación ---
const confirmarEliminar = async () => {
  eliminando.enProceso = true
  alerta.visible = false

  try {
    await productoServicio.eliminar(props.idProducto)

    alerta.visible = true
    alerta.mensaje = `"${props.nombreProducto}" fue eliminado correctamente.`
    alerta.tipo    = 'exito'

    // Espera un momento para que el usuario vea el mensaje, luego cierra
    setTimeout(() => emit('eliminado'), 1200)

  } catch (error: unknown) {
    // ====================================================
    // 🔌 BACKEND — Manejo de error (ej: producto con dependencias)
    // Spring Boot puede devolver un 404 Not Found si no existe
    // ====================================================
    const axiosErr = error as { response?: { data?: string | { message?: string } } }
    const data = axiosErr.response?.data
    const msg = typeof data === 'string'
      ? data
      : data?.message ?? 'No se pudo eliminar el producto. Intenta de nuevo.'
    alerta.visible = true
    alerta.mensaje = msg
    alerta.tipo    = 'error'
  } finally {
    eliminando.enProceso = false
  }
}
</script>

<template>
  <div class="contenedor">
    <div class="cabecera-modal">
      <div class="icono-advertencia" aria-hidden="true">⚠️</div>
      <button class="modal-cerrar" type="button" @click="emit('cancelado')" aria-label="Cerrar">✕</button>
    </div>
    <h3 class="titulo">Eliminar Producto</h3>

    <!-- Descripción del producto a eliminar -->
    <p class="descripcion">
      ¿Estás seguro de que deseas eliminar
      <strong class="nombre-producto">{{ nombreProducto }}</strong>?
    </p>
    <p class="aviso">Esta acción no se puede deshacer y eliminará también el stock asociado.</p>

    <!-- Alerta de resultado -->
    <div v-if="alerta.visible" :class="['alerta', `alerta-${alerta.tipo}`]">
      {{ alerta.mensaje }}
    </div>

    <!-- Botones de acción -->
    <div class="acciones">
      <button
        class="btn-cancelar"
        type="button"
        :disabled="eliminando.enProceso"
        @click="emit('cancelado')"
      >
        Cancelar
      </button>
      <button
        class="btn-eliminar"
        type="button"
        :disabled="eliminando.enProceso"
        @click="confirmarEliminar"
      >
        <span v-if="eliminando.enProceso" class="spinner" aria-hidden="true"></span>
        {{ eliminando.enProceso ? 'Eliminando...' : 'Sí, eliminar' }}
      </button>
    </div>

  </div>
</template>

<style scoped>
/* ===== CONTENEDOR ===== */
.contenedor {
  padding: 8px;
  min-width: 320px;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  text-align: center;
}

.cabecera-modal { display: flex; justify-content: space-between; align-items: flex-start; }

/* ===== ÍCONO ===== */
.icono-advertencia {
  font-size: 3rem;
  line-height: 1;
}

/* ===== TEXTOS ===== */
.titulo {
  font-size: 1.2rem;
  font-weight: 700;
  color: #b71c1c;
  margin: 0;
}

.descripcion {
  font-size: 0.95rem;
  color: #333;
  line-height: 1.5;
}

.nombre-producto {
  color: #156895;
}

.aviso {
  font-size: 0.82rem;
  color: #888;
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 8px 12px;
  width: 100%;
  box-sizing: border-box;
}

/* ===== ALERTA ===== */
.alerta {
  width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.875rem;
  box-sizing: border-box;
}

.alerta-exito {
  background: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}

.alerta-error {
  background: #ffebee;
  color: #c62828;
  border: 1px solid #ef9a9a;
}

/* ===== BOTONES ===== */
.acciones {
  display: flex;
  gap: 12px;
  width: 100%;
}

.btn-cancelar,
.btn-eliminar {
  flex: 1;
  padding: 11px 0;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: background-color 0.2s, opacity 0.2s;
}

.btn-cancelar {
  background: #f0f0f0;
  color: #333;
}

.btn-cancelar:hover:not(:disabled) {
  background: #e0e0e0;
}

.btn-eliminar {
  background: #c62828;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-eliminar:hover:not(:disabled) {
  background: #b71c1c;
}

.btn-cancelar:disabled,
.btn-eliminar:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ===== SPINNER ===== */
.spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: white;
  border-radius: 50%;
  animation: girar 0.7s linear infinite;
}

@keyframes girar {
  to { transform: rotate(360deg); }
}

.modal-cerrar { background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #666; padding: 4px; line-height: 1; }
.modal-cerrar:hover { color: #333; }
</style>
