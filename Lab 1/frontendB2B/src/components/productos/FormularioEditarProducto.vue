<script setup lang="ts">
// =====================================================
// FormularioEditarProducto.vue
// Permite editar los campos de un producto existente.
// =====================================================

import { reactive, onMounted } from 'vue'
import { productoServicio } from '@/services/productoServicio'

// --- Props ---
const props = defineProps<{
  idProducto: number
}>()

// --- Evento que emite al padre cuando se guarda o se cancela ---
const emit = defineEmits<{
  (e: 'actualizado'): void
  (e: 'cancelado'): void
}>()

// --- Estado del formulario ---
const producto = reactive({
  producto_ID: 0,
  categoria_ID: 0,
  nombre_producto: '',
  descripcion: '',
  precio: 0,
  stock: 0,
  sku: '',
})

// --- Alerta de resultado ---
const alerta = reactive({ visible: false, mensaje: '', tipo: 'exito' as 'exito' | 'error' })

// --- Cargar datos del producto ---
const cargarProducto = async () => {
  try {
    const respuesta = await productoServicio.obtenerPorId(props.idProducto)
    const data = respuesta.data
    producto.producto_ID = data.producto_ID
    producto.categoria_ID = data.categoria_ID
    producto.nombre_producto = data.nombre_producto
    producto.descripcion = data.descripcion
    producto.precio = data.precio
    producto.stock = data.stock
    producto.sku = data.sku
  } catch (error: unknown) {
    const axiosErr = error as { response?: { data?: string | { error?: string; message?: string } } }
    const data = axiosErr.response?.data
    let msg = 'Error al cargar el producto.'
    if (typeof data === 'string') {
      msg = data
    } else if (data?.message) {
      msg = data.message
    } else if (data?.error) {
      msg = data.error
    }
    alerta.visible = true
    alerta.mensaje = msg
    alerta.tipo = 'error'
  }
}

// --- Envío del formulario ---
const manejarGuardar = async () => {
  if (!producto.nombre_producto.trim()) {
    alerta.visible = true
    alerta.mensaje = 'El nombre del producto es obligatorio.'
    alerta.tipo = 'error'
    return
  }
  if (producto.categoria_ID <= 0) {
    alerta.visible = true
    alerta.mensaje = 'Selecciona una categoría válida.'
    alerta.tipo = 'error'
    return
  }
  if (producto.precio < 0) {
    alerta.visible = true
    alerta.mensaje = 'El precio no puede ser negativo.'
    alerta.tipo = 'error'
    return
  }

  try {
    await productoServicio.actualizar(props.idProducto, producto)

    alerta.visible = true
    alerta.mensaje = '¡Producto actualizado correctamente!'
    alerta.tipo = 'exito'
    emit('actualizado')

  } catch (error: unknown) {
    const axiosErr = error as { response?: { data?: string | { error?: string; message?: string } } }
    const data = axiosErr.response?.data
    let msg = 'Hubo un error al actualizar el producto.'
    if (typeof data === 'string') {
      msg = data
    } else if (data?.message) {
      msg = data.message
    } else if (data?.error) {
      msg = data.error
    }
    alerta.visible = true
    alerta.mensaje = msg
    alerta.tipo = 'error'
  }
}

onMounted(cargarProducto)
</script>

<template>
  <div class="formulario-contenedor">
    <div class="cabecera-modal">
      <h3 class="formulario-titulo">Editar Producto</h3>
      <button class="modal-cerrar" type="button" @click="emit('cancelado')" aria-label="Cerrar">✕</button>
    </div>

    <!-- Alerta de resultado -->
    <div v-if="alerta.visible" :class="['alerta', `alerta-${alerta.tipo}`]">
      {{ alerta.mensaje }}
      <button class="alerta-cerrar" type="button" @click="alerta.visible = false">✕</button>
    </div>

    <!-- Categoría ID -->
    <div class="campo-grupo">
      <label class="campo-label">Categoría ID</label>
      <input
        class="entrada"
        type="number"
        v-model.number="producto.categoria_ID"
        placeholder="ID de la categoría"
      />
    </div>

    <!-- Nombre del producto -->
    <div class="campo-grupo">
      <label class="campo-label">Nombre del Producto</label>
      <input
        class="entrada"
        type="text"
        v-model="producto.nombre_producto"
        placeholder="Nombre del producto"
      />
    </div>

    <!-- Descripción -->
    <div class="campo-grupo">
      <label class="campo-label">Descripción</label>
      <textarea
        class="entrada"
        v-model="producto.descripcion"
        placeholder="Descripción del producto"
        rows="3"
      ></textarea>
    </div>

    <!-- Precio -->
    <div class="campo-grupo">
      <label class="campo-label">Precio</label>
      <input
        class="entrada"
        type="number"
        step="0.01"
        min="0"
        v-model.number="producto.precio"
        placeholder="Precio del producto"
      />
    </div>

    <!-- SKU -->
    <div class="campo-grupo">
      <label class="campo-label">SKU</label>
      <input
        class="entrada"
        type="text"
        v-model="producto.sku"
        placeholder="Código SKU"
      />
    </div>

    <!-- Nota: El stock se conserva sin cambios -->
    <p class="nota-stock">Nota: El stock del producto se mantiene sin cambios.</p>

    <div class="formulario-acciones">
      <button type="button" class="btn-guardar" @click="manejarGuardar">Guardar Cambios</button>
    </div>
  </div>
</template>

<style scoped>
.formulario-contenedor { padding: 8px; min-width: 320px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; max-height: 90vh; }
.cabecera-modal { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px; }
.formulario-titulo { font-size: 1.1rem; font-weight: 700; color: #156895; margin: 0; }
.campo-grupo { display: flex; flex-direction: column; gap: 8px; }
.campo-label { font-size: 0.9rem; font-weight: 600; color: #333; }
.entrada { padding: 9px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; outline: none; transition: border-color 0.2s; }
.entrada:focus { border-color: #156895; }
.alerta { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-radius: 8px; font-size: 0.875rem; }
.alerta-exito { background: #e8f5e9; color: #2e7d32; border: 1px solid #a5d6a7; }
.alerta-error  { background: #ffebee; color: #c62828; border: 1px solid #ef9a9a; }
.alerta-cerrar { background: none; border: none; cursor: pointer; font-size: 1rem; color: inherit; }
.formulario-acciones { display: flex; justify-content: center; }
.btn-guardar { padding: 10px 28px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
.btn-guardar:hover { background-color: #0f5070; }
.nota-stock { font-size: 0.85rem; color: #666; font-style: italic; text-align: center; }
.modal-cerrar { background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #666; padding: 4px; line-height: 1; }
.modal-cerrar:hover { color: #333; }
</style>
