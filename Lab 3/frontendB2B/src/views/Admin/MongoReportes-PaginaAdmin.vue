<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  BarChart2, RefreshCw, Package, TrendingUp, Trophy, Medal, Layers,
  AlertCircle, CheckCircle2, ChevronDown, ChevronUp,
} from 'lucide-vue-next'
import {
  mongoReporteServicio,
  type BucketVolumen,
  type ProductoMasVendido,
} from '@/services/mongoReporteServicio'

// ===================== ESTADO GENERAL ========================
const cargando = ref(true)
const errorVolumen = ref<string | null>(null)
const errorMasVendidos = ref<string | null>(null)

// ===================== TAREA 4: VOLUMEN PROYECTADO ========================
const bucketsVolumen = ref<BucketVolumen[]>([])
const bucketExpandido = ref<string | number | null>(null)

const etiquetaBucket = (id: string | number): string => {
  if (typeof id === 'string') return id
  if (id === 0) return 'BAJO (< $50.000)'
  if (id === 50000) return 'MEDIO ($50.000 – $200.000)'
  return String(id)
}

const colorBucket = (id: string | number): string => {
  if (typeof id === 'string') return '#156895' // ALTO
  if (id === 0) return '#e57e24'               // BAJO
  return '#1a9c5b'                             // MEDIO
}

const iconoBucket = (id: string | number) => {
  if (typeof id === 'string') return Trophy // ALTO
  if (id === 0) return Package              // BAJO
  return TrendingUp                          // MEDIO
}

const toggleBucket = (id: string | number) => {
  bucketExpandido.value = bucketExpandido.value === id ? null : id
}

// ===================== TAREA 6: MÁS VENDIDOS ========================
const productosMasVendidos = ref<ProductoMasVendido[]>([])
const limiteTop = ref(10)
const recalculando = ref(false)
const mensajeRecalculo = ref<string | null>(null)
const mensajeRecalculoTipo = ref<'ok' | 'error'>('ok')

const ultimaActualizacion = computed<string>(() => {
  if (!productosMasVendidos.value.length) return '—'
  const fechas = productosMasVendidos.value.map(p => new Date(p.actualizadoEn).getTime())
  return formatearFecha(new Date(Math.max(...fechas)).toISOString())
})

// ===================== UTILIDADES ========================
const formatearMoneda = (v: number): string =>
  new Intl.NumberFormat('es-CL', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(v)

const formatearFecha = (iso: string | null | undefined): string => {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('es-CL', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}

// ===================== MÉTODOS ========================
const cargarVolumen = async () => {
  errorVolumen.value = null
  try {
    bucketsVolumen.value = await mongoReporteServicio.obtenerVolumenProyectado()
  } catch (e: any) {
    errorVolumen.value = e?.response?.data || e?.message || 'No se pudo cargar el volumen de ventas proyectado'
  }
}

const cargarMasVendidos = async () => {
  errorMasVendidos.value = null
  try {
    productosMasVendidos.value = await mongoReporteServicio.obtenerProductosMasVendidos(limiteTop.value)
  } catch (e: any) {
    errorMasVendidos.value = e?.response?.data?.error || e?.message || 'No se pudieron cargar los productos más vendidos'
  }
}

const cargarTodo = async () => {
  cargando.value = true
  await Promise.all([cargarVolumen(), cargarMasVendidos()])
  cargando.value = false
}

const recalcular = async () => {
  recalculando.value = true
  mensajeRecalculo.value = null
  try {
    const res = await mongoReporteServicio.recalcularProductosMasVendidos()
    mensajeRecalculoTipo.value = 'ok'
    mensajeRecalculo.value = `${res.mensaje} (${res.productosEnRanking} productos)`
    await cargarMasVendidos()
  } catch (e: any) {
    mensajeRecalculoTipo.value = 'error'
    mensajeRecalculo.value = e?.response?.data?.error || e?.message || 'Error al recalcular'
  } finally {
    recalculando.value = false
    setTimeout(() => { mensajeRecalculo.value = null }, 6000)
  }
}

onMounted(cargarTodo)
</script>

<template>
  <div class="pagina">

    <!-- ===== ENCABEZADO ===== -->
    <div class="encabezado">
      <div class="encabezado-titulo">
        <span class="icono-mongo"><BarChart2 :size="22" /></span>
        <div>
          <h1 class="titulo-pagina">Panel de Ventas</h1>
          <p class="subtitulo">Resumen de ventas y productos más vendidos, actualizado en tiempo real</p>
        </div>
      </div>
      <button class="btn-refrescar" @click="cargarTodo" :disabled="cargando" title="Recargar datos">
        <RefreshCw :size="15" :class="{ girando: cargando }" />
        <span>{{ cargando ? 'Cargando...' : 'Recargar' }}</span>
      </button>
    </div>

    <!-- ===== ESTADO: CARGANDO ===== -->
    <div v-if="cargando" class="estado-cargando">
      <div class="spinner" />
      <span>Cargando datos...</span>
    </div>

    <template v-else>

      <!-- ════════════════════════════════════════════════ -->
      <!--  TAREA 4: AGGREGATION PIPELINE                  -->
      <!-- ════════════════════════════════════════════════ -->
      <section class="seccion">
        <div class="seccion-header">
          <div class="seccion-titulo-grupo">
            <h2 class="seccion-titulo">
              <Layers :size="18" /> Volumen de Ventas Proyectado
            </h2>
          </div>
        </div>

        <!-- Error volumen -->
        <div v-if="errorVolumen" class="alerta-error">
          <AlertCircle :size="16" /> {{ errorVolumen }}
        </div>

        <!-- Sin datos -->
        <div v-else-if="!bucketsVolumen.length" class="sin-datos">
          No hay proyecciones activas. Los carritos en estado <strong>ACTIVO</strong> con ítems aparecerán aquí.
        </div>

        <!-- Buckets -->
        <div v-else class="buckets-grid">
          <div
            v-for="bucket in bucketsVolumen"
            :key="String(bucket._id)"
            class="bucket-card"
            :style="{ '--color-bucket': colorBucket(bucket._id) }"
          >
            <!-- Cabecera del bucket (clickeable para expandir) -->
            <button class="bucket-header" @click="toggleBucket(bucket._id)">
              <div class="bucket-info">
                <component :is="iconoBucket(bucket._id)" :size="22" class="bucket-icono" />
                <div>
                  <p class="bucket-etiqueta">{{ etiquetaBucket(bucket._id) }}</p>
                  <p class="bucket-cantidad">{{ bucket.cantidad }} proyección{{ bucket.cantidad !== 1 ? 'es' : '' }}</p>
                </div>
              </div>
              <ChevronDown v-if="bucketExpandido !== bucket._id" :size="18" class="chevron" />
              <ChevronUp v-else :size="18" class="chevron" />
            </button>

            <!-- Tabla de proyecciones (expandible) -->
            <div v-if="bucketExpandido === bucket._id" class="bucket-detalle">
              <table class="tabla-proyecciones">
                <thead>
                  <tr>
                    <th>Cliente ID</th>
                    <th>Categoría</th>
                    <th class="txt-r">Volumen Proyectado</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(p, i) in bucket.proyecciones" :key="i" class="fila">
                    <td><span class="badge-id">#{{ p.clienteId }}</span></td>
                    <td>{{ p.categoria || '—' }}</td>
                    <td class="txt-r monto">${{ formatearMoneda(p.volumen) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <!-- ════════════════════════════════════════════════ -->
      <!--  TAREA 6: VISTA MATERIALIZADA / CHANGE STREAMS  -->
      <!-- ════════════════════════════════════════════════ -->
      <section class="seccion">
        <div class="seccion-header">
          <div class="seccion-titulo-grupo">
            <h2 class="seccion-titulo">
              <Trophy :size="18" /> Productos Más Vendidos
            </h2>
          </div>
          <span class="seccion-subtitulo">
            <RefreshCw :size="12" /> Se actualiza automáticamente con cada venta
          </span>
        </div>

        <!-- Controles Tarea 6 -->
        <div class="controles-t6">
          <div class="control-campo">
            <label for="limite-top" class="control-label">Top N</label>
            <select id="limite-top" v-model="limiteTop" class="select-control" @change="cargarMasVendidos">
              <option :value="5">Top 5</option>
              <option :value="10">Top 10</option>
              <option :value="20">Top 20</option>
              <option :value="50">Top 50</option>
            </select>
          </div>

          <button class="btn-recalcular" @click="recalcular" :disabled="recalculando" title="Fuerza una actualización inmediata del ranking">
            <RefreshCw :size="14" :class="{ girando: recalculando }" />
            <span>{{ recalculando ? 'Actualizando...' : 'Actualizar ahora' }}</span>
          </button>

          <div class="ultima-actualizacion">
            <span class="dot-live" title="Se actualiza solo con cada venta confirmada" />
            Actualizado: <strong>{{ ultimaActualizacion }}</strong>
          </div>
        </div>

        <!-- Feedback recálculo -->
        <div v-if="mensajeRecalculo" class="alerta-feedback" :class="{ 'alerta-feedback--error': mensajeRecalculoTipo === 'error' }">
          <CheckCircle2 v-if="mensajeRecalculoTipo === 'ok'" :size="16" />
          <AlertCircle v-else :size="16" />
          {{ mensajeRecalculo }}
        </div>

        <!-- Error -->
        <div v-if="errorMasVendidos" class="alerta-error">
          <AlertCircle :size="16" /> {{ errorMasVendidos }}
        </div>

        <!-- Sin datos -->
        <div v-else-if="!productosMasVendidos.length" class="sin-datos">
          Todavía no hay ventas registradas. En cuanto se confirme una orden, el ranking se actualiza solo.
        </div>

        <!-- Ranking -->
        <div v-else class="ranking-wrapper">
          <table class="tabla-ranking">
            <thead>
              <tr>
                <th class="col-pos">#</th>
                <th>Producto</th>
                <th class="txt-r">Unidades</th>
                <th class="txt-r">Órdenes</th>
                <th class="txt-r">Monto Total</th>
                <th class="txt-r">Última Venta</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(prod, idx) in productosMasVendidos"
                :key="prod._id"
                class="fila"
                :class="{ 'fila-podio': idx < 3 }"
              >
                <td class="col-pos">
                  <Medal v-if="idx < 3" :size="18" class="medalla" :class="`medalla-${idx + 1}`" />
                  <span v-else class="posicion">{{ idx + 1 }}</span>
                </td>
                <td>
                  <div class="producto-celda">
                    <Package :size="14" class="ico-pkg" />
                    <span>{{ prod.nombreProducto }}</span>
                  </div>
                </td>
                <td class="txt-r">
                  <span class="badge-unidades">{{ prod.unidadesVendidas.toLocaleString('es-CL') }}</span>
                </td>
                <td class="txt-r numero">{{ prod.ordenesConfirmadas }}</td>
                <td class="txt-r monto">${{ formatearMoneda(Number(prod.montoTotalVendido)) }}</td>
                <td class="txt-r fecha">{{ formatearFecha(prod.ultimaVentaEn) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

    </template>
  </div>
</template>

<style scoped>
/* ── Layout general ────────────────────────────────────── */
.pagina {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ── Encabezado ─────────────────────────────────────────── */
.encabezado {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.encabezado-titulo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icono-mongo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #156895, #0f4f72);
  color: white;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(21, 104, 149, 0.35);
  font-size: 1.3rem;
  line-height: 1;
}

.titulo-pagina {
  font-size: 1.4rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.subtitulo {
  font-size: 0.78rem;
  color: #888;
  margin: 2px 0 0;
}

/* ── Botones de acción ───────────────────────────────────── */
.btn-refrescar {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 18px;
  background: #156895;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-refrescar:hover:not(:disabled) { background: #0f4f72; }
.btn-refrescar:disabled { background: #a0b8c8; cursor: not-allowed; }

.btn-recalcular {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  background: #f5f9fc;
  color: #156895;
  border: 1px solid #b8d8ee;
  border-radius: 8px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.btn-recalcular:hover:not(:disabled) { background: #e8f4fd; border-color: #156895; }
.btn-recalcular:disabled { opacity: 0.5; cursor: not-allowed; }

/* ── Spinner giratorio ──────────────────────────────────── */
@keyframes girar {
  to { transform: rotate(360deg); }
}
.girando {
  animation: girar 0.8s linear infinite;
}

/* ── Estado cargando ─────────────────────────────────────── */
.estado-cargando {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px;
  color: #156895;
  font-size: 0.95rem;
  font-weight: 500;
}
.spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #b8d8ee;
  border-top-color: #156895;
  border-radius: 50%;
  animation: girar 0.8s linear infinite;
}

/* ── Secciones ───────────────────────────────────────────── */
.seccion {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.seccion-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 18px 22px;
  border-bottom: 1px solid #eef1f5;
  background: #fafbfd;
}

.seccion-titulo-grupo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.seccion-titulo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.seccion-subtitulo {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.78rem;
  color: #888;
}

/* ── Alertas ────────────────────────────────────────────── */
.alerta-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 22px;
  padding: 12px 16px;
  background: #ffebee;
  color: #c62828;
  border: 1px solid #ffcdd2;
  border-radius: 8px;
  font-size: 0.875rem;
}

.alerta-feedback {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 22px 0;
  padding: 10px 16px;
  background: #e6f7ef;
  color: #1a7a47;
  border: 1px solid #b2e4cc;
  border-radius: 8px;
  font-size: 0.875rem;
}

.alerta-feedback--error {
  background: #ffebee;
  color: #c62828;
  border-color: #ffcdd2;
}

.sin-datos {
  padding: 40px 22px;
  text-align: center;
  color: #888;
  font-size: 0.9rem;
}

/* ── Tarea 4: Buckets ──────────────────────────────────── */
.buckets-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.bucket-card {
  border-bottom: 1px solid #f0f4f8;
}
.bucket-card:last-child { border-bottom: none; }

.bucket-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 22px;
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.bucket-header:hover { background: #fafbfd; }

.bucket-info {
  display: flex;
  align-items: center;
  gap: 14px;
}

.bucket-icono {
  color: var(--color-bucket);
  flex-shrink: 0;
}

.bucket-etiqueta {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-bucket);
  margin: 0;
}

.bucket-cantidad {
  font-size: 0.78rem;
  color: #888;
  margin: 2px 0 0;
}

.chevron {
  color: #aaa;
  flex-shrink: 0;
}

.bucket-detalle {
  padding: 0 22px 18px;
}

/* ── Tarea 6: Controles ─────────────────────────────────── */
.controles-t6 {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 22px;
  border-bottom: 1px solid #eef1f5;
  background: #fafbfd;
}

.control-campo {
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.select-control {
  padding: 6px 10px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 0.875rem;
  cursor: pointer;
  background: white;
  outline: none;
  transition: border-color 0.2s;
}
.select-control:focus { border-color: #156895; }

.ultima-actualizacion {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 0.78rem;
  color: #777;
  margin-left: auto;
}

.dot-live {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #1a9c5b;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(26, 156, 91, 0.2);
  animation: pulso 2s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes pulso {
  0%, 100% { box-shadow: 0 0 0 3px rgba(26, 156, 91, 0.2); }
  50%       { box-shadow: 0 0 0 6px rgba(26, 156, 91, 0.0); }
}

/* ── Tablas compartidas ─────────────────────────────────── */
.tabla-proyecciones,
.tabla-ranking {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.tabla-proyecciones thead tr,
.tabla-ranking thead tr {
  background: #f7f9fc;
}

.tabla-proyecciones th,
.tabla-ranking th {
  padding: 10px 16px;
  text-align: left;
  font-size: 0.75rem;
  font-weight: 700;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 2px solid #e8edf2;
  white-space: nowrap;
}

.tabla-proyecciones td,
.tabla-ranking td {
  padding: 11px 16px;
  border-bottom: 1px solid #f4f6f9;
  color: #333;
}

.fila:hover td { background: #f7fbff; }
.fila:last-child td { border-bottom: none; }

.ranking-wrapper {
  overflow-x: auto;
}

/* ── Ranking: columnas especiales ───────────────────────── */
.col-pos {
  width: 50px;
  text-align: center !important;
}

.medalla-1 { color: #d4af37; } /* oro */
.medalla-2 { color: #a8a8a8; } /* plata */
.medalla-3 { color: #b08d57; } /* bronce */

.posicion {
  color: #888;
  font-weight: 600;
  font-size: 0.85rem;
}

.fila-podio td { background: #fefdf7; }

.producto-celda {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ico-pkg { color: #aaa; flex-shrink: 0; }

.badge-id {
  display: inline-block;
  background: #e8f4fd;
  color: #156895;
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 12px;
}


.badge-unidades {
  display: inline-block;
  background: #e6f7ef;
  color: #1a9c5b;
  font-weight: 700;
  font-size: 0.82rem;
  padding: 3px 10px;
  border-radius: 20px;
  font-family: monospace;
}

.txt-r { text-align: right !important; }
.monto  { font-family: monospace; font-weight: 600; color: #156895; }
.numero { font-family: monospace; font-weight: 600; color: #1a1a2e; }
.fecha  { color: #888; font-size: 0.82rem; white-space: nowrap; }

/* ── Responsive ─────────────────────────────────────────── */
@media (max-width: 768px) {
  .encabezado { flex-direction: column; align-items: flex-start; }
  .seccion-header { flex-direction: column; align-items: flex-start; }
  .controles-t6 { flex-direction: column; align-items: flex-start; }
  .ultima-actualizacion { margin-left: 0; }
}
</style>
