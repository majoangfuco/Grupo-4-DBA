<script setup lang="ts">
// =====================================================
// ListaClientes.vue
// Muestra la tabla de clientes con ordenamiento,
// estados de carga, error y lista vacía.
// =====================================================

// --- Tipos ---
interface Cliente {
  idCliente: number
  nombre: string
  rut: string
  telefono: string
  email: string
  estado: string
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
}>()

// --- Métodos ---
const manejarOrden = (clave: string) => {
  emit('ordenar', clave)
}

const obtenerDireccionOrden = (clave: string): 'asc' | 'desc' => {
  if (props.configOrden?.clave === clave) return props.configOrden.direccion
  return 'asc'
}

const estaOrdenandoPor = (clave: string): boolean => {
  return props.configOrden?.clave === clave
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
              { clave: 'idCliente',  etiqueta: 'ID Cliente' },
              { clave: 'nombre',     etiqueta: 'Nombre' },
              { clave: 'rut',        etiqueta: 'RUT' },
              { clave: 'telefono',   etiqueta: 'Teléfono' },
              { clave: 'email',      etiqueta: 'Email' },
              { clave: 'estado',     etiqueta: 'Estado' },
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
          <td colspan="6" class="celda-estado">
            <span class="spinner" aria-hidden="true"></span>
            <span class="texto-estado">Cargando datos...</span>
          </td>
        </tr>

        <!-- Estado: error -->
        <tr v-else-if="error">
          <td colspan="6" class="celda-estado">
            <p class="texto-error">{{ error }}</p>
            <button class="btn-reintentar" @click="emit('reintentar')">
              ↺ Reintentar
            </button>
          </td>
        </tr>

        <!-- Estado: lista vacía -->
        <tr v-else-if="clientes.length === 0">
          <td colspan="6" class="celda-estado">
            <p class="texto-vacio">No se encontraron clientes registrados.</p>
            <p class="texto-vacio-sub">Puedes agregar uno nuevo usando el botón "Agregar cliente".</p>
          </td>
        </tr>

        <!-- Datos de clientes -->
        <tr
          v-else
          v-for="cliente in clientes"
          :key="cliente.idCliente"
          class="fila-cliente"
        >
          <td class="celda">{{ cliente.idCliente }}</td>
          <td class="celda">{{ cliente.nombre }}</td>
          <td class="celda">{{ cliente.rut }}</td>
          <td class="celda">{{ cliente.telefono }}</td>
          <td class="celda">{{ cliente.email }}</td>
          <td class="celda">
            <span
              class="badge-estado"
              :class="cliente.estado === 'ACTIVO' ? 'estado-activo' : 'estado-restringido'"
            >
              {{ cliente.estado === 'ACTIVO' ? 'Activo' : 'Restringido' }}
            </span>
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

/* ===== ENCABEZADO ===== */
.tabla-encabezado {
  background-color: #156895;
  color: white;
}

.celda-encabezado {
  padding: 12px 16px;
  text-align: center;
  font-weight: 600;
  cursor: pointer;
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

.celda {
  padding: 12px 16px;
  text-align: center;
  color: #333;
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
