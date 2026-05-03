<script setup lang="ts">
import { ref, watch } from 'vue'
import { facturasServicio, type Factura } from '@/services/facturasServicio'

const props = defineProps<{
  ordenId: number | null
}>()

const emit = defineEmits<{
  (e: 'cerrar'): void
}>()

const factura = ref<Factura | null>(null)
const cargando = ref(false)
const error = ref<string | null>(null)
const descargaError = ref<string | null>(null)
const descargando = ref(false)

const descargarFactura = async () => {
  if (!factura.value || props.ordenId === null) return
  descargaError.value = null
  descargando.value = true
  try {
    const response = await facturasServicio.descargarPdfPorOrden(props.ordenId)
    const url = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `factura-${factura.value.fecha_Emision}${factura.value.fecha_Emision}.pdf`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (err) {
    console.error('Error al descargar factura:', err)
    descargaError.value = 'No se pudo descargar la factura. Intente nuevamente.'
  } finally {
    descargando.value = false
  }
}

watch(() => props.ordenId, async (newOrdenId) => {
  if (newOrdenId !== null) {
    cargando.value = true
    error.value = null
    factura.value = null
    try {
      const response = await facturasServicio.obtenerFacturaPorOrden(newOrdenId)
      factura.value = response.data
    } catch (err) {
      console.error('Error al cargar factura:', err)
      const axiosError = err as { response?: { data?: { message?: string } } }
      error.value = axiosError.response?.data?.message || 'No se pudo cargar la factura. Es posible que aún no se haya emitido.'
    } finally {
      cargando.value = false
    }
  }
}, { immediate: true })

const formatearMoneda = (valor: number) => {
  return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(valor)
}

const formatearFecha = (fecha: string) => {
  return new Date(fecha).toLocaleDateString('es-CL', {
    year: 'numeric', month: 'long', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="ordenId !== null" class="modal-overlay" @click="emit('cerrar')">
      <div class="modal-content" @click.stop>

        <button class="btn-cerrar" @click="emit('cerrar')" aria-label="Cerrar modal">✕</button>
        <button
          v-if="factura"
          class="btn-descargar"
          :disabled="descargando"
          @click="descargarFactura"
        >
          <span v-if="descargando" class="spinner-mini"></span>
          <span v-else>⬇</span>
        </button>

        <div class="modal-header">
          <h2>Detalle de Factura</h2>
          <p v-if="factura" class="orden-badge">Orden N° {{ factura.ordenId }}</p>
          <p v-else-if="ordenId" class="orden-badge">Orden N° {{ ordenId }}</p>
        </div>

        <div class="modal-body">
          <div v-if="cargando" class="estado-contenedor">
            <span class="spinner"></span>
            <p>Cargando información de la factura...</p>
          </div>

          <div v-else-if="error" class="estado-contenedor error">
            <p>{{ error }}</p>
          </div>

          <div v-if="descargaError" class="estado-contenedor error descarga-error">
            <p>{{ descargaError }}</p>
          </div>

          <div v-else-if="factura" class="factura-detalles">

            <div class="info-seccion">
              <div class="info-item">
                <span class="etiqueta">N° Factura:</span>
                <span class="valor destacado">{{ factura.factura_ID }}</span>
              </div>
              <div class="info-item">
                <span class="etiqueta">ID Cliente:</span>
                <span class="valor">{{ factura.usuarioId }}</span>
              </div>
              <div class="info-item">
                <span class="etiqueta">Fecha de Emisión:</span>
                <span class="valor">{{ formatearFecha(factura.fecha_Emision) }}</span>
              </div>
            </div>

            <div class="divider"></div>

            <div v-if="factura.items && factura.items.length > 0" class="items-seccion">
              <h3 class="items-titulo">Detalle de Productos</h3>
              <table class="items-tabla">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th class="text-right">Cant.</th>
                    <th class="text-right">Precio unit.</th>
                    <th class="text-right">Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in factura.items" :key="item.carrito_Producto_ID">
                    <td>{{ item.producto.nombre_producto }}</td>
                    <td class="text-right">{{ item.unidad_producto }}</td>
                    <td class="text-right">{{ formatearMoneda(item.producto.precio) }}</td>
                    <td class="text-right">{{ formatearMoneda(item.producto.precio * item.unidad_producto) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="divider"></div>

            <div class="resumen-seccion">
              <div class="resumen-fila">
                <span>Total Neto:</span>
                <span>{{ formatearMoneda(factura.total_Neto) }}</span>
              </div>
              <div class="resumen-fila">
                <span>IVA (19%):</span>
                <span>{{ formatearMoneda(factura.iva) }}</span>
              </div>
              <div class="resumen-fila total-final">
                <span>Total a Pagar:</span>
                <span>{{ formatearMoneda(factura.precio_Total) }}</span>
              </div>
            </div>

          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
  animation: fadeIn 0.2s ease-out;
}

.modal-content {
  background: white;
  border-radius: 16px;
  width: 92%;
  max-width: 700px;
  min-width: 380px;
  min-height: 560px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  position: relative;
  overflow: hidden;
  animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  display: flex;
  flex-direction: column;
}

.btn-cerrar {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: #f1f5f9;
  color: #64748b;
  font-size: 1.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  z-index: 10;
}

.btn-cerrar:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.btn-descargar {
  position: absolute;
  top: 16px;
  right: 56px;
  width: 38px;
  height: 38px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background-color: White;
  color: #156895;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.2s, transform 0.2s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.btn-descargar:hover:not(:disabled) {
  background-color: #72bcf1;
  transform: translateY(-1px);
}

.btn-descargar:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.descarga-error {
  width: 100%;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
  text-align: left;
}

.spinner-mini {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}

.modal-header {
  padding: 24px 24px 16px;
  background: linear-gradient(135deg, #156895 0%, #0d4b6e 100%);
  color: white;
  position: relative;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.orden-badge {
  display: inline-block;
  margin-top: 8px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 500;
  letter-spacing: 0.03em;
}

.modal-body {
  padding: 24px;
}

.estado-contenedor {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: #64748b;
  text-align: center;
}

.estado-contenedor.error {
  color: #ef4444;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #e2e8f0;
  border-top-color: #156895;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

.factura-detalles {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-seccion {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.95rem;
}

.etiqueta {
  color: #64748b;
}

.valor {
  font-weight: 600;
  color: #0f172a;
}

.valor.destacado {
  color: #156895;
  font-size: 1.1rem;
}

.divider {
  height: 1px;
  background-color: #e2e8f0;
  margin: 8px 0;
}

.resumen-seccion {
  background: #f8fafc;
  padding: 16px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.resumen-fila {
  display: flex;
  justify-content: space-between;
  font-size: 0.95rem;
  color: #475569;
}

.total-final {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed #cbd5e1;
  font-size: 1.25rem;
  font-weight: 700;
  color: #0f172a;
}

.modal-footer {
  padding: 0 24px 24px;
}

.btn-accion {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 8px;
  background-color: #156895;
  color: white;
  font-weight: 600;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-accion:hover:not(:disabled) {
  background-color: #0d4b6e;
  transform: translateY(-1px);
}

.btn-accion:disabled {
  background-color: #94a3b8;
  cursor: not-allowed;
  opacity: 0.7;
}

.items-seccion {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.items-titulo {
  font-size: 0.9rem;
  font-weight: 600;
  color: #475569;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0;
}

.items-tabla {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.items-tabla thead tr {
  border-bottom: 2px solid #e2e8f0;
}

.items-tabla th {
  padding: 6px 8px;
  color: #64748b;
  font-weight: 600;
  text-align: left;
}

.items-tabla td {
  padding: 8px 8px;
  color: #0f172a;
  border-bottom: 1px solid #f1f5f9;
}

.items-tabla tbody tr:last-child td {
  border-bottom: none;
}

.text-right {
  text-align: right;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
