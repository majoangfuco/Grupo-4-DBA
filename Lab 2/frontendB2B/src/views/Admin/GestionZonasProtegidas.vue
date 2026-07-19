<script setup lang="ts">
// ============================================================
// GestionZonasProtegidas.vue
// Herramienta de mantenimiento: elegir una comuna, ver sus unidades
// vecinales en el mapa y marcar/desmarcar cuáles son zona residencial
// protegida (PATCH existente). Vista separada de Mapa-PaginaAdmin.vue
// (que solo muestra las zonas ya protegidas, de solo lectura) porque
// este es un workflow de edición con su propio ciclo de vida
// (elegir comuna → zoom → click para togglear), no un dashboard.
// ============================================================

import { ref, computed, onMounted, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import httpClient from '@/http-common'
import { categoriaServicio, type CategoriaEntidad } from '@/services/categoriaServicio'

interface ComunaEntidad {
  id: number
  nombre: string
  distrito_postal: string
}

interface UnidadVecinal {
  id: number
  nombreUv: string
  codigoUv: string
  esZonaProtegida: boolean
}

interface ConteoComuna {
  total: number
  protegidas: number
}

// Mismo morado que usa Mapa-PaginaAdmin.vue para "zona protegida"
// (estiloZonaProtegida) — se reutiliza la paleta ya establecida para
// este mismo concepto en vez de inventar un color nuevo.
const COLOR_PROTEGIDA = { fill: '#7c3aed', border: '#5b21b6' }
const COLOR_NO_PROTEGIDA = { fill: '#9ca3af', border: '#6b7280' }

const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let capaUvs: L.GeoJSON | null = null
// Capa de fondo con TODAS las zonas ya protegidas (de cualquier comuna),
// siempre visible, sin toggle — independiente de qué comuna esté elegida
// para editar. Se agrega una sola vez al montar y se re-agrega (nunca se
// togglea "apagada") cada vez que un toggle individual o masivo cambia el
// set de UVs protegidas, para no quedar desactualizada.
let capaProtegidasBase: L.GeoJSON | null = null

const comunas = ref<ComunaEntidad[]>([])
const comunaSeleccionadaId = ref<number | null>(null)
const unidadesVecinales = ref<UnidadVecinal[]>([])
const conteosPorComuna = ref<Record<number, ConteoComuna>>({})

const cargandoComunas = ref(false)
const cargandoUvs = ref(false)
const actualizandoId = ref<number | null>(null)
const procesandoMasivo = ref(false)
const error = ref('')
const mensajeMasivo = ref('')

// Categorías restringidas en zona residencial — mismo control que
// FormularioGestionCategorias.vue, replicado aquí para no obligar al admin
// a saltar de pantalla mientras marca zonas. Es una lista global (no una
// relación por zona): togglear una categoría afecta a TODAS las zonas
// protegidas por igual.
const categorias = ref<CategoriaEntidad[]>([])
const cargandoCategorias = ref(false)
const actualizandoCategoriaId = ref<number | null>(null)
const errorCategorias = ref('')

// Combobox de comuna con búsqueda local (52 items, sin fetch por tecla)
const comunaFiltro = ref('')
const mostrarDropdownComunas = ref(false)

const comunasFiltradas = computed(() => {
  const filtro = comunaFiltro.value.trim().toLowerCase()
  if (!filtro) return comunas.value
  return comunas.value.filter((c) => c.nombre.toLowerCase().includes(filtro))
})

const contadorProtegidas = computed(
  () => unidadesVecinales.value.filter((uv) => uv.esZonaProtegida).length,
)

const extraerError = (err: unknown, fallback: string): string => {
  const e = err as { response?: { data?: unknown } }
  const data = e.response?.data
  if (typeof data === 'string' && data) return data
  if (data && typeof data === 'object' && 'message' in data) {
    return String((data as { message: string }).message)
  }
  return fallback
}

const estiloUv = (feature: any): L.PathOptions => {
  const protegida = Boolean(feature?.properties?.esZonaProtegida)
  return {
    fillColor: protegida ? COLOR_PROTEGIDA.fill : COLOR_NO_PROTEGIDA.fill,
    color: protegida ? COLOR_PROTEGIDA.border : COLOR_NO_PROTEGIDA.border,
    weight: 1.5,
    fillOpacity: 0.55,
  }
}

const aplicarEstiloCapa = (layer: L.Layer, protegida: boolean) => {
  ;(layer as L.Path).setStyle({
    fillColor: protegida ? COLOR_PROTEGIDA.fill : COLOR_NO_PROTEGIDA.fill,
    color: protegida ? COLOR_PROTEGIDA.border : COLOR_NO_PROTEGIDA.border,
  })
}

const cargarComunas = async () => {
  cargandoComunas.value = true
  error.value = ''
  try {
    const res = await httpClient.get<ComunaEntidad[]>('/api/admin/comunas')
    comunas.value = res.data
  } catch (err) {
    error.value = extraerError(err, 'No se pudieron cargar las comunas.')
  } finally {
    cargandoComunas.value = false
  }
}

// Conteo agregado por comuna (sin geometría), una sola vez al montar, para
// poder mostrar "X de Y protegidas" en el combobox mientras se filtra —
// sin pagar 52 fetches de GeoJSON solo para contar.
const cargarConteos = async () => {
  try {
    const res = await httpClient.get<Array<{ comunaId: number; total: number; protegidas: number }>>(
      '/api/admin/unidades-vecinales/conteo-por-comuna',
    )
    const mapa: Record<number, ConteoComuna> = {}
    res.data.forEach((c) => {
      mapa[c.comunaId] = { total: c.total, protegidas: c.protegidas }
    })
    conteosPorComuna.value = mapa
  } catch (err) {
    // No bloquea el flujo principal: el combobox sigue funcionando sin
    // conteos (simplemente no se muestra la línea "X de Y") si esto falla.
    console.error('Error obteniendo conteo por comuna:', err)
  }
}

// Capa de fondo (GET /protegidas, mismo endpoint que usa Mapa-PaginaAdmin.vue)
// con TODAS las zonas ya protegidas, sin importar la comuna elegida. Se
// re-llama tras cada toggle exitoso (individual o masivo) para que no quede
// desactualizada; siempre se re-agrega DESPUÉS de reafirmar capaUvs al
// frente, así la capa editable de la comuna elegida nunca queda tapada.
const cargarCapaProtegidasBase = async () => {
  if (!map) return
  try {
    const res = await httpClient.get('/api/admin/unidades-vecinales/protegidas')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    if (capaProtegidasBase) {
      map.removeLayer(capaProtegidasBase)
    }
    capaProtegidasBase = L.geoJSON(geojson, {
      style: estiloUv,
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        layer.bindTooltip(props.nombreUv ?? 'UV sin nombre', { sticky: true })
      },
    }).addTo(map)

    // La capa editable de la comuna elegida (si hay una) debe seguir por
    // encima de este fondo recién agregado, nunca tapada por él.
    capaUvs?.bringToFront()
  } catch (err) {
    console.error('Error cargando la capa base de zonas protegidas:', err)
  }
}

const cargarUvsDeComuna = async (comunaId: number) => {
  if (!map) return
  cargandoUvs.value = true
  error.value = ''
  try {
    const res = await httpClient.get(`/api/admin/unidades-vecinales?comunaId=${comunaId}`)
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    unidadesVecinales.value = (geojson.features ?? []).map((f: any) => ({
      id: f.properties.id,
      nombreUv: f.properties.nombreUv,
      codigoUv: f.properties.codigoUv,
      esZonaProtegida: f.properties.esZonaProtegida,
    }))

    if (capaUvs) {
      map.removeLayer(capaUvs)
    }
    capaUvs = L.geoJSON(geojson, {
      style: estiloUv,
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        layer.bindTooltip(props.nombreUv ?? 'UV sin nombre', { sticky: true })
        layer.on('click', () => toggleZonaProtegida(props.id, layer))
      },
    }).addTo(map)
    capaUvs.bringToFront() // por encima de la capa base de protegidas (sin conflicto visual)

    const bounds = capaUvs.getBounds()
    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [20, 20] })
    }
  } catch (err) {
    unidadesVecinales.value = []
    error.value = extraerError(err, 'No se pudieron cargar las unidades vecinales de esta comuna.')
  } finally {
    cargandoUvs.value = false
  }
}

const seleccionarComuna = (comuna: ComunaEntidad) => {
  comunaSeleccionadaId.value = comuna.id
  comunaFiltro.value = comuna.nombre
  mostrarDropdownComunas.value = false
  mensajeMasivo.value = ''
  cargarUvsDeComuna(comuna.id)
}

// El mousedown.prevent en cada opción evita que el input pierda foco antes
// del click (así seleccionarComuna corre primero); este blur es solo el
// fallback para cerrar el dropdown al hacer click fuera del combobox.
const onBlurCombo = () => {
  window.setTimeout(() => {
    mostrarDropdownComunas.value = false
  }, 150)
}

// Toggle con actualización visual optimista: pinta el nuevo color de
// inmediato (antes de que el PATCH responda) y revierte color + estado
// si el PATCH falla.
const toggleZonaProtegida = async (id: number, layer: L.Layer) => {
  const uv = unidadesVecinales.value.find((u) => u.id === id)
  if (!uv || actualizandoId.value != null) return

  const valorAnterior = uv.esZonaProtegida
  const nuevoValor = !valorAnterior

  uv.esZonaProtegida = nuevoValor
  aplicarEstiloCapa(layer, nuevoValor)
  actualizandoId.value = id
  error.value = ''

  try {
    await httpClient.patch(`/api/admin/unidades-vecinales/${id}/zona-protegida`, {
      esZonaProtegida: nuevoValor,
    })
    await cargarCapaProtegidasBase() // mantiene la capa de fondo al día con este cambio
  } catch (err) {
    uv.esZonaProtegida = valorAnterior
    aplicarEstiloCapa(layer, valorAnterior)
    error.value = extraerError(err, 'No se pudo actualizar la zona protegida, se revirtió el cambio.')
  } finally {
    actualizandoId.value = null
  }
}

// Marca/desmarca TODAS las UV de la comuna elegida de una sola vez.
// Promise.allSettled (no Promise.all): un PATCH individual que falle no
// debe abortar ni revertir los que ya tuvieron éxito, solo se informa el
// conteo de fallos al final.
const aplicarMasivo = async (nuevoValor: boolean) => {
  if (comunaSeleccionadaId.value == null || !map) return

  const objetivos = unidadesVecinales.value.filter((uv) => uv.esZonaProtegida !== nuevoValor)
  if (objetivos.length === 0) {
    mensajeMasivo.value = nuevoValor
      ? 'Todas las unidades vecinales de esta comuna ya están protegidas.'
      : 'Ninguna unidad vecinal de esta comuna está protegida.'
    return
  }

  const nombreComuna = comunas.value.find((c) => c.id === comunaSeleccionadaId.value)?.nombre ?? 'esta comuna'
  const confirmado = window.confirm(
    `¿${nuevoValor ? 'Marcar' : 'Desmarcar'} ${objetivos.length} unidad(es) vecinal(es) de ${nombreComuna} como ${nuevoValor ? '' : 'no '}protegida(s)?`,
  )
  if (!confirmado) return

  procesandoMasivo.value = true
  error.value = ''
  mensajeMasivo.value = ''

  const resultados = await Promise.allSettled(
    objetivos.map((uv) =>
      httpClient.patch(`/api/admin/unidades-vecinales/${uv.id}/zona-protegida`, {
        esZonaProtegida: nuevoValor,
      }),
    ),
  )

  let exitosos = 0
  let fallidos = 0
  resultados.forEach((resultado, i) => {
    const uv = objetivos[i]
    if (!uv) return
    if (resultado.status === 'fulfilled') {
      uv.esZonaProtegida = nuevoValor
      exitosos++
    } else {
      fallidos++
    }
  })

  // Repinta la capa completa según el estado ya actualizado localmente —
  // más simple que ubicar cada layer individual cuando se tocan decenas
  // de polígonos a la vez.
  capaUvs?.eachLayer((layer) => {
    const feature = (layer as L.Layer & { feature?: { properties?: { id?: number } } }).feature
    const uv = unidadesVecinales.value.find((u) => u.id === feature?.properties?.id)
    if (uv) aplicarEstiloCapa(layer, uv.esZonaProtegida)
  })

  conteosPorComuna.value[comunaSeleccionadaId.value] = {
    total: unidadesVecinales.value.length,
    protegidas: unidadesVecinales.value.filter((u) => u.esZonaProtegida).length,
  }

  if (exitosos > 0) {
    await cargarCapaProtegidasBase() // mantiene la capa de fondo al día con los cambios masivos
  }

  if (fallidos > 0) {
    error.value = `${exitosos} unidad(es) actualizada(s), ${fallidos} fallaron (no se revirtieron las que sí tuvieron éxito).`
  } else {
    mensajeMasivo.value = `${exitosos} unidad(es) actualizada(s) correctamente.`
  }

  procesandoMasivo.value = false
}

const marcarTodas = () => aplicarMasivo(true)
const desmarcarTodas = () => aplicarMasivo(false)

const cargarCategorias = async () => {
  cargandoCategorias.value = true
  errorCategorias.value = ''
  try {
    const res = await categoriaServicio.listar(true)
    categorias.value = res.data
  } catch (err) {
    errorCategorias.value = extraerError(err, 'No se pudieron cargar las categorías.')
  } finally {
    cargandoCategorias.value = false
  }
}

// Sin optimistic update aquí (a diferencia del toggle de UV): esta lista es
// global y afecta a TODAS las zonas protegidas a la vez, mayor radio de
// impacto que un solo polígono. Mismo patrón (pesimista, espera el PATCH)
// que ya usa FormularioGestionCategorias.vue para este mismo control, para
// que se comporte igual sin importar desde qué pantalla se togglee.
const toggleCategoriaRestringida = async (cat: CategoriaEntidad) => {
  errorCategorias.value = ''
  const nuevoValor = !cat.restringida_Zona_Residencial
  actualizandoCategoriaId.value = cat.categoria_ID
  try {
    await categoriaServicio.actualizarRestringidaZonaResidencial(cat.categoria_ID, nuevoValor)
    cat.restringida_Zona_Residencial = nuevoValor
  } catch (err) {
    errorCategorias.value = extraerError(err, 'No se pudo actualizar la restricción de la categoría.')
  } finally {
    actualizandoCategoriaId.value = null
  }
}

const initMap = async () => {
  if (!mapContainer.value) return
  map = L.map(mapContainer.value).setView([-33.4489, -70.6693], 10)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(map)
}

onMounted(async () => {
  await nextTick()
  await initMap()
  await Promise.all([cargarComunas(), cargarConteos(), cargarCategorias(), cargarCapaProtegidasBase()])
})
</script>

<template>
  <div class="gestion-zonas-container">
    <div class="header">
      <h2>Gestión de Zonas Protegidas</h2>
      <p>Selecciona una comuna, luego haz clic en una unidad vecinal del mapa para marcarla o desmarcarla como zona residencial protegida.</p>
    </div>

    <div class="controles">
      <div class="selector-comuna-combo">
        <label for="input-comuna">Comuna</label>
        <div class="combo-wrapper">
          <input
            id="input-comuna"
            type="text"
            v-model="comunaFiltro"
            :disabled="cargandoComunas"
            @focus="mostrarDropdownComunas = true"
            @blur="onBlurCombo"
            placeholder="Buscar comuna..."
            autocomplete="off"
          />
          <ul v-if="mostrarDropdownComunas" class="dropdown-comunas">
            <li
              v-for="c in comunasFiltradas"
              :key="c.id"
              :class="{ activa: c.id === comunaSeleccionadaId }"
              @mousedown.prevent="seleccionarComuna(c)"
            >
              <span class="nombre-comuna">{{ c.nombre }}</span>
              <span v-if="conteosPorComuna[c.id]" class="conteo-comuna">
                {{ conteosPorComuna[c.id]?.protegidas }} de {{ conteosPorComuna[c.id]?.total }} UV protegidas
              </span>
            </li>
            <li v-if="comunasFiltradas.length === 0" class="sin-coincidencias">Sin coincidencias</li>
          </ul>
        </div>
      </div>

      <span v-if="comunaSeleccionadaId != null" class="contador">
        {{ contadorProtegidas }} de {{ unidadesVecinales.length }} unidades vecinales marcadas como protegidas
      </span>

      <div v-if="comunaSeleccionadaId != null && unidadesVecinales.length > 0" class="acciones-masivas">
        <button
          type="button"
          class="btn-masivo btn-marcar-todas"
          :disabled="procesandoMasivo"
          @click="marcarTodas"
        >
          Marcar todas las UV como protegidas
        </button>
        <button
          type="button"
          class="btn-masivo btn-desmarcar-todas"
          :disabled="procesandoMasivo"
          @click="desmarcarTodas"
        >
          Desmarcar todas
        </button>
        <span v-if="procesandoMasivo" class="estado">Aplicando...</span>
      </div>
    </div>

    <p v-if="error" class="msg-error">{{ error }}</p>
    <p v-if="mensajeMasivo" class="msg-ok">{{ mensajeMasivo }}</p>
    <p v-if="cargandoUvs" class="estado">Cargando unidades vecinales...</p>

    <div class="map-wrapper">
      <div ref="mapContainer" class="map-view"></div>
    </div>

    <div class="panel-categorias">
      <h3>Categorías restringidas en zona residencial</h3>
      <p class="ayuda-categorias">
        Esta lista es global: bloquea la categoría en TODAS las zonas protegidas por igual, no es una relación por zona individual.
      </p>
      <p v-if="errorCategorias" class="msg-error">{{ errorCategorias }}</p>
      <p v-if="cargandoCategorias" class="estado">Cargando categorías...</p>
      <ul v-else class="lista-categorias">
        <li v-for="cat in categorias" :key="cat.categoria_ID" class="item-categoria">
          <label>
            <input
              type="checkbox"
              :checked="cat.restringida_Zona_Residencial"
              :disabled="actualizandoCategoriaId === cat.categoria_ID"
              @change="toggleCategoriaRestringida(cat)"
            />
            {{ cat.nombre_Categoria }}
          </label>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.gestion-zonas-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.header {
  margin-bottom: 16px;
}

.header h2 {
  font-size: 1.5rem;
  color: #1f2937;
  margin-bottom: 5px;
}

.header p {
  color: #6b7280;
}

.controles {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.selector-comuna-combo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: #374151;
  font-weight: 600;
}

.combo-wrapper {
  position: relative;
}

.combo-wrapper input {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.88rem;
  min-width: 240px;
}

.dropdown-comunas {
  position: absolute;
  /* Los panes y controles internos de Leaflet usan z-index hasta ~1000
     (.leaflet-top/.leaflet-control ya está en 1000); sin superarlo, el mapa
     tapaba este dropdown aunque estuviera fuera del contenedor del mapa. */
  z-index: 2000;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 260px;
  overflow-y: auto;
  margin: 0;
  padding: 0;
  list-style: none;
  background: white;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.12);
}

.dropdown-comunas li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 10px;
  cursor: pointer;
  font-weight: 400;
}

.dropdown-comunas li:hover,
.dropdown-comunas li.activa {
  background: #f5f3ff;
}

.nombre-comuna {
  font-size: 0.85rem;
  color: #1f2937;
}

.conteo-comuna {
  font-size: 0.72rem;
  color: #7c3aed;
}

.sin-coincidencias {
  color: #9ca3af;
  font-style: italic;
  cursor: default !important;
}

.contador {
  font-size: 0.85rem;
  color: #5b21b6;
  font-weight: 600;
  background: #f5f3ff;
  padding: 4px 10px;
  border-radius: 12px;
}

.acciones-masivas {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-masivo {
  border: 1px solid #d1d5db;
  background: #f9fafb;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  color: #374151;
}

.btn-masivo:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-marcar-todas {
  color: #5b21b6;
  border-color: #c4b5fd;
}

.btn-desmarcar-todas {
  color: #6b7280;
}

.msg-error {
  color: #b02a1f;
  font-size: 0.85rem;
  margin: 4px 0;
}

.msg-ok {
  color: #15803d;
  font-size: 0.85rem;
  margin: 4px 0;
}

.estado {
  color: #888;
  font-style: italic;
  margin: 4px 0;
}

.map-wrapper {
  flex: 1;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  border: 1px solid #e5e7eb;
}

.map-view {
  width: 100%;
  height: 600px;
}

.panel-categorias {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
}

.panel-categorias h3 {
  font-size: 1rem;
  color: #1f2937;
  margin: 0 0 4px;
}

.ayuda-categorias {
  color: #6b7280;
  font-size: 0.78rem;
  margin: 0 0 10px;
}

.lista-categorias {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
}

.item-categoria label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: #374151;
  cursor: pointer;
}

.item-categoria input:disabled {
  cursor: not-allowed;
}
</style>
