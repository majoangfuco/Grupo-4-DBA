<script setup lang="ts">
// =====================================================
// ListaProductos.vue
// Tabla de tipos de herramientas con acciones:
//   - Agregar stock (modal)
//   - Editar tarifas (modal)
//   - Ver detalle de unidades (navegación)
// =====================================================

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import FormularioEditarProducto from '@/components/productos/FormularioEditarProducto.vue'
import FormularioEliminarProducto from '@/components/productos/FormularioEliminarProducto.vue'

const router = useRouter()

// --- Tipos ---
interface Producto {
  idProducto: number
  nombre: string
  categoria: string
  valorReposicion: number
  tarifaDiaria: number
  tarifaAtraso: number
  stock: number
}

interface ConfigOrden {
  clave: string
  direccion: 'asc' | 'desc'
}

// --- Props ---
const props = defineProps<{
  productos: Producto[]
  cargando: boolean
  error: string | null
  configOrden?: ConfigOrden
}>()

// --- Eventos ---
const emit = defineEmits<{
  (e: 'ordenar', clave: string): void
  (e: 'reintentar'): void
  (e: 'actualizar'): void
}>()

// --- Estado de modales ---
const modalEditarAbierto    = ref(false)
const modalEliminarAbierto  = ref(false)
const productoSeleccionado  = ref<Producto | null>(null)

const abrirModalEditar = (producto: Producto) => {
  productoSeleccionado.value = producto
  modalEditarAbierto.value   = true
}

const cerrarModalEditar = () => {
  modalEditarAbierto.value   = false
  productoSeleccionado.value = null
}

const abrirModalEliminar = (producto: Producto) => {
  productoSeleccionado.value  = producto
  modalEliminarAbierto.value  = true
}

const cerrarModalEliminar = () => {
  modalEliminarAbierto.value  = false
  productoSeleccionado.value  = null
}

const manejarActualizado = () => {
  cerrarModalEditar()
  emit('actualizar')
}

const manejarEliminado = () => {
  cerrarModalEliminar()
  emit('actualizar')
}

// --- Navegación a detalle de unidades ---
const verDetalle = (id: number) => {
  // ====================================================
  // 🔌 BACKEND — Ruta a la página de herramientas unitarias
  // Esta página deberá cargar GET /api/herramientas/{id}/unidades
  // Recuerda agregar la ruta en router/index.ts:
  // { path: 'productos/:id', component: DetalleProducto }
  // ====================================================
  router.push(`/productosAdmin/${id}`)
}

// --- Helpers de ordenamiento ---
const estaOrdenandoPor = (clave: string) => props.configOrden?.clave === clave
const obtenerDireccion = (clave: string): 'asc' | 'desc' =>
  props.configOrden?.clave === clave ? props.configOrden.direccion : 'asc'

// --- Columnas de la tabla ---
const columnas = [
  { clave: 'idProducto',       etiqueta: 'ID' },
  { clave: 'nombre',           etiqueta: 'Nombre' },
  { clave: 'categoria',        etiqueta: 'Categoría' },
  { clave: 'valorReposicion',  etiqueta: 'Valor Reposición' },
  { clave: 'tarifaDiaria',     etiqueta: 'Tarifa Diaria' },
  { clave: 'tarifaAtraso',     etiqueta: 'Tarifa Atraso' },
  { clave: 'stock',            etiqueta: 'Disponibles' },
]
</script>

<template>
  <div class="tabla-contenedor">
    <table class="tabla" aria-label="Lista de herramientas">

      <!-- Encabezado -->
      <thead class="tabla-encabezado">
        <tr>
          <th
            v-for="col in columnas"
            :key="col.clave"
            class="celda-encabezado"
            @click="emit('ordenar', col.clave)"
          >
            <span class="encabezado-contenido">
              {{ col.etiqueta }}
              <span class="icono-orden">
                {{ estaOrdenandoPor(col.clave) ? (obtenerDireccion(col.clave) === 'asc' ? '↑' : '↓') : '⇅' }}
              </span>
            </span>
          </th>
          <th class="celda-encabezado">Acciones</th>
        </tr>
      </thead>

      <!-- Cuerpo -->
      <tbody>

        <!-- Cargando -->
        <tr v-if="cargando">
          <td colspan="8" class="celda-estado">
            <span class="spinner" aria-hidden="true"></span>
            <span class="texto-estado">Cargando datos...</span>
          </td>
        </tr>

        <!-- Error -->
        <tr v-else-if="error">
          <td colspan="8" class="celda-estado">
            <p class="texto-error">{{ error }}</p>
            <button class="btn-reintentar" @click="emit('reintentar')">↺ Reintentar</button>
          </td>
        </tr>

        <!-- Lista vacía -->
        <tr v-else-if="productos.length === 0">
          <td colspan="8" class="celda-estado">
            <p class="texto-vacio">No se encontraron herramientas registradas en el inventario.</p>
            <p class="texto-vacio-sub">Puedes agregar una nueva usando el botón "Agregar Nueva Herramienta".</p>
          </td>
        </tr>

        <!-- Filas de datos -->
        <tr v-else v-for="prod in productos" :key="prod.idProducto" class="fila-producto">
          <td class="celda">{{ prod.idProducto }}</td>
          <td class="celda">{{ prod.nombre }}</td>
          <td class="celda">{{ prod.categoria }}</td>
          <td class="celda">$ {{ prod.valorReposicion.toLocaleString('es-CL') }}</td>
          <td class="celda">$ {{ prod.tarifaDiaria.toLocaleString('es-CL') }}</td>
          <td class="celda">$ {{ prod.tarifaAtraso.toLocaleString('es-CL') }}</td>
          <td class="celda">{{ prod.stock }}</td>
          <td class="celda">
            <div class="acciones">

              <!-- Botón: Editar tarifas -->
              <button
                class="btn-accion btn-editar"
                title="Editar tarifas"
                @click="abrirModalEditar(prod)"
              >
                ✏️
              </button>

              <!-- Botón: Eliminar herramienta -->
              <button
                class="btn-accion btn-eliminar"
                title="Eliminar herramienta"
                @click="abrirModalEliminar(prod)"
              >
                🗑️
              </button>

              <!-- Botón: Ver unidades individuales -->
              <button
                class="btn-accion btn-detalle"
                title="Ver herramientas unitarias"
                @click="verDetalle(prod.idProducto)"
              >
                🔧
              </button>

            </div>
          </td>
        </tr>

      </tbody>
    </table>

    <!-- Modal: Editar producto -->
    <Teleport to="body">
      <div v-if="modalEditarAbierto" class="modal-overlay" @click.self="cerrarModalEditar">
        <div class="modal-contenido">
          <button class="modal-cerrar" @click="cerrarModalEditar" aria-label="Cerrar">✕</button>
          <FormularioEditarProducto
            v-if="productoSeleccionado"
            :id-producto="productoSeleccionado.idProducto"
            @actualizado="manejarActualizado"
          />
        </div>
      </div>
    </Teleport>

    <!-- Modal: Eliminar producto -->
    <Teleport to="body">
      <div v-if="modalEliminarAbierto" class="modal-overlay" @click.self="cerrarModalEliminar">
        <div class="modal-contenido">
          <button class="modal-cerrar" @click="cerrarModalEliminar" aria-label="Cerrar">✕</button>
          <FormularioEliminarProducto
            v-if="productoSeleccionado"
            :id-producto="productoSeleccionado.idProducto"
            :nombre-producto="productoSeleccionado.nombre"
            @eliminado="manejarEliminado"
            @cancelado="cerrarModalEliminar"
          />
        </div>
      </div>
    </Teleport>

  </div>
</template>

<style scoped>
.tabla-contenedor { width: 100%; overflow-x: auto; border-radius: 10px; border: 1px solid #e0e0e0; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.9rem; min-width: 800px; }
.tabla-encabezado { background-color: #156895; color: white; }
.celda-encabezado { padding: 12px 16px; text-align: center; font-weight: 600; cursor: pointer; user-select: none; white-space: nowrap; }
.celda-encabezado:hover { background-color: #0f5070; }
.encabezado-contenido { display: inline-flex; align-items: center; gap: 6px; }
.icono-orden { font-size: 0.8rem; opacity: 0.8; }
.fila-producto { border-bottom: 1px solid #f0f0f0; transition: background-color 0.15s; }
.fila-producto:hover { background-color: #f0f7ff; }
.celda { padding: 12px 16px; text-align: center; color: #333; }
.celda-estado { padding: 40px 16px; text-align: center; }
.spinner { display: inline-block; width: 24px; height: 24px; border: 3px solid #e0e0e0; border-top-color: #156895; border-radius: 50%; animation: girar 0.8s linear infinite; vertical-align: middle; margin-right: 10px; }
@keyframes girar { to { transform: rotate(360deg); } }
.texto-estado { color: #666; font-size: 0.9rem; }
.texto-error { color: #d32f2f; margin-bottom: 10px; }
.btn-reintentar { padding: 6px 14px; border: 1px solid #d32f2f; color: #d32f2f; background: none; border-radius: 6px; cursor: pointer; font-size: 0.85rem; }
.btn-reintentar:hover { background: #fff0f0; }
.texto-vacio { color: #555; margin-bottom: 6px; }
.texto-vacio-sub { color: #888; font-size: 0.85rem; }
.acciones { display: flex; justify-content: center; gap: 8px; }
.btn-accion { background: none; border: 1px solid #e0e0e0; border-radius: 8px; padding: 6px 10px; cursor: pointer; font-size: 1rem; transition: border-color 0.2s, filter 0.2s; }
.btn-accion:hover { border-color: #156895; filter: drop-shadow(0 0 6px rgba(21,104,149,0.4)); }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal-contenido { background: white; border-radius: 12px; padding: 28px; min-width: 360px; max-width: 90vw; position: relative; box-shadow: 0 8px 32px rgba(0,0,0,0.18); }
.modal-cerrar { position: absolute; top: 12px; right: 14px; background: none; border: none; font-size: 1.1rem; cursor: pointer; color: #666; }
.modal-cerrar:hover { color: #333; }
</style>
