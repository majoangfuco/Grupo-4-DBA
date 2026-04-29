<script setup lang="ts">
// =====================================================
// FormularioAgregarProducto.vue
// Formulario para registrar un nuevo tipo de herramienta
// con su lote inicial de stock.
// =====================================================

import { ref, reactive } from 'vue'

// --- Evento que emite al padre cuando se agrega el producto ---
const emit = defineEmits<{
  (e: 'productoAgregado'): void
}>()

// --- Estado del formulario ---
const nombre          = ref('')
const categoria       = ref('')
const valorReposicion = ref<number | ''>('')
const tarifaDiaria    = ref<number | ''>('')
const tarifaAtraso    = ref<number | ''>('')
const cantidad        = ref<number | ''>('')

// --- Errores de validación por campo ---
const errores = reactive<Record<string, string>>({})

// --- Alerta de resultado ---
const alerta = reactive({ visible: false, mensaje: '', tipo: 'exito' as 'exito' | 'error' })

// --- Limpia el formulario ---
const limpiarFormulario = () => {
  nombre.value          = ''
  categoria.value       = ''
  valorReposicion.value = ''
  tarifaDiaria.value    = ''
  tarifaAtraso.value    = ''
  cantidad.value        = ''
  Object.keys(errores).forEach(k => delete errores[k])
}

// --- Validación local ---
const validarFormulario = (): boolean => {
  Object.keys(errores).forEach(k => delete errores[k])

  if (!nombre.value)     errores.nombre    = 'El nombre es obligatorio.'
  if (!categoria.value)  errores.categoria = 'La categoría es obligatoria.'

  if (!valorReposicion.value || Number(valorReposicion.value) <= 0)
    errores.valorReposicion = 'El valor de reposición debe ser mayor a 0.'

  if (tarifaDiaria.value === '' || Number(tarifaDiaria.value) < 0)
    errores.tarifaDiaria = 'La tarifa diaria debe ser mayor o igual a 0.'

  if (tarifaAtraso.value === '' || Number(tarifaAtraso.value) < 0)
    errores.tarifaAtraso = 'La tarifa de atraso debe ser mayor o igual a 0.'

  if (!cantidad.value || Number(cantidad.value) <= 0)
    errores.cantidad = 'La cantidad debe ser mayor a 0.'

  return Object.keys(errores).length === 0
}

// --- Envío del formulario ---
const manejarEnvio = async () => {
  if (!validarFormulario()) return

  const datosProducto = {
    nombre:          nombre.value,
    categoria:       categoria.value,
    valorReposicion: Number(valorReposicion.value),
    tarifaDiaria:    Number(tarifaDiaria.value),
    tarifaAtraso:    Number(tarifaAtraso.value),
    cantidad:        Number(cantidad.value),
  }

  try {
    // ====================================================
    // 🔌 BACKEND — Crear nuevo tipo de herramienta + stock
    // Endpoint esperado: POST /api/herramientas
    // Body: { nombre, categoria, valorReposicion, tarifaDiaria, tarifaAtraso, cantidad }
    // Respuesta esperada: el objeto creado con su ID
    //
    // Descomentar cuando el backend esté listo:
    // import { productoServicio } from '@/services/productoServicio'
    // await productoServicio.crearLote(datosProducto)
    // ====================================================
    console.log('Producto a registrar:', datosProducto)

    alerta.visible = true
    alerta.mensaje = '¡Herramienta agregada con éxito!'
    alerta.tipo    = 'exito'
    limpiarFormulario()
    emit('productoAgregado')

  } catch (error: unknown) {
    console.error('Error al agregar la herramienta:', error)
    // ====================================================
    // 🔌 BACKEND — Manejo del error de la API
    // Spring Boot suele devolver el mensaje en error.response.data
    // ====================================================
    const axiosErr = error as { response?: { data?: string } }
    const msg = axiosErr.response?.data ?? 'Hubo un error de conexión al agregar la herramienta.'
    alerta.visible = true
    alerta.mensaje = msg
    alerta.tipo    = 'error'
  }
}
</script>

<template>
  <div class="formulario-contenedor">
    <form class="formulario" @submit.prevent="manejarEnvio" novalidate>
      <h3 class="formulario-titulo">Agregar Nueva Herramienta</h3>

      <!-- Alerta de resultado -->
      <div v-if="alerta.visible" :class="['alerta', `alerta-${alerta.tipo}`]">
        {{ alerta.mensaje }}
        <button class="alerta-cerrar" type="button" @click="alerta.visible = false">✕</button>
      </div>

      <!-- Nombre -->
      <div class="campo">
        <label for="prod-nombre" class="etiqueta">Nombre</label>
        <input id="prod-nombre" class="entrada" :class="{ 'entrada-error': errores.nombre }"
          type="text" placeholder="Ej: Martillo" v-model="nombre" @input="delete errores.nombre" />
        <span v-if="errores.nombre" class="mensaje-error">{{ errores.nombre }}</span>
      </div>

      <!-- Categoría -->
      <div class="campo">
        <label for="prod-categoria" class="etiqueta">Categoría</label>
        <input id="prod-categoria" class="entrada" :class="{ 'entrada-error': errores.categoria }"
          type="text" placeholder="Ej: Construcción" v-model="categoria" @input="delete errores.categoria" />
        <span v-if="errores.categoria" class="mensaje-error">{{ errores.categoria }}</span>
      </div>

      <!-- Valor de reposición -->
      <div class="campo">
        <label for="prod-reposicion" class="etiqueta">Valor de reposición ($)</label>
        <input id="prod-reposicion" class="entrada" :class="{ 'entrada-error': errores.valorReposicion }"
          type="number" min="1" placeholder="Ej: 50000" v-model="valorReposicion" @input="delete errores.valorReposicion" />
        <span v-if="errores.valorReposicion" class="mensaje-error">{{ errores.valorReposicion }}</span>
      </div>

      <!-- Tarifa diaria -->
      <div class="campo">
        <label for="prod-diaria" class="etiqueta">Tarifa diaria de arriendo ($)</label>
        <input id="prod-diaria" class="entrada" :class="{ 'entrada-error': errores.tarifaDiaria }"
          type="number" min="0" placeholder="Ej: 5000" v-model="tarifaDiaria" @input="delete errores.tarifaDiaria" />
        <span v-if="errores.tarifaDiaria" class="mensaje-error">{{ errores.tarifaDiaria }}</span>
      </div>

      <!-- Tarifa de atraso -->
      <div class="campo">
        <label for="prod-atraso" class="etiqueta">Tarifa por día de atraso ($)</label>
        <input id="prod-atraso" class="entrada" :class="{ 'entrada-error': errores.tarifaAtraso }"
          type="number" min="0" placeholder="Ej: 8000" v-model="tarifaAtraso" @input="delete errores.tarifaAtraso" />
        <span v-if="errores.tarifaAtraso" class="mensaje-error">{{ errores.tarifaAtraso }}</span>
      </div>

      <!-- Cantidad -->
      <div class="campo">
        <label for="prod-cantidad" class="etiqueta">Cantidad inicial en stock</label>
        <input id="prod-cantidad" class="entrada" :class="{ 'entrada-error': errores.cantidad }"
          type="number" min="1" placeholder="Ej: 10" v-model="cantidad" @input="delete errores.cantidad" />
        <span v-if="errores.cantidad" class="mensaje-error">{{ errores.cantidad }}</span>
      </div>

      <div class="formulario-acciones">
        <button type="submit" class="btn-agregar">Agregar Herramienta</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.formulario-contenedor { padding: 8px; min-width: 340px; }
.formulario { display: flex; flex-direction: column; gap: 14px; }
.formulario-titulo { font-size: 1.1rem; font-weight: 700; color: #156895; margin-bottom: 4px; }
.campo { display: flex; flex-direction: column; gap: 4px; }
.etiqueta { font-size: 0.875rem; font-weight: 600; color: #444; }
.entrada { padding: 9px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; outline: none; transition: border-color 0.2s, box-shadow 0.2s; }
.entrada:focus { border-color: #156895; box-shadow: 0 0 0 3px rgba(21,104,149,0.15); }
.entrada-error { border-color: #d32f2f; }
.mensaje-error { font-size: 0.78rem; color: #d32f2f; }
.alerta { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-radius: 8px; font-size: 0.875rem; }
.alerta-exito { background: #e8f5e9; color: #2e7d32; border: 1px solid #a5d6a7; }
.alerta-error  { background: #ffebee; color: #c62828; border: 1px solid #ef9a9a; }
.alerta-cerrar { background: none; border: none; cursor: pointer; font-size: 1rem; color: inherit; opacity: 0.7; }
.alerta-cerrar:hover { opacity: 1; }
.formulario-acciones { display: flex; justify-content: center; margin-top: 8px; }
.btn-agregar { padding: 10px 28px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
.btn-agregar:hover { background-color: #0f5070; }
</style>
