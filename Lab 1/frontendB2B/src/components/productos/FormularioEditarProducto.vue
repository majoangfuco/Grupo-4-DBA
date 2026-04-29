<script setup lang="ts">
// =====================================================
// FormularioEditarProducto.vue
// Permite editar selectivamente: tarifa diaria,
// tarifa de atraso y valor de reposición de un
// tipo de herramienta existente.
// =====================================================

import { reactive } from 'vue'

// --- Props ---
const props = defineProps<{
  idProducto: number
}>()

// --- Evento que emite al padre cuando se guarda ---
const emit = defineEmits<{
  (e: 'actualizado'): void
}>()

// --- Checkboxes: qué campos desea editar el usuario ---
const seleccionados = reactive({
  tarifaDiaria:    false,
  tarifaAtraso:    false,
  valorReposicion: false,
})

// --- Valores de cada campo ---
const valores = reactive({
  tarifaDiaria:    '',
  tarifaAtraso:    '',
  valorReposicion: '',
})

// --- Alerta de resultado ---
const alerta = reactive({ visible: false, mensaje: '', tipo: 'exito' as 'exito' | 'error' })

// --- Envío del formulario ---
const manejarGuardar = async () => {
  const hayCambios = Object.values(seleccionados).some(Boolean)

  if (!hayCambios) {
    alerta.visible = true
    alerta.mensaje = 'Por favor, selecciona al menos una opción para actualizar.'
    alerta.tipo    = 'error'
    return
  }

  try {
    // ====================================================
    // 🔌 BACKEND — Actualizar tarifa diaria
    // Endpoint esperado: PATCH /api/herramientas/{id}/tarifa-diaria
    // Body: { tarifaDiaria: number }
    //
    // Descomentar cuando el backend esté listo:
    // import { productoServicio } from '@/services/productoServicio'
    // if (seleccionados.tarifaDiaria && valores.tarifaDiaria) {
    //   await productoServicio.actualizarTarifaDiaria(props.idProducto, Number(valores.tarifaDiaria))
    // }
    // ====================================================
    if (seleccionados.tarifaDiaria && valores.tarifaDiaria) {
      console.log('🔌 Actualizar tarifa diaria:', props.idProducto, Number(valores.tarifaDiaria))
    }

    // ====================================================
    // 🔌 BACKEND — Actualizar tarifa de atraso
    // Endpoint esperado: PATCH /api/herramientas/{id}/tarifa-atraso
    // Body: { tarifaAtraso: number }
    //
    // Descomentar cuando el backend esté listo:
    // if (seleccionados.tarifaAtraso && valores.tarifaAtraso) {
    //   await productoServicio.actualizarTarifaAtraso(props.idProducto, Number(valores.tarifaAtraso))
    // }
    // ====================================================
    if (seleccionados.tarifaAtraso && valores.tarifaAtraso) {
      console.log('🔌 Actualizar tarifa atraso:', props.idProducto, Number(valores.tarifaAtraso))
    }

    // ====================================================
    // 🔌 BACKEND — Actualizar valor de reposición
    // Endpoint esperado: PATCH /api/herramientas/{id}/valor-reposicion
    // Body: { valorReposicion: number }
    //
    // Descomentar cuando el backend esté listo:
    // if (seleccionados.valorReposicion && valores.valorReposicion) {
    //   await productoServicio.actualizarValorReposicion(props.idProducto, Number(valores.valorReposicion))
    // }
    // ====================================================
    if (seleccionados.valorReposicion && valores.valorReposicion) {
      console.log('🔌 Actualizar valor reposición:', props.idProducto, Number(valores.valorReposicion))
    }

    alerta.visible = true
    alerta.mensaje = '¡Datos actualizados correctamente!'
    alerta.tipo    = 'exito'
    emit('actualizado')

  } catch (error: unknown) {
    // ====================================================
    // 🔌 BACKEND — Manejo de error de la API
    // Spring Boot devuelve el mensaje en error.response.data
    // ====================================================
    const axiosErr = error as { response?: { data?: string } }
    const msg = axiosErr.response?.data ?? 'Hubo un error al actualizar uno o más campos.'
    alerta.visible = true
    alerta.mensaje = msg
    alerta.tipo    = 'error'
  }
}
</script>

<template>
  <div class="formulario-contenedor">
    <h3 class="formulario-titulo">Configuración de Tarifas</h3>

    <!-- Alerta de resultado -->
    <div v-if="alerta.visible" :class="['alerta', `alerta-${alerta.tipo}`]">
      {{ alerta.mensaje }}
      <button class="alerta-cerrar" type="button" @click="alerta.visible = false">✕</button>
    </div>

    <!-- Tarifa diaria -->
    <div class="campo-grupo">
      <label class="campo-check">
        <input type="checkbox" v-model="seleccionados.tarifaDiaria" />
        <span>Editar Tarifa Diaria</span>
      </label>
      <input
        class="entrada"
        :class="{ 'entrada-desactivada': !seleccionados.tarifaDiaria }"
        type="number" min="0"
        placeholder="Precio diario"
        v-model="valores.tarifaDiaria"
        :disabled="!seleccionados.tarifaDiaria"
      />
    </div>

    <!-- Tarifa de atraso -->
    <div class="campo-grupo">
      <label class="campo-check">
        <input type="checkbox" v-model="seleccionados.tarifaAtraso" />
        <span>Editar Multa por Atraso</span>
      </label>
      <input
        class="entrada"
        :class="{ 'entrada-desactivada': !seleccionados.tarifaAtraso }"
        type="number" min="0"
        placeholder="Costo por atraso"
        v-model="valores.tarifaAtraso"
        :disabled="!seleccionados.tarifaAtraso"
      />
    </div>

    <!-- Valor de reposición -->
    <div class="campo-grupo">
      <label class="campo-check">
        <input type="checkbox" v-model="seleccionados.valorReposicion" />
        <span>Editar Valor de Reposición</span>
      </label>
      <input
        class="entrada"
        :class="{ 'entrada-desactivada': !seleccionados.valorReposicion }"
        type="number" min="1"
        placeholder="Valor reposición"
        v-model="valores.valorReposicion"
        :disabled="!seleccionados.valorReposicion"
      />
    </div>

    <div class="formulario-acciones">
      <button type="button" class="btn-guardar" @click="manejarGuardar">Guardar Cambios</button>
    </div>
  </div>
</template>

<style scoped>
.formulario-contenedor { padding: 8px; min-width: 320px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; max-height: 90vh; }
.formulario-titulo { font-size: 1.1rem; font-weight: 700; color: #156895; }
.campo-grupo { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: #f8f9fa; border-radius: 10px; }
.campo-check { display: flex; align-items: center; gap: 10px; font-size: 0.9rem; font-weight: 600; color: #333; cursor: pointer; }
.campo-check input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; accent-color: #156895; }
.entrada { padding: 9px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; outline: none; transition: border-color 0.2s; }
.entrada:focus { border-color: #156895; }
.entrada-desactivada { background: #e9ecef; color: #aaa; cursor: not-allowed; }
.alerta { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-radius: 8px; font-size: 0.875rem; }
.alerta-exito { background: #e8f5e9; color: #2e7d32; border: 1px solid #a5d6a7; }
.alerta-error  { background: #ffebee; color: #c62828; border: 1px solid #ef9a9a; }
.alerta-cerrar { background: none; border: none; cursor: pointer; font-size: 1rem; color: inherit; }
.formulario-acciones { display: flex; justify-content: center; }
.btn-guardar { padding: 10px 28px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
.btn-guardar:hover { background-color: #0f5070; }
</style>
