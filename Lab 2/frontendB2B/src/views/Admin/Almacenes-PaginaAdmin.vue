<script setup lang="ts">
// =====================================================
// Almacenes-PaginaAdmin.vue
// Gestión de almacenes: crear, editar, eliminar,
// administrar stock por almacén y fijar la tarifa de envío.
// =====================================================

import { ref, reactive, onMounted } from 'vue'
import {
  almacenServicio,
  type AlmacenEntidad,
  type AlmacenNuevo,
  type StockAlmacenProducto,
} from '@/services/almacenServicio'
import { configEnvioServicio } from '@/services/configEnvioServicio'

const almacenes = ref<AlmacenEntidad[]>([])
const cargando = ref(true)
const error = ref<string | null>(null)

// ─── Tarifa de envío ─────────────────────────────────
const valorKm = ref<number>(0)
const valorKmGuardando = ref(false)
const valorKmMensaje = ref<string | null>(null)

// ─── Modal crear/editar almacén ──────────────────────
const modalAbierto = ref(false)
const editandoId = ref<number | null>(null)
const guardando = ref(false)
const formError = ref<string | null>(null)
const form = reactive<AlmacenNuevo>({
  nombre: '',
  direccion: '',
  latitud: 0,
  longitud: 0,
})

// ─── Modal stock ─────────────────────────────────────
const modalStockAbierto = ref(false)
const almacenStock = ref<AlmacenEntidad | null>(null)
const stockItems = ref<StockAlmacenProducto[]>([])
const stockCargando = ref(false)
const stockError = ref<string | null>(null)
const stockGuardandoId = ref<number | null>(null)

// ============ Carga de datos ============

const cargarAlmacenes = async () => {
  cargando.value = true
  error.value = null
  try {
    const resp = await almacenServicio.listar()
    almacenes.value = resp.data
  } catch (err: unknown) {
    console.error('Error al obtener almacenes:', err)
    almacenes.value = []
    error.value = 'Error al cargar los almacenes.'
  } finally {
    cargando.value = false
  }
}

const cargarValorKm = async () => {
  try {
    const resp = await configEnvioServicio.obtener()
    valorKm.value = resp.data.valorKm ?? 0
  } catch (err) {
    console.error('Error al obtener tarifa de envío:', err)
  }
}

const guardarValorKm = async () => {
  valorKmGuardando.value = true
  valorKmMensaje.value = null
  try {
    await configEnvioServicio.actualizar(Number(valorKm.value))
    valorKmMensaje.value = 'Tarifa guardada correctamente.'
  } catch (err) {
    console.error('Error al guardar tarifa:', err)
    valorKmMensaje.value = 'No se pudo guardar la tarifa.'
  } finally {
    valorKmGuardando.value = false
  }
}

// ============ Crear / editar almacén ============

const abrirCrear = () => {
  editandoId.value = null
  form.nombre = ''
  form.direccion = ''
  form.latitud = 0
  form.longitud = 0
  formError.value = null
  modalAbierto.value = true
}

const abrirEditar = (a: AlmacenEntidad) => {
  editandoId.value = a.almacenId
  form.nombre = a.nombre
  form.direccion = a.direccion
  form.latitud = a.latitud
  form.longitud = a.longitud
  formError.value = null
  modalAbierto.value = true
}

const cerrarModal = () => {
  modalAbierto.value = false
}

const guardarAlmacen = async () => {
  formError.value = null
  if (!form.nombre.trim()) {
    formError.value = 'El nombre es obligatorio.'
    return
  }
  if (form.latitud === null || form.longitud === null) {
    formError.value = 'Latitud y longitud son obligatorias.'
    return
  }
  guardando.value = true
  const payload: AlmacenNuevo = {
    nombre: form.nombre.trim(),
    direccion: form.direccion.trim(),
    latitud: Number(form.latitud),
    longitud: Number(form.longitud),
  }
  try {
    if (editandoId.value === null) {
      await almacenServicio.crear(payload)
    } else {
      await almacenServicio.actualizar(editandoId.value, payload)
    }
    modalAbierto.value = false
    await cargarAlmacenes()
  } catch (err) {
    console.error('Error al guardar almacén:', err)
    formError.value = 'No se pudo guardar el almacén.'
  } finally {
    guardando.value = false
  }
}

const eliminarAlmacen = async (a: AlmacenEntidad) => {
  if (!confirm(`¿Eliminar el almacén "${a.nombre}"? Esta acción no se puede deshacer.`)) return
  try {
    await almacenServicio.eliminar(a.almacenId)
    await cargarAlmacenes()
  } catch (err) {
    console.error('Error al eliminar almacén:', err)
    alert('No se pudo eliminar el almacén.')
  }
}

// ============ Stock por almacén ============

const abrirStock = async (a: AlmacenEntidad) => {
  almacenStock.value = a
  modalStockAbierto.value = true
  stockCargando.value = true
  stockError.value = null
  stockItems.value = []
  try {
    const resp = await almacenServicio.listarStock(a.almacenId)
    stockItems.value = resp.data
  } catch (err) {
    console.error('Error al obtener stock:', err)
    stockError.value = 'No se pudo cargar el stock del almacén.'
  } finally {
    stockCargando.value = false
  }
}

const cerrarStock = () => {
  modalStockAbierto.value = false
  almacenStock.value = null
}

const guardarStockItem = async (item: StockAlmacenProducto) => {
  if (!almacenStock.value) return
  if (item.stockDisponible === null || item.stockDisponible < 0) {
    stockError.value = 'El stock debe ser un número mayor o igual a 0.'
    return
  }
  stockGuardandoId.value = item.productoId
  stockError.value = null
  try {
    await almacenServicio.actualizarStock(
      almacenStock.value.almacenId,
      item.productoId,
      Number(item.stockDisponible),
    )
  } catch (err: unknown) {
    console.error('Error al guardar stock:', err)
    const axiosErr = err as { response?: { data?: { error?: string } } }
    stockError.value = axiosErr.response?.data?.error ?? 'No se pudo guardar el stock.'
  } finally {
    stockGuardandoId.value = null
  }
}

onMounted(() => {
  cargarAlmacenes()
  cargarValorKm()
})
</script>

<template>
  <div class="pagina">
    <div class="encabezado">
      <h1 class="titulo-pagina">Almacenes</h1>
      <button class="btn-agregar" @click="abrirCrear">+ Agregar almacén</button>
    </div>

    <!-- Tarifa de envío -->
    <div class="tarifa-panel">
      <div class="tarifa-info">
        <span class="tarifa-titulo">Tarifa de envío</span>
        <span class="tarifa-desc">Valor cobrado por kilómetro (última milla).</span>
      </div>
      <div class="tarifa-controles">
        <label class="tarifa-label">$/km</label>
        <input v-model.number="valorKm" type="number" min="0" step="1" class="tarifa-input" />
        <button class="btn-agregar" :disabled="valorKmGuardando" @click="guardarValorKm">
          {{ valorKmGuardando ? 'Guardando…' : 'Guardar' }}
        </button>
        <span v-if="valorKmMensaje" class="tarifa-mensaje">{{ valorKmMensaje }}</span>
      </div>
    </div>

    <div v-if="cargando" class="estado">Cargando almacenes...</div>
    <div v-else-if="error" class="estado error">{{ error }}</div>

    <table v-else class="tabla-almacenes">
      <thead>
        <tr>
          <th>Nombre</th>
          <th>Dirección</th>
          <th>Latitud</th>
          <th>Longitud</th>
          <th>Acciones</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="almacen in almacenes" :key="almacen.almacenId">
          <td>{{ almacen.nombre }}</td>
          <td>{{ almacen.direccion }}</td>
          <td>{{ almacen.latitud }}</td>
          <td>{{ almacen.longitud }}</td>
          <td>
            <div class="celda-acciones">
              <button class="btn-mini btn-stock" @click="abrirStock(almacen)">Stock</button>
              <button class="btn-mini btn-editar" @click="abrirEditar(almacen)">Editar</button>
              <button class="btn-mini btn-eliminar" @click="eliminarAlmacen(almacen)">Eliminar</button>
            </div>
          </td>
        </tr>
        <tr v-if="almacenes.length === 0">
          <td colspan="5" class="vacio">No hay almacenes registrados</td>
        </tr>
      </tbody>
    </table>

    <!-- Modal crear/editar -->
    <div v-if="modalAbierto" class="modal-overlay" @click="cerrarModal">
      <div class="modal-contenido" @click.stop>
        <button class="modal-cerrar" @click="cerrarModal">✕</button>
        <h2 class="modal-titulo">{{ editandoId === null ? 'Agregar almacén' : 'Editar almacén' }}</h2>

        <div class="campo">
          <label>Nombre</label>
          <input v-model="form.nombre" type="text" placeholder="Almacén Central" />
        </div>
        <div class="campo">
          <label>Dirección</label>
          <input v-model="form.direccion" type="text" placeholder="Av. Siempre Viva 123" />
        </div>
        <div class="campo-fila">
          <div class="campo">
            <label>Latitud</label>
            <input v-model.number="form.latitud" type="number" step="0.000001" placeholder="-33.45" />
          </div>
          <div class="campo">
            <label>Longitud</label>
            <input v-model.number="form.longitud" type="number" step="0.000001" placeholder="-70.66" />
          </div>
        </div>

        <p v-if="formError" class="form-error">{{ formError }}</p>

        <div class="modal-acciones">
          <button class="btn-secundario" @click="cerrarModal">Cancelar</button>
          <button class="btn-agregar" :disabled="guardando" @click="guardarAlmacen">
            {{ guardando ? 'Guardando…' : 'Guardar' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Modal stock -->
    <div v-if="modalStockAbierto" class="modal-overlay" @click="cerrarStock">
      <div class="modal-contenido modal-ancho" @click.stop>
        <button class="modal-cerrar" @click="cerrarStock">✕</button>
        <h2 class="modal-titulo">Stock — {{ almacenStock?.nombre }}</h2>

        <p v-if="stockError" class="form-error">{{ stockError }}</p>
        <div v-if="stockCargando" class="estado">Cargando stock…</div>

        <table v-else class="tabla-stock">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Stock en almacén</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in stockItems" :key="item.productoId">
              <td>{{ item.nombreProducto }}</td>
              <td>
                <input v-model.number="item.stockDisponible" type="number" min="0" step="1" class="stock-input" />
              </td>
              <td>
                <button
                  class="btn-mini btn-stock"
                  :disabled="stockGuardandoId === item.productoId"
                  @click="guardarStockItem(item)"
                >
                  {{ stockGuardandoId === item.productoId ? '…' : 'Guardar' }}
                </button>
              </td>
            </tr>
            <tr v-if="stockItems.length === 0">
              <td colspan="3" class="vacio">No hay productos.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pagina { display: flex; flex-direction: column; gap: 16px; }
.encabezado { display: flex; justify-content: space-between; align-items: center; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
.descripcion { color: #666; font-size: 0.9rem; margin: 0; }
.btn-agregar { padding: 10px 20px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.9rem; font-weight: 600; cursor: pointer; }
.btn-agregar:hover { background-color: #0f5070; }
.btn-agregar:disabled { opacity: 0.6; cursor: not-allowed; }
.estado { padding: 12px; color: #444; }
.estado.error { color: #b00020; }

/* Tarifa */
.tarifa-panel { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; background: #f0f7ff; border: 1px solid #cfe2ff; border-radius: 10px; padding: 14px 18px; }
.tarifa-info { display: flex; flex-direction: column; }
.tarifa-titulo { font-weight: 700; color: #0d4b6e; }
.tarifa-desc { font-size: 0.82rem; color: #567; }
.tarifa-controles { display: flex; align-items: center; gap: 10px; }
.tarifa-label { font-weight: 600; color: #334155; }
.tarifa-input { width: 120px; padding: 8px 10px; border: 1px solid #ccc; border-radius: 8px; }
.tarifa-mensaje { font-size: 0.82rem; color: #0a5c36; }

.tabla-almacenes { width: 100%; border-collapse: separate; border-spacing: 0; background: #fff; border: 1px solid #e6e6e6; border-radius: 10px; }
.tabla-almacenes th, .tabla-almacenes td { padding: 12px 16px; border-bottom: 1px solid #f2f2f2; text-align: left; font-size: 0.9rem; }
.tabla-almacenes th { color: #ffffff; font-weight: 600; background: #156895; border-bottom: none; }
.tabla-almacenes th:first-child { border-top-left-radius: 9px; }
.tabla-almacenes th:last-child { border-top-right-radius: 9px; }
.tabla-almacenes tbody tr:last-child td { border-bottom: none; }
.vacio { text-align: center; color: #777; padding: 16px; }

.celda-acciones { display: flex; gap: 6px; }
.btn-mini { padding: 5px 10px; border-radius: 6px; font-size: 0.8rem; font-weight: 600; border: 1px solid transparent; cursor: pointer; }
.btn-stock { background: #e8f0fe; color: #156895; border-color: #b6d4fe; }
.btn-editar { background: #fff8e1; color: #8a6d00; border-color: #ffe08a; }
.btn-eliminar { background: #ffebee; color: #c62828; border-color: #ef9a9a; }
.btn-mini:disabled { opacity: 0.6; cursor: not-allowed; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(15,23,42,0.5); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal-contenido { background: #fff; border-radius: 12px; padding: 26px; width: 92%; max-width: 460px; position: relative; box-shadow: 0 12px 40px rgba(0,0,0,0.2); }
.modal-ancho { max-width: 620px; }
.modal-cerrar { position: absolute; top: 12px; right: 14px; background: none; border: none; font-size: 1.1rem; cursor: pointer; color: #666; }
.modal-titulo { font-size: 1.15rem; font-weight: 700; color: #1a1a2e; margin-bottom: 16px; }
.campo { display: flex; flex-direction: column; gap: 5px; margin-bottom: 12px; flex: 1; }
.campo label { font-size: 0.82rem; color: #475569; font-weight: 600; }
.campo input { padding: 9px 11px; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem; }
.campo-fila { display: flex; gap: 12px; }
.form-error { color: #b00020; font-size: 0.85rem; margin: 4px 0 12px; }
.modal-acciones { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.btn-secundario { padding: 10px 18px; background: #eef2f7; color: #334155; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; }

.tabla-stock { width: 100%; border-collapse: collapse; margin-top: 8px; }
.tabla-stock th, .tabla-stock td { padding: 9px 12px; border-bottom: 1px solid #eee; text-align: left; font-size: 0.88rem; }
.stock-input { width: 110px; padding: 6px 8px; border: 1px solid #ccc; border-radius: 6px; }
</style>
