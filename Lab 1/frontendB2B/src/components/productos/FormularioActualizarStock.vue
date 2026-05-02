<script setup lang="ts">
import { ref } from 'vue'
import { productoServicio } from '@/services/productoServicio'

const props = defineProps<{
  idProducto: number
  nombreProducto: string
  stockActual: number
}>()

const emit = defineEmits<{
  (e: 'actualizado'): void
  (e: 'cancelado'): void
}>()

const nuevoStockInput = ref<number | ''>(props.stockActual)
const cargandoStock = ref(false)
const errorStock = ref<string | null>(null)

const guardarStock = async () => {
  if (nuevoStockInput.value === '' || nuevoStockInput.value < 0) {
    errorStock.value = "El stock debe ser un número válido mayor o igual a cero."
    return
  }
  
  cargandoStock.value = true
  errorStock.value = null
  
  try {
    await productoServicio.actualizarStock(props.idProducto, nuevoStockInput.value)
    emit('actualizado')
  } catch (error: unknown) {
    const axiosErr = error as { response?: { data?: string | { error?: string; message?: string } } }
    const data = axiosErr.response?.data
    let msg = 'Error al actualizar el stock.'
    if (typeof data === 'string') {
      msg = data
    } else if (data?.message) {
      msg = data.message
    } else if (data?.error) {
      msg = data.error
    }
    errorStock.value = msg
  } finally {
    cargandoStock.value = false
  }
}
</script>

<template>
  <div class="modal-pequeno">
    <div class="cabecera-modal">
      <h3 class="modal-titulo">Actualizar Stock</h3>
      <button class="modal-cerrar" type="button" @click="emit('cancelado')" aria-label="Cerrar">✕</button>
    </div>
    <p class="modal-desc">
      Producto: <strong>{{ nombreProducto }}</strong><br>
      Stock actual: {{ stockActual }}
    </p>

    <div class="campo-grupo">
      <label class="campo-label">Nuevo Stock Total:</label>
      <input 
        type="number" 
        class="entrada" 
        v-model.number="nuevoStockInput" 
        min="0"
        placeholder="Ingrese el nuevo stock"
      />
    </div>

    <div v-if="errorStock" class="alerta-error">
      {{ errorStock }}
    </div>

    <div class="modal-acciones">
      <button class="btn-secundario" @click="emit('cancelado')" :disabled="cargandoStock">Cancelar</button>
      <button class="btn-primario" @click="guardarStock" :disabled="cargandoStock">
        {{ cargandoStock ? 'Guardando...' : 'Guardar' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.modal-pequeno { min-width: 300px; max-width: 400px; }
.cabecera-modal { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.modal-titulo { font-size: 1.1rem; color: #156895; margin: 0; }
.modal-desc { font-size: 0.9rem; color: #555; margin-bottom: 16px; line-height: 1.4; }
.campo-grupo { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }
.campo-label { font-size: 0.9rem; font-weight: 600; color: #333; }
.entrada { padding: 8px 12px; border: 1px solid #ccc; border-radius: 6px; font-size: 0.9rem; outline: none; }
.entrada:focus { border-color: #156895; }
.alerta-error { background: #ffebee; color: #c62828; padding: 10px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 16px; border: 1px solid #ef9a9a; }
.modal-acciones { display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px; }
.btn-secundario { padding: 8px 16px; background: white; border: 1px solid #ccc; border-radius: 6px; cursor: pointer; color: #555; transition: background 0.2s; }
.btn-secundario:hover { background: #f5f5f5; }
.btn-primario { padding: 8px 16px; background: #156895; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; transition: background 0.2s; }
.btn-primario:hover { background: #0f5070; }
.btn-primario:disabled, .btn-secundario:disabled { opacity: 0.6; cursor: not-allowed; }

.modal-cerrar { background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #666; padding: 4px; line-height: 1; }
.modal-cerrar:hover { color: #333; }
</style>
