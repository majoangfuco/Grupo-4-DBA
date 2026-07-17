<script setup lang="ts">
import { ref, computed } from 'vue'
import { productoServicio } from '@/services/productoServicio'
import { categoriaServicio, type CategoriaEntidad } from '@/services/categoriaServicio'

// ============= PROPS & EMITS =============
const props = defineProps<{
  categorias: CategoriaEntidad[]
}>()

const emit = defineEmits<{
  (e: 'descuentoAplicado'): void
  (e: 'cancelado'): void
}>()

// ============= ESTADO =============
const categoriaSeleccionada = ref<number | null>(null)
const porcentaje = ref<number>(5)
const cargando = ref(false)
const error = ref<string | null>(null)
const exito = ref(false)

// ============= VALIDACIONES =============
const esValido = computed(() => {
  return categoriaSeleccionada.value !== null && 
         porcentaje.value >= 1 && 
         porcentaje.value <= 100
})

const nombreCategoriaSeleccionada = computed(() => {
  if (!categoriaSeleccionada.value) return ''
  return props.categorias.find(c => c.categoria_ID === categoriaSeleccionada.value)?.nombre_Categoria || ''
})

// ============= MANEJADORES =============
const aplicarDescuento = async () => {
  if (!esValido.value || !categoriaSeleccionada.value) return

  cargando.value = true
  error.value = null

  try {
    await productoServicio.aplicarDescuentoPorCategoria(categoriaSeleccionada.value, porcentaje.value)
    exito.value = true
    
    // Mostrar mensaje de éxito por 2 segundos
    setTimeout(() => {
      emit('descuentoAplicado')
      resetearFormulario()
    }, 1200)
  } catch (err: unknown) {
    console.error('Error al aplicar descuento:', err)
    const axiosErr = err as { response?: { data?: { message?: string } } }
    error.value = axiosErr.response?.data?.message ?? 'Error al aplicar el descuento'
  } finally {
    cargando.value = false
  }
}

const resetearFormulario = () => {
  categoriaSeleccionada.value = null
  porcentaje.value = 5
  exito.value = false
}

const cerrar = () => {
  resetearFormulario()
  emit('cancelado')
}
</script>

<template>
  <div class="formulario-descuento">
    <div class="encabezado">
      <h2 class="titulo">Aplicar Descuento por Categoría</h2>
      <button class="btn-cerrar" @click="cerrar">×</button>
    </div>

    <div v-if="exito" class="mensaje-exito">
      ✓ Descuento aplicado correctamente a {{ nombreCategoriaSeleccionada }}
    </div>

    <div v-if="error" class="mensaje-error">
      ✕ {{ error }}
    </div>

    <form class="formulario" @submit.prevent="aplicarDescuento">
      <!-- Seleccionar Categoría -->
      <div class="campo">
        <label for="categoria" class="etiqueta">Categoría</label>
        <select
          id="categoria"
          v-model.number="categoriaSeleccionada"
          class="entrada"
          required
        >
          <option value="">Selecciona una categoría</option>
          <option v-for="cat in categorias" :key="cat.categoria_ID" :value="cat.categoria_ID">
            {{ cat.nombre_Categoria }}
          </option>
        </select>
      </div>

      <!-- Porcentaje de Descuento -->
      <div class="campo">
        <label for="porcentaje" class="etiqueta">Porcentaje de Descuento</label>
        <div class="grupo-porcentaje">
          <input
            id="porcentaje"
            v-model.number="porcentaje"
            type="range"
            min="1"
            max="100"
            step="1"
            class="rango"
          />
          <div class="valor-porcentaje">
            <strong>{{ porcentaje }}</strong>%
          </div>
        </div>
        <div class="ayuda-porcentaje">
          Reduce el precio hasta {{ porcentaje }}% en todos los productos de la categoría
        </div>
      </div>

      <!-- Información Previa -->
      <div v-if="categoriaSeleccionada" class="vista-previa">
        <div class="preview-titulo">Vista Previa</div>
        <div class="preview-item">
          <span>Categoría:</span>
          <strong>{{ nombreCategoriaSeleccionada }}</strong>
        </div>
        <div class="preview-item">
          <span>Descuento:</span>
          <strong class="descuento-valor">-{{ porcentaje }}%</strong>
        </div>
      </div>

      <!-- Botones -->
      <div class="acciones">
        <button type="button" class="btn-cancelar" @click="cerrar">
          Cancelar
        </button>
        <button
          type="submit"
          class="btn-aplicar"
          :disabled="!esValido || cargando"
        >
          {{ cargando ? 'Aplicando...' : 'Aplicar Descuento' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.formulario-descuento {
  background: white;
  border-radius: 12px;
  padding: 20px;
  max-width: 450px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.encabezado {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  border-bottom: 1px solid #e6e6e6;
  padding-bottom: 12px;
}

.titulo {
  font-size: 1.1rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.btn-cerrar {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #999;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s;
}

.btn-cerrar:hover {
  color: #333;
}

.mensaje-exito {
  background: #e8f5e9;
  color: #2e7d32;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 0.9rem;
  border-left: 4px solid #4caf50;
}

.mensaje-error {
  background: #ffebee;
  color: #c62828;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 0.9rem;
  border-left: 4px solid #f44336;
}

.formulario {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.campo {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.etiqueta {
  font-size: 0.9rem;
  font-weight: 600;
  color: #333;
}

.entrada {
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 0.9rem;
  background: white;
  color: #222;
  transition: border-color 0.2s;
}

.entrada:focus {
  outline: none;
  border-color: #156895;
  box-shadow: 0 0 0 2px rgba(21, 104, 149, 0.1);
}

.grupo-porcentaje {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rango {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: #ddd;
  outline: none;
  -webkit-appearance: none;
  appearance: none;
}

.rango::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #156895;
  cursor: pointer;
  transition: background 0.2s;
}

.rango::-webkit-slider-thumb:hover {
  background: #0f5070;
}

.rango::-moz-range-thumb {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #156895;
  cursor: pointer;
  border: none;
  transition: background 0.2s;
}

.rango::-moz-range-thumb:hover {
  background: #0f5070;
}

.valor-porcentaje {
  min-width: 50px;
  text-align: center;
  font-size: 0.95rem;
  color: #333;
}

.ayuda-porcentaje {
  font-size: 0.8rem;
  color: #999;
}

.vista-previa {
  background: #f9f9f9;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-titulo {
  font-size: 0.85rem;
  font-weight: 600;
  color: #666;
  text-transform: uppercase;
  margin-bottom: 4px;
}

.preview-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
  color: #555;
}

.descuento-valor {
  color: #d32f2f;
  font-size: 1rem;
}

.acciones {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.btn-cancelar,
.btn-aplicar {
  flex: 1;
  padding: 12px 16px;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-cancelar {
  background: #f0f0f0;
  color: #333;
}

.btn-cancelar:hover {
  background: #e0e0e0;
}

.btn-aplicar {
  background: #156895;
  color: white;
}

.btn-aplicar:hover:not(:disabled) {
  background: #0f5070;
}

.btn-aplicar:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
