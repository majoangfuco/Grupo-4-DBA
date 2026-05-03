<script setup lang="ts">
// =====================================================
// listaOrdenes-vistaCliente.vue
// Tabla de órdenes para el cliente autenticado.
//   - Recibe las órdenes ya filtradas como prop.
//   - El padre (Ordenes-PaginaClientes.vue) llama a
//     GET /api/ordenes/usuario/{id} para traer solo las del usuario.
// =====================================================

import type { Orden } from '@/services/ordenesServicio'
import { ref } from 'vue'
import ModalFactura from '@/components/ordenes/ModalFactura.vue'

interface ConfigOrden {
  clave: string
  direccion: 'asc' | 'desc'
}

// --- Props ---
const props = defineProps<{
  ordenes: Orden[]
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

const columnas: Array<{ clave: string; etiqueta: string }> = [
  { clave: 'orden_ID',    etiqueta: 'N° Orden' },
  { clave: 'fecha_Orden', etiqueta: 'Fecha' },
  { clave: 'estado',      etiqueta: 'Estado' },
  { clave: 'factura',     etiqueta: 'Factura' },
]

// --- Modal Factura ---
const ordenSeleccionadaParaFactura = ref<number | null>(null)

const abrirFactura = (ordenId: number) => {
  ordenSeleccionadaParaFactura.value = ordenId
}

const cerrarFactura = () => {
  ordenSeleccionadaParaFactura.value = null
}

// --- Helper para badge de estado ---
function formatearEstado(estado: string): string {
  if (!estado) return 'Desconocido'
  if (estado.toUpperCase() === 'EN_RUTA') return 'En Ruta'
  return estado.charAt(0).toUpperCase() + estado.slice(1).toLowerCase()
}

function claseEstado(estado: string): string {
  const e = (estado ?? '').toUpperCase()
  if (e === 'ENTREGADO') return 'badge badge-entregado'
  if (e === 'EN_RUTA')   return 'badge badge-en-ruta'
  if (e === 'PREPARANDO') return 'badge badge-preparando'
  if (e === 'APROBADO' || e === 'APROBADA')  return 'badge badge-aprobada'
  if (e === 'CANCELADO' || e === 'CANCELADA') return 'badge badge-cancelada'
  if (e === 'PENDIENTE') return 'badge badge-pendiente'
  return 'badge badge-default'
}
</script>

<template>
  <div class="tabla-contenedor">
    <table class="tabla" aria-label="Mis órdenes">

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
        </tr>
      </thead>

      <!-- Cuerpo -->
      <tbody>

        <!-- Cargando -->
        <tr v-if="cargando">
          <td colspan="5" class="celda-estado">
            <span class="spinner" aria-hidden="true"></span>
            <span class="texto-estado">Cargando órdenes...</span>
          </td>
        </tr>

        <!-- Error -->
        <tr v-else-if="error">
          <td colspan="5" class="celda-estado">
            <p class="texto-error">{{ error }}</p>
            <button class="btn-reintentar" @click="emit('reintentar')">↺ Reintentar</button>
          </td>
        </tr>

        <!-- Lista vacía -->
        <tr v-else-if="ordenes.length === 0">
          <td colspan="5" class="celda-estado">
            <p class="texto-vacio">No tienes órdenes registradas aún.</p>
          </td>
        </tr>

        <!-- Filas de datos -->
        <template v-else>
          <tr v-for="orden in ordenes" :key="orden.orden_ID" class="fila-orden">
            <td class="celda">{{ orden.orden_ID }}</td>
            <td class="celda">{{ orden.fecha_Orden ? new Date(orden.fecha_Orden).toLocaleDateString('es-CL') : '—' }}</td>
            <td class="celda">
              <span :class="claseEstado(orden.estado)">{{ formatearEstado(orden.estado) }}</span>
            </td>
            <td class="celda">
              <button class="btn-ver-factura" @click="abrirFactura(orden.orden_ID)"> Ver factura</button>
            </td>
          </tr>
        </template>

      </tbody>
    </table>

    <ModalFactura
      :ordenId="ordenSeleccionadaParaFactura"
      @cerrar="cerrarFactura"
    />
  </div>
</template>

<style scoped>
.tabla-contenedor { width: 100%; overflow-x: auto; border-radius: 10px; border: 1px solid #e0e0e0; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.9rem; min-width: 600px; }
.tabla-encabezado { background-color: #156895; color: white; }
.celda-encabezado { padding: 12px 16px; text-align: center; font-weight: 600; cursor: pointer; user-select: none; white-space: nowrap; }
.celda-encabezado:hover { background-color: #0f5070; }
.encabezado-contenido { display: inline-flex; align-items: center; gap: 6px; }
.icono-orden { font-size: 0.8rem; opacity: 0.8; }
.fila-orden { border-bottom: 1px solid #f0f0f0; transition: background-color 0.15s; }
.fila-orden:hover { background-color: #f0f7ff; }
.celda { padding: 12px 16px; text-align: center; color: #333; }
.celda-estado { padding: 40px 16px; text-align: center; }
.spinner { display: inline-block; width: 24px; height: 24px; border: 3px solid #e0e0e0; border-top-color: #156895; border-radius: 50%; animation: girar 0.8s linear infinite; vertical-align: middle; margin-right: 10px; }
@keyframes girar { to { transform: rotate(360deg); } }
.texto-estado { color: #666; font-size: 0.9rem; }
.texto-error { color: #d32f2f; margin-bottom: 10px; }
.btn-reintentar { padding: 6px 14px; border: 1px solid #d32f2f; color: #d32f2f; background: none; border-radius: 6px; cursor: pointer; font-size: 0.85rem; }
.btn-reintentar:hover { background: #fff0f0; }
.texto-vacio { color: #555; margin-bottom: 6px; }
.badge { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.badge-pendiente  { background: #fff3cd; color: #856404; }
.badge-aprobada   { background: #cfe2ff; color: #084298; }
.badge-cancelada  { background: #f8d7da; color: #842029; }
.badge-entregado  { background: #d1e7dd; color: #0a5c36; }
.badge-en-ruta    { background: #cff4fc; color: #055160; }
.badge-preparando { background: #e2e3e5; color: #41464b; }
.badge-default    { background: #e2e8f0; color: #475569; }
.btn-ver-factura { padding: 6px 12px; background-color: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 6px; color: #334155; font-size: 0.85rem; font-weight: 500; cursor: pointer; transition: all 0.2s; }
.btn-ver-factura:hover { background-color: #e2e8f0; border-color: #94a3b8; color: #0f172a; }
</style>
