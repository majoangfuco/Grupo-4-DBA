<script setup lang="ts">
// =====================================================
// AjustesCarrito-PaginaAdmin.vue
// El Admin NO agrega ítems al carrito (eso lo hace el
// cliente desde el carrito azul real de Postgres) — acá
// solo gestiona la cantidad mínima B2B por producto.
// =====================================================

import { computed, onMounted, ref } from 'vue'
import { categoriaServicio, type CategoriaEntidad } from '@/services/categoriaServicio'
import { productoServicio } from '@/services/productoServicio'
import { carritoMongoServicio } from '@/services/carritoMongoServicio'

interface ProductoListado {
  producto_ID: number
  categoria_ID: number
  nombre_producto: string
  activo: boolean
}

const categorias = ref<CategoriaEntidad[]>([])
const productos = ref<ProductoListado[]>([])
const cargando = ref(true)

const categoriaFiltro = ref<number | null>(null)
const productoId = ref<number | null>(null)
const cantidadMinimaB2B = ref(1)

const enviando = ref(false)

const toastMensaje = ref<string | null>(null)
const toastTipo = ref<'ok' | 'error'>('ok')
let toastTimer: number | null = null

function mostrarToast(mensaje: string, tipo: 'ok' | 'error') {
  toastTipo.value = tipo
  toastMensaje.value = mensaje
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMensaje.value = null
  }, 1800)
}

function extraerMensaje(err: unknown, fallback: string): string {
  const axiosErr = err as { response?: { data?: { message?: string } } }
  return axiosErr.response?.data?.message ?? fallback
}

// Filtro rápido por categoría: si no se elige ninguna, se ven todos los
// productos. Es solo para ubicar el producto más rápido en la lista.
const productosFiltrados = computed(() =>
  categoriaFiltro.value === null
    ? productos.value
    : productos.value.filter((p) => p.categoria_ID === categoriaFiltro.value),
)

onMounted(async () => {
  try {
    const [respCategorias, respProductos] = await Promise.all([
      categoriaServicio.listar(),
      productoServicio.obtenerTodos(),
    ])
    categorias.value = respCategorias.data
    productos.value = (respProductos.data as ProductoListado[]).filter((p) => p.activo)
    productoId.value = productos.value[0]?.producto_ID ?? null
  } catch (err: unknown) {
    console.error('Error al cargar categorías/productos:', err)
    mostrarToast('No se pudieron cargar categorías o productos.', 'error')
  } finally {
    cargando.value = false
  }
})

const guardarCantidadMinima = async () => {
  if (productoId.value === null) return
  enviando.value = true
  try {
    await carritoMongoServicio.establecerCantidadMinima(productoId.value, cantidadMinimaB2B.value)
    mostrarToast(`Cantidad mínima B2B actualizada a ${cantidadMinimaB2B.value} unidades.`, 'ok')
  } catch (err: unknown) {
    console.error('Error al guardar cantidad mínima B2B:', err)
    mostrarToast(extraerMensaje(err, 'No se pudo guardar la configuración.'), 'error')
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <div class="pagina">
    <div v-if="toastMensaje" class="toast" :class="toastTipo">
      {{ toastMensaje }}
    </div>

    <div class="encabezado">
      <h1 class="titulo-pagina">Configuración de Carrito</h1>
    </div>

    <div class="contenedor-centrado">
      <form class="tarjeta-formulario" @submit.prevent="guardarCantidadMinima">
        <h2 class="titulo-seccion">Configurar cantidad mínima B2B</h2>
        <p class="ayuda">Si un producto no tiene configuración, no se aplica mínimo B2B especial. Guardar 1 quita la configuración.</p>

        <div class="campo">
          <label>Filtrar por categoría </label>
          <select v-model="categoriaFiltro" class="filtro-entrada" :disabled="cargando">
            <option :value="null">Todas las categorías</option>
            <option v-for="categoria in categorias" :key="categoria.categoria_ID" :value="categoria.categoria_ID">
              {{ categoria.nombre_Categoria }}
            </option>
          </select>
        </div>

        <div class="campo">
          <label>Producto</label>
          <select v-model="productoId" class="filtro-entrada" :disabled="cargando" required>
            <option v-if="cargando" :value="null">Cargando productos...</option>
            <option v-for="producto in productosFiltrados" :key="producto.producto_ID" :value="producto.producto_ID">
              {{ producto.nombre_producto }}
            </option>
          </select>
        </div>

        <div class="campo">
          <label>Cantidad mínima B2B</label>
          <input v-model.number="cantidadMinimaB2B" class="filtro-entrada" type="number" min="1" required />
        </div>

        <div class="acciones-fin">
          <button type="submit" class="btn-solid" :disabled="enviando || productoId === null">
            {{ enviando ? 'Guardando...' : 'Guardar mínimo' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.pagina { display: flex; flex-direction: column; gap: 20px; }
.encabezado { display: flex; justify-content: space-between; align-items: center; }
.titulo-pagina { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; }
.subtitulo { color: #666; font-size: 0.9rem; margin-top: -12px; }

.contenedor-centrado { display: flex; justify-content: center; }

.tarjeta-formulario {
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 12px;
  padding: 24px;
  width: 100%;
  max-width: 420px;
}

.titulo-seccion { font-size: 1.05rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.ayuda { font-size: 0.8rem; color: #777; margin: 0; }

.campo { display: flex; flex-direction: column; gap: 6px; }
.campo label { font-size: 0.85rem; font-weight: 600; color: #333; }

.filtro-entrada {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 0.875rem;
  outline: none;
  transition: border-color 0.2s;
  background: #fff;
}
.filtro-entrada:focus { border-color: #156895; }

.acciones-fin { display: flex; justify-content: flex-end; margin-top: 6px; }

.btn-solid {
  background: #156895;
  color: #fff;
  border: none;
  border-radius: 22px;
  padding: 8px 20px;
  cursor: pointer;
  font-weight: 600;
}
.btn-solid:hover { background: #1b76a5; }
.btn-solid:disabled { opacity: 0.6; cursor: not-allowed; }

.toast {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 300;
  padding: 10px 14px;
  border-radius: 10px;
  color: #fff;
  font-size: 0.9rem;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
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
</style>
