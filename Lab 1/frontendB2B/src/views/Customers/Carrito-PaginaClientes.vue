<script setup lang="ts">
// =====================================================
// Carrito-PaginaClientes.vue
// Muestra el carrito activo/abandonado del cliente.
// =====================================================

import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { carritoServicio, type CarritoEntidad } from '@/services/carritoServicio'
import { carritoProductoServicio, type CarritoProductoEntidad } from '@/services/carritoProductoServicio'

const authStore = useAuthStore()

const carrito = ref<CarritoEntidad | null>(null)
const items = ref<CarritoProductoEntidad[]>([])
const subtotal = ref<number>(0)
const cargando = ref(false)
const error = ref<string | null>(null)
const toastMensaje = ref<string | null>(null)
const toastTipo = ref<'ok' | 'error'>('ok')
let toastTimer: number | null = null
const modalAbierto = ref(false)
const modalAccion = ref<'agregar' | 'eliminar'>('eliminar')
const modalItem = ref<CarritoProductoEntidad | null>(null)

const cargarItems = async (carritoId: number) => {
  const [itemsResp, subtotalResp] = await Promise.all([
    carritoProductoServicio.listarItemsPorCarrito(carritoId),
    carritoProductoServicio.calcularSubtotal(carritoId),
  ])
  items.value = itemsResp.data
  subtotal.value = subtotalResp.data
}

const notificar = (mensaje: string, tipo: 'ok' | 'error') => {
  toastTipo.value = tipo
  toastMensaje.value = mensaje
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMensaje.value = null
  }, 1800)
}

const actualizarCantidad = async (item: CarritoProductoEntidad, nuevaCantidad: number) => {
  if (!carrito.value?.carrito_ID) return
  try {
    if (nuevaCantidad <= 0) {
      abrirConfirmacion('eliminar', item)
      return
    } else {
      await carritoProductoServicio.actualizarCantidad(item.carrito_Producto_ID, nuevaCantidad)
      notificar('Cantidad actualizada', 'ok')
    }
    await cargarItems(carrito.value.carrito_ID)
  } catch (err: unknown) {
    console.error('Error al actualizar carrito:', err)
    notificar('No se pudo actualizar el carrito', 'error')
  }
}

const disminuirUnidad = async (item: CarritoProductoEntidad) => {
  const actual = item.unidad_producto ?? 0
  await actualizarCantidad(item, actual - 1)
}

const aumentarUnidad = async (item: CarritoProductoEntidad) => {
  abrirConfirmacion('agregar', item)
}

const abrirConfirmacion = (accion: 'agregar' | 'eliminar', item: CarritoProductoEntidad) => {
  modalAccion.value = accion
  modalItem.value = item
  modalAbierto.value = true
}

const cerrarModal = () => {
  modalAbierto.value = false
  modalItem.value = null
}

const confirmarModal = async () => {
  if (!modalItem.value || !carrito.value?.carrito_ID) return
  const item = modalItem.value
  const actual = item.unidad_producto ?? 0
  cerrarModal()
  try {
    if (modalAccion.value === 'agregar') {
      await carritoProductoServicio.actualizarCantidad(item.carrito_Producto_ID, actual + 1)
      notificar('Cantidad actualizada', 'ok')
    } else {
      await carritoProductoServicio.eliminarItem(item.carrito_Producto_ID)
      notificar('Producto eliminado del carrito', 'ok')
    }
    await cargarItems(carrito.value.carrito_ID)
  } catch (err: unknown) {
    console.error('Error al actualizar carrito:', err)
    notificar('No se pudo actualizar el carrito', 'error')
  }
}

const cargarCarrito = async () => {
  error.value = null
  cargando.value = true
  try {
    const userId = Number(authStore.userId)
    if (!userId || Number.isNaN(userId)) {
      throw new Error('Usuario no valido')
    }
    const respuesta = await carritoServicio.obtenerOCrearPorCliente(userId)
    carrito.value = respuesta.data
    if (carrito.value?.carrito_ID) {
      await cargarItems(carrito.value.carrito_ID)
    } else {
      items.value = []
      subtotal.value = 0
    }
  } catch (err: unknown) {
    console.error('Error al obtener carrito:', err)
    error.value = 'No se pudo cargar el carrito'
  } finally {
    cargando.value = false
  }
}

onMounted(cargarCarrito)
</script>

<template>
  <div class="pagina">
    <div v-if="modalAbierto" class="modal-overlay" @click.self="cerrarModal">
      <div class="modal-box" role="dialog" aria-modal="true">
        <div class="modal-header">
          <span class="modal-check">✓</span>
          <h3 class="modal-title">
            {{ modalAccion === 'agregar' ? 'Producto agregado a tu Carro' : 'Eliminar producto del Carro' }}
          </h3>
          <button class="modal-close" @click="cerrarModal">×</button>
        </div>
        <div class="modal-body">
          <div class="modal-info">
            <div class="modal-nombre">{{ modalItem?.producto?.nombre_producto }}</div>
            <div class="modal-cantidad">
              {{ modalAccion === 'agregar' ? 'Agregar 1 unidad' : 'Quitar este producto' }}
            </div>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-link" @click="cerrarModal">Cancelar</button>
          <button class="btn-solid" @click="confirmarModal">Aceptar</button>
        </div>
      </div>
    </div>
    <div v-if="toastMensaje" class="toast" :class="toastTipo">
      {{ toastMensaje }}
    </div>
    <div class="encabezado">
      <h1 class="titulo-pagina">Mi carrito</h1>
      <button class="btn-primario" @click="cargarCarrito">Actualizar</button>
    </div>

    <div v-if="cargando" class="estado">Cargando carrito...</div>
    <div v-else-if="error" class="estado error">{{ error }}</div>

    <div v-if="carrito" class="panel principal">
      <h2 class="subtitulo">Productos</h2>
      <table class="tabla">
        <thead>
          <tr>
            <th>Producto</th>
            <th>Precio</th>
            <th>Cantidad</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.carrito_Producto_ID">
            <td>{{ item.producto?.nombre_producto ?? 'Producto' }}</td>
            <td>{{ item.producto?.precio ?? 0 }}</td>
            <td>
              <div class="cantidad">
                <button class="btn-cantidad" @click="disminuirUnidad(item)">−</button>
                <span class="cantidad-valor">{{ item.unidad_producto }}</span>
                <button class="btn-cantidad" @click="aumentarUnidad(item)">+</button>
              </div>
            </td>
            <td>{{ (item.producto?.precio ?? 0) * item.unidad_producto }}</td>
          </tr>
          <tr v-if="items.length === 0">
            <td colspan="4" class="vacio">Carrito sin productos</td>
          </tr>
        </tbody>
      </table>
      <div class="subtotal">
        <span>Subtotal</span>
        <strong>{{ subtotal }}</strong>
      </div>
    </div>

    <div v-else class="estado">No hay carrito activo.</div>
  </div>
</template>

<style scoped>
.pagina { display: flex; flex-direction: column; gap: 16px; }
.toast {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 300;
  padding: 10px 14px;
  border-radius: 10px;
  color: #fff;
  font-size: 0.9rem;
  box-shadow: 0 6px 18px rgba(0,0,0,0.18);
  animation: fadeout 1.8s ease-in-out;
}
.toast.ok { background: #156895; }
.toast.error { background: #b00020; }
@keyframes fadeout {
  0% { opacity: 0; transform: translateY(-6px); }
  10% { opacity: 1; transform: translateY(0); }
  80% { opacity: 1; }
  100% { opacity: 0; }
}
.encabezado { display: flex; align-items: center; justify-content: space-between; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
.estado { padding: 12px; color: #444; }
.estado.error { color: #b00020; }
.panel { background: #fff; border: 1px solid #e6e6e6; border-radius: 12px; padding: 16px; }
.panel.principal { max-width: 980px; }
.fila { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f2f2f2; }
.fila:last-child { border-bottom: none; }
.etiqueta { color: #666; font-size: 0.9rem; }
.valor { font-weight: 600; color: #222; }
.btn-primario { background: #156895; color: #fff; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; }
.btn-primario:hover { background: #1b76a5; }
.subtitulo { font-size: 1.1rem; font-weight: 700; margin-bottom: 10px; color: #1a1a2e; }
.tabla { width: 100%; border-collapse: collapse; }
.tabla th, .tabla td { padding: 8px; border-bottom: 1px solid #f2f2f2; text-align: left; font-size: 0.9rem; }
.tabla th { color: #555; font-weight: 600; }
.vacio { text-align: center; color: #777; padding: 12px; }
.subtotal { display: flex; justify-content: space-between; padding-top: 10px; font-size: 1rem; }
.cantidad { display: inline-flex; align-items: center; gap: 8px; }
.btn-cantidad { width: 26px; height: 26px; border-radius: 6px; border: 1px solid #ddd; background: #fff; cursor: pointer; }
.btn-cantidad:hover { border-color: #156895; color: #156895; }
.cantidad-valor { min-width: 24px; text-align: center; font-weight: 600; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; z-index: 300; }
.modal-box { background: #fff; border-radius: 14px; padding: 18px 20px; width: 520px; max-width: 92vw; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }
.modal-header { display: flex; align-items: center; gap: 10px; position: relative; }
.modal-check { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border: 2px solid #2e7d32; color: #2e7d32; border-radius: 50%; font-size: 0.85rem; }
.modal-title { font-size: 1rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.modal-close { position: absolute; right: 0; top: -4px; background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #777; }
.modal-body { margin-top: 12px; }
.modal-info { display: flex; flex-direction: column; gap: 4px; color: #333; }
.modal-nombre { font-weight: 600; }
.modal-cantidad { color: #666; font-size: 0.9rem; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 16px; }
.btn-link { background: none; border: none; color: #156895; cursor: pointer; }
.btn-solid { background: #156895; color: #fff; border: none; border-radius: 22px; padding: 8px 16px; cursor: pointer; }
.btn-solid:hover { background: #1b76a5; }
</style>
