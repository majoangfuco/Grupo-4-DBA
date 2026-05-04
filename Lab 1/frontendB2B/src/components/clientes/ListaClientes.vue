<script setup lang="ts">
// =====================================================
// ListaClientes.vue
// Muestra la tabla de clientes con ordenamiento,
// estados de carga, error y lista vacía.
// =====================================================

// --- Tipos ---
interface Cliente {
  usuario_ID: number
  nombre_Usuario: string
  correo: string
  rut_Empresa: string
  ultima_Compra: string | null
  ordenes_Pendiente: number
  ordenes_Aprobada: number
  ordenes_Cancelada: number
}

interface ConfigOrden {
  clave: string
  direccion: 'asc' | 'desc'
}

// --- Props que recibe este componente ---
const props = defineProps<{
  clientes: Cliente[]
  cargando: boolean
  error: string | null
  configOrden?: ConfigOrden
}>()

// --- Eventos que emite hacia el padre ---
const emit = defineEmits<{
  (e: 'ordenar', clave: string): void
  (e: 'reintentar'): void
  (e: 'seleccionar', cliente: Cliente): void
}>()

// --- Métodos ---
const manejarOrden = (clave: string) => {
  emit('ordenar', clave)
}

const formatearFecha = (fecha: string | number | null): string => {
  if (fecha === null || fecha === undefined) return 'Sin compras'
  try {
    const date = new Date(fecha as any)
    return date.toLocaleDateString('es-ES', { year: 'numeric', month: '2-digit', day: '2-digit' })
  } catch {
    return 'Sin compras'
  }
}

const obtenerDireccionOrden = (clave: string): 'asc' | 'desc' => {
  if (props.configOrden?.clave === clave) return props.configOrden.direccion
  return 'asc'
}

const estaOrdenandoPor = (clave: string): boolean => {
  return props.configOrden?.clave === clave
}

const navegarOrdenes = (cliente: Cliente) => {
  emit('seleccionar', cliente)
}
</script>

<template>
  <div class="tabla-contenedor">
    <table class="tabla" aria-label="Lista de clientes">

      <!-- Encabezado de la tabla -->
      <thead class="tabla-encabezado">
        <tr>
          <th
            v-for="col in [
              { clave: 'nombre_Usuario', etiqueta: 'Nombre' },
              { clave: 'correo',         etiqueta: 'Correo' },
              { clave: 'rut_Empresa',    etiqueta: 'RUT Empresa' },
              { clave: 'ultima_Compra',  etiqueta: 'Última Compra' },
              { clave: 'ordenes_Pendiente', etiqueta: 'Ordenes (Pendiente / Aprobada / Cancelada)' },
            ]"
            :key="col.clave"
            class="celda-encabezado"
            @click="manejarOrden(col.clave)"
          >
            <span class="encabezado-contenido">
              {{ col.etiqueta }}
              <span class="icono-orden">
                <template v-if="estaOrdenandoPor(col.clave)">
                  {{ obtenerDireccionOrden(col.clave) === 'asc' ? '↑' : '↓' }}
                </template>
                <template v-else>⇅</template>
              </span>
            </span>
          </th>
        </tr>
      </thead>

      <!-- Cuerpo de la tabla -->
      <tbody>

        <!-- Estado: cargando -->
        <tr v-if="cargando">
          <td colspan="5" class="celda-estado">
            <span class="spinner" aria-hidden="true"></span>
            <span class="texto-estado">Cargando datos...</span>
          </td>
        </tr>

        <!-- Estado: error -->
        <tr v-else-if="error">
          <td colspan="5" class="celda-estado">
            <p class="texto-error">{{ error }}</p>
            <button class="btn-reintentar" @click="emit('reintentar')">
              ↺ Reintentar
            </button>
          </td>
        </tr>

        <!-- Estado: lista vacía -->
        <tr v-else-if="clientes.length === 0">
          <td colspan="5" class="celda-estado">
            <p class="texto-vacio">No se encontraron clientes registrados.</p>
            <p class="texto-vacio-sub">Puedes agregar uno nuevo usando el botón "Agregar cliente".</p>
          </td>
        </tr>

        <!-- Datos de clientes -->
        <tr
          v-else
          v-for="cliente in clientes"
          :key="cliente.usuario_ID"
          class="fila-cliente fila-clickable"
          role="button"
          tabindex="0"
          @click="navegarOrdenes(cliente)"
          @keydown.enter="navegarOrdenes(cliente)"
        >
          <td class="celda">{{ cliente.nombre_Usuario }}</td>
          <td class="celda">{{ cliente.correo }}</td>
          <td class="celda">{{ cliente.rut_Empresa }}</td>
          <td class="celda">{{ cliente.ultima_Compra ? formatearFecha(cliente.ultima_Compra) : 'Sin compras' }}</td>
          <td class="celda">
            <div class="ordenes-resumen">
              <span class="estado-chip estado-pendiente">PENDIENTE: {{ cliente.ordenes_Pendiente }}</span>
              <span class="estado-sep">|</span>
              <span class="estado-chip estado-aprobada">APROBADA: {{ cliente.ordenes_Aprobada }}</span>
              <span class="estado-sep">|</span>
              <span class="estado-chip estado-cancelada">CANCELADA: {{ cliente.ordenes_Cancelada }}</span>
            </div>
          </td>
        </tr>

      </tbody>
    </table>
  </div>
</template>

<style scoped>
/* ===== TABLA ===== */
.tabla-contenedor {
  width: 100%;
  overflow-x: auto;
  border-radius: 10px;
  border: 1px solid #e0e0e0;
}

.tabla {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
  min-width: 700px;
}

.tabla-encabezado {
  background-color: #156895;
  color: white;
}

.celda-encabezado {
  padding: 14px 16px;
  text-align: center;
  font-weight: 600;
  user-select: none;
  white-space: nowrap;
}

.celda-encabezado:hover {
  background-color: #0f5070;
}

.encabezado-contenido {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.icono-orden {
  font-size: 0.8rem;
  opacity: 0.8;
}

/* ===== FILAS ===== */
.fila-cliente {
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.15s;
}

.fila-cliente:hover {
  background-color: #f0f7ff;
}

.fila-clickable {
  cursor: pointer;
}

.fila-clickable:focus {
  outline: 2px solid #156895;
  outline-offset: -2px;
}

.celda {
  padding: 12px 16px;
  text-align: center;
  color: #333;
}

.ordenes-resumen {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.estado-chip {
  border-radius: 12px;
  padding: 3px 8px;
  font-size: 0.75rem;
  font-weight: 600;
}

.estado-pendiente {
  background: #fff3cd;
  color: #856404;
  border: 1px solid #f1d28a;
}

.estado-aprobada {
  background: #d1e7dd;
  color: #0a5c36;
  border: 1px solid #a8d5b6;
}

.estado-cancelada {
  background: #f8d7da;
  color: #842029;
  border: 1px solid #f1b8bf;
}

.estado-sep {
  color: #a0a0a0;
  font-size: 0.8rem;
}

/* ===== ESTADOS ESPECIALES ===== */
.celda-estado {
  padding: 40px 16px;
  text-align: center;
}

.spinner {
  display: inline-block;
  width: 24px;
  height: 24px;
  border: 3px solid #e0e0e0;
  border-top-color: #156895;
  border-radius: 50%;
  animation: girar 0.8s linear infinite;
  vertical-align: middle;
  margin-right: 10px;
}

@keyframes girar {
  to { transform: rotate(360deg); }
}

.texto-estado {
  color: #666;
  font-size: 0.9rem;
}

.texto-error {
  color: #d32f2f;
  margin-bottom: 10px;
}

.btn-reintentar {
  padding: 6px 14px;
  border: 1px solid #d32f2f;
  color: #d32f2f;
  background: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.btn-reintentar:hover {
  background: #fff0f0;
}

.texto-vacio {
  color: #555;
  margin-bottom: 6px;
}

.texto-vacio-sub {
  color: #888;
  font-size: 0.85rem;
}

/* ===== BADGE DE ESTADO ===== */
.badge-estado {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
}

.estado-activo {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.estado-restringido {
  background-color: #ffebee;
  color: #c62828;
}
</style>
