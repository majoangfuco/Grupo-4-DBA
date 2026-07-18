<script setup lang="ts">
// =====================================================
// FormularioGestionCategorias.vue
// CRUD de categorías desde el frontend (admin):
// agregar, renombrar y desactivar (eliminación lógica).
// =====================================================

import { ref, onMounted } from 'vue'
import { categoriaServicio, type CategoriaEntidad } from '@/services/categoriaServicio'

const emit = defineEmits<{
  (e: 'actualizado'): void
  (e: 'cerrar'): void
}>()

const categorias = ref<CategoriaEntidad[]>([])
const cargando = ref(false)
const error = ref('')

// Nueva categoría
const nuevoNombre = ref('')
const creando = ref(false)

// Edición en línea
const editandoId = ref<number | null>(null)
const nombreEditado = ref('')

const cargarCategorias = async () => {
  cargando.value = true
  error.value = ''
  try {
    // incluirInactivas=true para poder administrarlas todas
    const resp = await categoriaServicio.listar(true)
    categorias.value = resp.data
  } catch {
    error.value = 'No se pudieron cargar las categorías.'
  } finally {
    cargando.value = false
  }
}

const crear = async () => {
  error.value = ''
  if (!nuevoNombre.value.trim()) {
    error.value = 'El nombre de la categoría es obligatorio.'
    return
  }
  creando.value = true
  try {
    await categoriaServicio.crear({ nombre_Categoria: nuevoNombre.value.trim() })
    nuevoNombre.value = ''
    await cargarCategorias()
    emit('actualizado')
  } catch (err: unknown) {
    error.value = extraerError(err, 'No se pudo crear la categoría.')
  } finally {
    creando.value = false
  }
}

const empezarEdicion = (cat: CategoriaEntidad) => {
  editandoId.value = cat.categoria_ID
  nombreEditado.value = cat.nombre_Categoria
  error.value = ''
}

const cancelarEdicion = () => {
  editandoId.value = null
  nombreEditado.value = ''
}

const guardarEdicion = async (cat: CategoriaEntidad) => {
  error.value = ''
  if (!nombreEditado.value.trim()) {
    error.value = 'El nombre no puede quedar vacío.'
    return
  }
  try {
    await categoriaServicio.actualizar(cat.categoria_ID, { nombre_Categoria: nombreEditado.value.trim() })
    cancelarEdicion()
    await cargarCategorias()
    emit('actualizado')
  } catch (err: unknown) {
    error.value = extraerError(err, 'No se pudo actualizar la categoría.')
  }
}

const eliminar = async (cat: CategoriaEntidad) => {
  error.value = ''
  if (!confirm(`¿Desactivar la categoría "${cat.nombre_Categoria}"?`)) return
  try {
    await categoriaServicio.eliminar(cat.categoria_ID)
    await cargarCategorias()
    emit('actualizado')
  } catch (err: unknown) {
    error.value = extraerError(err, 'No se pudo eliminar la categoría.')
  }
}

const extraerError = (err: unknown, fallback: string): string => {
  const e = err as { response?: { data?: unknown } }
  const data = e.response?.data
  if (typeof data === 'string' && data) return data
  if (data && typeof data === 'object' && 'message' in data) {
    return String((data as { message: string }).message)
  }
  return fallback
}

onMounted(cargarCategorias)
</script>

<template>
  <div class="gestion-categorias">
    <button class="modal-cerrar" @click="emit('cerrar')">✕</button>
    <h3 class="titulo">Gestión de categorías</h3>

    <!-- Alta de categoría -->
    <form class="fila-nueva" @submit.prevent="crear">
      <input
        v-model="nuevoNombre"
        class="entrada"
        type="text"
        placeholder="Nombre de la nueva categoría"
      />
      <button class="btn-crear" type="submit" :disabled="creando">
        {{ creando ? 'Agregando...' : '+ Agregar' }}
      </button>
    </form>

    <p v-if="error" class="msg-error">{{ error }}</p>

    <!-- Lista de categorías -->
    <div class="lista">
      <p v-if="cargando" class="estado">Cargando...</p>
      <p v-else-if="categorias.length === 0" class="estado">No hay categorías registradas.</p>

      <div
        v-for="cat in categorias"
        v-else
        :key="cat.categoria_ID"
        class="item"
        :class="{ inactiva: !cat.estado_Categoria }"
      >
        <!-- Modo edición -->
        <template v-if="editandoId === cat.categoria_ID">
          <input v-model="nombreEditado" class="entrada entrada-edicion" type="text" />
          <div class="acciones">
            <button class="btn-mini btn-guardar" @click="guardarEdicion(cat)">Guardar</button>
            <button class="btn-mini btn-cancelar" @click="cancelarEdicion">Cancelar</button>
          </div>
        </template>

        <!-- Modo lectura -->
        <template v-else>
          <span class="nombre">
            {{ cat.nombre_Categoria }}
            <span v-if="!cat.estado_Categoria" class="badge-inactiva">inactiva</span>
          </span>
          <div class="acciones">
            <button class="btn-mini btn-editar" @click="empezarEdicion(cat)">Editar</button>
            <button
              v-if="cat.estado_Categoria"
              class="btn-mini btn-eliminar"
              @click="eliminar(cat)"
            >
              Eliminar
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gestion-categorias { position: relative; min-width: 420px; max-width: 520px; }
.modal-cerrar { position: absolute; top: -8px; right: -8px; background: none; border: none; font-size: 1.1rem; cursor: pointer; color: #666; }
.titulo { font-size: 1.2rem; font-weight: 700; color: #1a1a2e; margin: 0 0 18px; }

.fila-nueva { display: flex; gap: 8px; margin-bottom: 12px; }
.entrada { flex: 1; padding: 9px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; outline: none; }
.entrada:focus { border-color: #156895; }
.btn-crear { padding: 9px 16px; background: #156895; color: #fff; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; white-space: nowrap; }
.btn-crear:disabled { opacity: 0.6; cursor: not-allowed; }

.msg-error { color: #b02a1f; font-size: 0.85rem; margin: 6px 0; }

.lista { max-height: 340px; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
.estado { color: #888; font-style: italic; text-align: center; padding: 16px 0; }
.item { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; border: 1px solid #eee; border-radius: 8px; }
.item.inactiva { background: #f7f7f7; }
.nombre { font-size: 0.92rem; color: #333; }
.badge-inactiva { font-size: 0.72rem; color: #a33; background: #fdecec; border-radius: 10px; padding: 2px 8px; margin-left: 8px; }
.entrada-edicion { flex: 1; }

.acciones { display: flex; gap: 6px; }
.btn-mini { padding: 5px 10px; border: none; border-radius: 6px; font-size: 0.8rem; font-weight: 600; cursor: pointer; }
.btn-editar { background: #e8f0f6; color: #156895; }
.btn-eliminar { background: #fdecec; color: #c0392b; }
.btn-guardar { background: #156895; color: #fff; }
.btn-cancelar { background: #eee; color: #555; }
</style>
