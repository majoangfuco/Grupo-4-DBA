<script setup lang="ts">
// =====================================================
// ListaProductos.vue
// Tabla de productos con acciones:
//   - Ver detalle de unidades (navegación)
// =====================================================

// --- Tipos ---
interface Producto {
  producto_ID: number
  categoria_ID: number
  nombre_producto: string
  descripcion: string
  precio: number
  stock: number
  sku: string
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
}>()

// --- Helpers de ordenamiento ---
const estaOrdenandoPor = (clave: string) => props.configOrden?.clave === clave
const obtenerDireccion = (clave: string): 'asc' | 'desc' =>
  props.configOrden?.clave === clave ? props.configOrden.direccion : 'asc'

// --- Columnas de la tabla ---
const columnas = [
  { clave: 'nombre_producto',   etiqueta: 'Nombre' },
  { clave: 'descripcion',       etiqueta: 'Descripción' },
  { clave: 'precio',            etiqueta: 'Precio' }
]
</script>

<template>
  <div class="tabla-contenedor">
    <table class="tabla" aria-label="Lista de productos">

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
            <p class="texto-vacio">No se encontraron productos registrados en el inventario.</p>
            <p class="texto-vacio-sub">Puedes agregar una nueva usando el botón "Agregar Nuevo Producto".</p>
          </td>
        </tr>

        <!-- Filas de datos -->
        <tr v-else v-for="prod in productos" :key="prod.producto_ID" class="fila-producto">
          <td v-for="col in columnas" :key="col.clave" class="celda">
            <span v-if="col.clave === 'precio'">$ {{ prod.precio != null ? Number(prod.precio).toLocaleString('es-CL') : '0' }}</span>
            <span v-else>{{ prod[col.clave as keyof typeof prod] }}</span>
          </td>
        </tr>

      </tbody>
    </table>

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
</style>
