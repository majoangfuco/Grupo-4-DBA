<script setup lang="ts">
// =====================================================
// listaOrdenes-vistaAdmin.vue
// Tabla simplificada de órdenes para el administrador.
// Muestra solo: RUT de empresa, Fecha, Estado y Acciones
// =====================================================

import type { OrdenAdmin } from '@/services/ordenesServicio'
import { ref, watch } from 'vue'
import ModalFactura from '@/components/ordenes/ModalFactura.vue'

interface ConfigOrden {
  clave: string
  direccion: 'asc' | 'desc'
}

// --- Props ---
const props = defineProps<{
  ordenes: OrdenAdmin[]
  cargando: boolean
  error: string | null
  configOrden?: ConfigOrden
  ordenParaFactura?: number | null
}>()

// --- Eventos ---
const emit = defineEmits<{
  (e: 'ordenar', clave: string): void
  (e: 'reintentar'): void
  (e: 'aprobar', ordenId: number): void
  (e: 'cancelar', ordenId: number): void
}>()

// --- Helpers de ordenamiento ---
const estaOrdenandoPor = (clave: string) => props.configOrden?.clave === clave
const obtenerDireccion = (clave: string): 'asc' | 'desc' =>
  props.configOrden?.clave === clave ? props.configOrden.direccion : 'asc'

// --- Columnas simplificadas ---
const columnas: Array<{ clave: string; etiqueta: string }> = [
  { clave: 'rut_Empresa',  etiqueta: 'RUT Empresa' },
  { clave: 'fecha_Orden',  etiqueta: 'Fecha' },
  { clave: 'estado',       etiqueta: 'Estado' },
  { clave: 'acciones',     etiqueta: 'Acciones' },
]

// --- Modal Factura ---
const ordenSeleccionadaParaFactura = ref<number | null>(null)

const abrirFactura = (ordenId: number) => {
  ordenSeleccionadaParaFactura.value = ordenId
}

const cerrarFactura = () => {
  ordenSeleccionadaParaFactura.value = null
}

watch(() => props.ordenParaFactura, (nuevoId) => {
  if (nuevoId !== null && nuevoId !== undefined) {
    ordenSeleccionadaParaFactura.value = nuevoId
  }
})

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
    <table class="tabla" aria-label="Lista de órdenes">

      <!-- Encabezado -->
      <thead class="tabla-encabezado">
        <tr>
          <th
            v-for="col in columnas"
            :key="col.clave"
            class="celda-encabezado"
            @click="col.clave !== 'acciones' && emit('ordenar', col.clave)"
            :style="{ cursor: col.clave !== 'acciones' ? 'pointer' : 'default' }"
          >
            <span class="encabezado-contenido">
              {{ col.etiqueta }}
              <span class="icono-orden" v-if="col.clave !== 'acciones'">
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
          <td colspan="4" class="celda-estado">
            <span class="spinner" aria-hidden="true"></span>
            <span class="texto-estado">Cargando órdenes...</span>
          </td>
        </tr>

        <!-- Error -->
        <tr v-else-if="error">
          <td colspan="4" class="celda-estado">
            <p class="texto-error">{{ error }}</p>
            <button class="btn-reintentar" @click="emit('reintentar')">↺ Reintentar</button>
          </td>
        </tr>

        <!-- Lista vacía -->
        <tr v-else-if="ordenes.length === 0">
          <td colspan="4" class="celda-estado">
            <p class="texto-vacio">No hay órdenes para mostrar.</p>
          </td>
        </tr>

        <!-- Filas de datos -->
        <template v-else>
          <tr v-for="orden in ordenes" :key="orden.orden_ID" class="fila-orden">
            <td class="celda celda-rut">{{ orden.rut_Empresa }}</td>
            <td class="celda celda-fecha">{{ orden.fecha_Orden ? new Date(orden.fecha_Orden).toLocaleDateString('es-CL') : '—' }}</td>
            <td class="celda celda-estado-col">
              <span :class="claseEstado(orden.estado)">{{ orden.estado }}</span>
            </td>
            <td class="celda celda-acciones">
              <div class="acciones">
                <button
                  class="btn-accion btn-ver-factura"
                  @click="abrirFactura(orden.orden_ID)"
                  title="Ver factura"
                >
                  Ver factura
                </button>
                <button
                  class="btn-accion btn-aprobar"
                  :disabled="orden.estado.toUpperCase() !== 'PENDIENTE'"
                  @click="emit('aprobar', orden.orden_ID)"
                >
                  Aprobar
                </button>
                <button
                  class="btn-accion btn-cancelar"
                  :disabled="orden.estado.toUpperCase() === 'CANCELADA' || orden.estado.toUpperCase() === 'APROBADA'"
                  @click="emit('cancelar', orden.orden_ID)"
                >
                  Cancelar
                </button>
              </div>
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
.tabla { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.tabla-encabezado { background-color: #156895; color: white; }
.celda-encabezado { padding: 14px 16px; text-align: center; font-weight: 600; user-select: none; white-space: nowrap; }
.celda-encabezado:hover { background-color: #0f5070; }
.encabezado-contenido { display: inline-flex; align-items: center; gap: 6px; }
.icono-orden { font-size: 0.8rem; opacity: 0.8; }
.fila-orden { border-bottom: 1px solid #f0f0f0; transition: background-color 0.15s; }
.fila-orden:hover { background-color: #f0f7ff; }
.celda { padding: 12px 16px; }
.celda-rut { text-align: left; font-weight: 500; color: #0f172a; }
.celda-fecha { text-align: center; color: #475569; }
.celda-estado-col { text-align: center; }
.celda-acciones { text-align: center; }
.celda-estado { padding: 40px 16px; text-align: center; }
.spinner { display: inline-block; width: 24px; height: 24px; border: 3px solid #e0e0e0; border-top-color: #156895; border-radius: 50%; animation: girar 0.8s linear infinite; vertical-align: middle; margin-right: 10px; }
@keyframes girar { to { transform: rotate(360deg); } }
.texto-estado { color: #666; font-size: 0.9rem; }
.texto-error { color: #d32f2f; margin-bottom: 10px; }
.btn-reintentar { padding: 6px 14px; border: 1px solid #d32f2f; color: #d32f2f; background: none; border-radius: 6px; cursor: pointer; font-size: 0.85rem; }
.btn-reintentar:hover { background: #fff0f0; }
.texto-vacio { color: #555; margin-bottom: 6px; }
.badge { display: inline-block; padding: 4px 12px; border-radius: 14px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }
.badge-pendiente    { background: #fff3cd; color: #856404; }
.badge-aprobada     { background: #d1e7dd; color: #0a5c36; }
.badge-cancelada    { background: #f8d7da; color: #842029; }
.badge-pagado       { background: #cfe2ff; color: #084298; }
.badge-preparando   { background: #e7d4f5; color: #5a3f8c; }
.badge-en-ruta      { background: #fff8c5; color: #664d03; }
.badge-entregado    { background: #d1ecf1; color: #0c5460; }
.badge-default      { background: #e9ecef; color: #383d41; }
.btn-ver-factura { padding: 6px 12px; background-color: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 6px; color: #334155; font-size: 0.82rem; font-weight: 500; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.btn-ver-factura:hover { background-color: #e2e8f0; border-color: #94a3b8; color: #0f172a; }
.acciones { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.btn-accion { padding: 6px 10px; border-radius: 6px; font-size: 0.82rem; font-weight: 600; border: 1px solid transparent; cursor: pointer; transition: all 0.2s; }
.btn-aprobar { background: #e8f5e9; color: #2e7d32; border-color: #a5d6a7; }
.btn-aprobar:hover:not(:disabled) { background: #dff1e2; }
.btn-cancelar { background: #ffebee; color: #c62828; border-color: #ef9a9a; }
.btn-cancelar:hover:not(:disabled) { background: #fbe1e6; }
.btn-accion:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
