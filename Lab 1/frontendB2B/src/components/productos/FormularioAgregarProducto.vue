<script setup lang="ts">
// =====================================================
// FormularioAgregarProducto.vue
// Formulario para registrar un nuevo tipo de herramienta
// con su lote inicial de stock.
// =====================================================

import { ref, reactive } from 'vue'
import { productoServicio } from '@/services/productoServicio'

// --- Evento que emite al padre cuando se agrega el producto o se cancela ---
const emit = defineEmits<{
  (e: 'productoAgregado'): void
  (e: 'cancelado'): void
}>()

// --- Estado del formulario ---
const categoria_ID   = ref<number | ''>('')
const nombre_producto = ref('')
const descripcion    = ref('')
const precio         = ref<number | ''>('')
const stock          = ref<number | ''>('')
const sku            = ref('')

// --- Errores de validación por campo ---
const errores = reactive<Record<string, string>>({})

// --- Alerta de resultado ---
const alerta = reactive({ visible: false, mensaje: '', tipo: 'exito' as 'exito' | 'error' })

// --- Limpia el formulario ---
const limpiarFormulario = () => {
  categoria_ID.value   = ''
  nombre_producto.value = ''
  descripcion.value    = ''
  precio.value         = ''
  stock.value          = ''
  sku.value            = ''
  Object.keys(errores).forEach(k => delete errores[k])
}

// --- Validación local ---
const validarFormulario = (): boolean => {
  Object.keys(errores).forEach(k => delete errores[k])

  if (!categoria_ID.value || Number(categoria_ID.value) <= 0)
    errores.categoria_ID = 'La categoría es obligatoria y debe ser mayor a 0.'

  if (!nombre_producto.value.trim()) errores.nombre_producto = 'El nombre del producto es obligatorio.'

  if (!descripcion.value.trim()) errores.descripcion = 'La descripción es obligatoria.'

  if (!precio.value || Number(precio.value) <= 0)
    errores.precio = 'El precio debe ser mayor a 0.'

  if (!stock.value || Number(stock.value) < 0)
    errores.stock = 'El stock debe ser mayor o igual a 0.'

  if (!sku.value.trim()) errores.sku = 'El SKU es obligatorio.'

  return Object.keys(errores).length === 0
}

// --- Envío del formulario ---
const manejarEnvio = async () => {
  if (!validarFormulario()) return

  const datosProducto = {
    categoria_ID: Number(categoria_ID.value),
    nombre_producto: nombre_producto.value.trim(),
    descripcion: descripcion.value.trim(),
    precio: Number(precio.value),
    stock: Number(stock.value),
    sku: sku.value.trim(),
  }

  try {
    // ====================================================
    // 🔌 BACKEND — Crear nuevo producto
    // Endpoint: POST /api/productos
    // Body: { categoria_ID, nombre_producto, descripcion, precio, stock, sku }
    // Respuesta: el producto creado
    // ====================================================
    await productoServicio.crearProducto(datosProducto)
    console.log('Producto a registrar:', datosProducto)

    alerta.visible = true
    alerta.mensaje = '¡Producto agregado con éxito!'
    alerta.tipo    = 'exito'
    limpiarFormulario()
    emit('productoAgregado')

  } catch (error: unknown) {
    console.error('Error al agregar el producto:', error)
    // ====================================================
    // 🔌 BACKEND — Manejo del error de la API
    // Spring Boot suele devolver el mensaje en error.response.data
    // ====================================================
    const axiosErr = error as { response?: { data?: string } }
    const msg = axiosErr.response?.data ?? 'Hubo un error de conexión al agregar el producto.'
    alerta.visible = true
    alerta.mensaje = msg
    alerta.tipo    = 'error'
  }
}
</script>

<template>
  <div class="formulario-contenedor">
    <div class="cabecera-modal">
      <h3 class="formulario-titulo">Agregar Nuevo Producto</h3>
      <button class="modal-cerrar" type="button" @click="emit('cancelado')" aria-label="Cerrar">✕</button>
    </div>
    <form class="formulario" @submit.prevent="manejarEnvio" novalidate>

      <!-- Alerta de resultado -->
      <div v-if="alerta.visible" :class="['alerta', `alerta-${alerta.tipo}`]">
        {{ alerta.mensaje }}
        <button class="alerta-cerrar" type="button" @click="alerta.visible = false">✕</button>
      </div>

      <!-- Categoría -->
      <div class="campo">
        <label for="prod-categoria" class="etiqueta">Categoría (ID)</label>
        <input id="prod-categoria" class="entrada" :class="{ 'entrada-error': errores.categoria_ID }"
          type="number" min="1" placeholder="Ej: 1" v-model="categoria_ID" @input="delete errores.categoria_ID" />
        <span v-if="errores.categoria_ID" class="mensaje-error">{{ errores.categoria_ID }}</span>
      </div>

      <!-- Nombre del producto -->
      <div class="campo">
        <label for="prod-nombre" class="etiqueta">Nombre del producto</label>
        <input id="prod-nombre" class="entrada" :class="{ 'entrada-error': errores.nombre_producto }"
          type="text" placeholder="Ej: Notebook" v-model="nombre_producto" @input="delete errores.nombre_producto" />
        <span v-if="errores.nombre_producto" class="mensaje-error">{{ errores.nombre_producto }}</span>
      </div>

      <!-- Descripción -->
      <div class="campo">
        <label for="prod-descripcion" class="etiqueta">Descripción</label>
        <textarea id="prod-descripcion" class="entrada" :class="{ 'entrada-error': errores.descripcion }"
          placeholder="Ej: Notebook Intel Core i7, 16GB RAM, 512GB SSD" v-model="descripcion" @input="delete errores.descripcion"></textarea>
        <span v-if="errores.descripcion" class="mensaje-error">{{ errores.descripcion }}</span>
      </div>

      <!-- Precio -->
      <div class="campo">
        <label for="prod-precio" class="etiqueta">Precio ($)</label>
        <input id="prod-precio" class="entrada" :class="{ 'entrada-error': errores.precio }"
          type="number" min="0.01" step="0.01" placeholder="Ej: 500000" v-model="precio" @input="delete errores.precio" />
        <span v-if="errores.precio" class="mensaje-error">{{ errores.precio }}</span>
      </div>

      <!-- Stock -->
      <div class="campo">
        <label for="prod-stock" class="etiqueta">Stock</label>
        <input id="prod-stock" class="entrada" :class="{ 'entrada-error': errores.stock }"
          type="number" min="0" placeholder="Ej: 10" v-model="stock" @input="delete errores.stock" />
        <span v-if="errores.stock" class="mensaje-error">{{ errores.stock }}</span>
      </div>

      <!-- SKU -->
      <div class="campo">
        <label for="prod-sku" class="etiqueta">SKU</label>
        <input id="prod-sku" class="entrada" :class="{ 'entrada-error': errores.sku }"
          type="text" placeholder="Ej: SKU-NOTEBOOK-1234" v-model="sku" @input="delete errores.sku" />
        <span v-if="errores.sku" class="mensaje-error">{{ errores.sku }}</span>
      </div>

      <div class="formulario-acciones">
        <button type="submit" class="btn-agregar">Agregar Producto</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.formulario-contenedor { padding: 8px; min-width: 340px; }
.cabecera-modal { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.formulario { display: flex; flex-direction: column; gap: 14px; }
.formulario-titulo { font-size: 1.1rem; font-weight: 700; color: #156895; margin: 0; }
.campo { display: flex; flex-direction: column; gap: 4px; }
.etiqueta { font-size: 0.875rem; font-weight: 600; color: #444; }
.entrada { padding: 9px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; outline: none; transition: border-color 0.2s, box-shadow 0.2s; }
.entrada:focus { border-color: #156895; box-shadow: 0 0 0 3px rgba(21,104,149,0.15); }
.entrada-error { border-color: #d32f2f; }
.entrada[type="textarea"] { resize: vertical; min-height: 60px; }
.mensaje-error { font-size: 0.78rem; color: #d32f2f; }
.alerta { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-radius: 8px; font-size: 0.875rem; }
.alerta-exito { background: #e8f5e9; color: #2e7d32; border: 1px solid #a5d6a7; }
.alerta-error  { background: #ffebee; color: #c62828; border: 1px solid #ef9a9a; }
.alerta-cerrar { background: none; border: none; cursor: pointer; font-size: 1rem; color: inherit; opacity: 0.7; }
.alerta-cerrar:hover { opacity: 1; }
.formulario-acciones { display: flex; justify-content: center; margin-top: 8px; }
.btn-agregar { padding: 10px 28px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
.btn-agregar:hover { background-color: #0f5070; }
.modal-cerrar { background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #666; padding: 4px; line-height: 1; }
.modal-cerrar:hover { color: #333; }
</style>
