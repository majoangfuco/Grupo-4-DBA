<script setup lang="ts">
// =====================================================
// Almacenes-PaginaAdmin.vue
// Listado de almacenes
// =====================================================

import { ref, onMounted } from 'vue'
import { almacenServicio, type AlmacenEntidad } from '@/services/almacenServicio'

const almacenes = ref<AlmacenEntidad[]>([])
const cargando = ref(true)
const error = ref<string | null>(null)

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

onMounted(cargarAlmacenes)
</script>

<template>
  <div class="pagina">
    <div class="encabezado">
      <h1 class="titulo-pagina">Almacenes</h1>
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
        </tr>
      </thead>
      <tbody>
        <tr v-for="almacen in almacenes" :key="almacen.almacenId">
          <td>{{ almacen.nombre }}</td>
          <td>{{ almacen.direccion }}</td>
          <td>{{ almacen.latitud }}</td>
          <td>{{ almacen.longitud }}</td>
        </tr>
        <tr v-if="almacenes.length === 0">
          <td colspan="4" class="vacio">No hay almacenes registrados</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.pagina { display: flex; flex-direction: column; gap: 16px; }
.encabezado { display: flex; justify-content: space-between; align-items: center; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
.descripcion { color: #666; font-size: 0.9rem; margin: 0; }
.btn-agregar { padding: 10px 20px; background-color: #156895; color: white; border: none; border-radius: 8px; font-size: 0.9rem; font-weight: 600; cursor: pointer; }
.btn-agregar:hover { background-color: #0f5070; }
.estado { padding: 12px; color: #444; }
.estado.error { color: #b00020; }

.tabla-almacenes { width: 100%; border-collapse: separate;border-spacing: 0;background: #fff; border: 1px solid #e6e6e6; border-radius: 10px; }
.tabla-almacenes th, .tabla-almacenes td { padding: 12px 16px; border-bottom: 1px solid #f2f2f2; text-align: left; font-size: 0.9rem; }
.tabla-almacenes th { color: #ffffff; font-weight: 600; background: #156895;  border-bottom: none; }
.tabla-almacenes th:first-child { border-top-left-radius: 9px;}
.tabla-almacenes th:last-child {border-top-right-radius: 9px;}
.tabla-almacenes tbody tr:last-child td { border-bottom: none;}
.vacio { text-align: center; color: #777; padding: 16px; }
</style>