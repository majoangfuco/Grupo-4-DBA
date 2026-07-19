<script setup lang="ts">
import { ref, onMounted } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import httpClient from '@/http-common'
import { colorParaNivel, etiquetaParaNivel, COLOR_NIVEL_SEMAFORO, type NivelSemaforo } from '@/constants/nivelSemaforo'

const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null

type VistaMapa = 'comuna' | 'distrito' | 'ninguno'
const vistaActual = ref<VistaMapa>('comuna')

let capaPoligonos: L.GeoJSON | null = null
let capaContornoRM: L.GeoJSON | null = null
let capaZonasProtegidas: L.GeoJSON | null = null
const mostrarZonasProtegidas = ref(false)

let markerVerificacion: L.Marker | null = null
let modoVerificarArmado = false
let botonVerificarEl: HTMLButtonElement | null = null

// Buscador de direcciones — dentro del mismo control "Verificar dirección"
let inputBusquedaEl: HTMLInputElement | null = null
let listaResultadosEl: HTMLUListElement | null = null
let botonLimpiarBusquedaEl: HTMLButtonElement | null = null
let spinnerBusquedaEl: HTMLSpanElement | null = null
let temporizadorBusqueda: ReturnType<typeof setTimeout> | null = null

const formatoCLP = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
})

const initMap = async () => {
  if (!mapContainer.value) return

  map = L.map(mapContainer.value).setView([-33.4489, -70.6693], 10) // Centrado en Santiago por defecto

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)

  agregarControlVerificarDireccion(map)
  agregarControlToggleVista(map)
  agregarControlLeyenda(map)

  await cargarCapaPoligonos(vistaActual.value)
  await cargarContornoRM() // borde fijo de la RM — nunca se remueve/togglea
  await fetchAndDrawAlmacenes() // pines rojos de almacén — sin cambios
}

// ── Capa choropleth (comuna o distrito) ─────────────────────

const construirPopupHtml = (props: Record<string, any>, tipoEtiqueta: string) => {
  const nivel = props.nivel_semaforo as NivelSemaforo
  const lineaMonto = props.tiene_ventas
    ? `Monto total: ${formatoCLP.format(props.monto_total_ventas ?? 0)}<br>`
    : ''
  return `
    <strong>${tipoEtiqueta}</strong><br>
    Distrito postal: ${props.distrito_postal}<br>
    Pedidos: ${props.total_pedidos ?? 0}<br>
    ${lineaMonto}
    <strong>${etiquetaParaNivel(nivel)}</strong>
  `
}

const estiloPoligono = (feature: any): L.PathOptions => {
  const props = feature?.properties || {}
  return {
    fillColor: colorParaNivel(props.nivel_semaforo),
    weight: 1.5,
    opacity: 1,
    color: '#374151',
    fillOpacity: 0.55,
  }
}

const cargarCapaPoligonos = async (vista: VistaMapa) => {
  if (!map) return
  try {
    const endpoint = vista === 'comuna'
      ? '/api/logistica/mapa/comunas'
      : '/api/logistica/mapa/distritos'
    const res = await httpClient.get(endpoint)
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    const nuevaCapa = L.geoJSON(geojson, {
      style: estiloPoligono,
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        const etiqueta = vista === 'comuna' ? `Comuna: ${props.nombre}` : `Distrito ${props.distrito_postal}`
        layer.bindTooltip(construirPopupHtml(props, etiqueta), { sticky: true })
      }
    })

    // Reemplaza SOLO la capa de polígonos, sin tocar zoom/centro del mapa.
    if (capaPoligonos) {
      map.removeLayer(capaPoligonos)
    }
    capaPoligonos = nuevaCapa.addTo(map)
    mantenerContornoAlFrente()
  } catch (error) {
    console.error('Error obteniendo capa de ventas por', vista, error)
  }
}

const cambiarVista = (vista: VistaMapa) => {
  if (vista === vistaActual.value) return
  vistaActual.value = vista

  if (vista === 'ninguno') {
    // Solo apaga el choropleth de ventas: sin fetch, contorno RM y zonas
    // protegidas (si están activas) siguen visibles sin cambios.
    if (map && capaPoligonos) {
      map.removeLayer(capaPoligonos)
    }
    capaPoligonos = null
    return
  }

  cargarCapaPoligonos(vista)
}

// ── Contorno fijo de la RM (borde real, ST_Union de las 52 comunas) ──
// Se carga UNA vez en initMap y nunca se remueve ni se togglea — vive
// independiente del ciclo de vida de capaPoligonos/capaZonasProtegidas.

const cargarContornoRM = async () => {
  if (!map) return
  try {
    const res = await httpClient.get('/api/logistica/mapa/cobertura')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    capaContornoRM = L.geoJSON(geojson, {
      style: {
        fillOpacity: 0,
        color: '#1d4ed8', // azul — no colisiona con la paleta magenta/cian/amarillo/gris del choropleth, y evita rojo/verde
        weight: 3,
        opacity: 0.9,
      },
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        layer.bindTooltip(props.nombre || 'Área de cobertura', { sticky: true })
      },
    }).addTo(map)
    mantenerContornoAlFrente()
  } catch (error) {
    console.error('Error obteniendo contorno de cobertura RM:', error)
  }
}

// Se reinvoca cada vez que se agrega/reemplaza otra capa de polígonos
// (choropleth o zonas protegidas), para que el contorno RM quede siempre
// visible por encima y no lo tape el fill de las capas más nuevas.
const mantenerContornoAlFrente = () => {
  capaContornoRM?.bringToFront()
}

// ── Almacenes (pines rojos) — SIN CAMBIOS ───────────────────

const fetchAndDrawAlmacenes = async () => {
  try {
    const res = await httpClient.get('/api/almacenes/geojson')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    // Icono para almacenes
    const iconAlmacen = L.icon({
      iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
      shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41]
    })

    L.geoJSON(geojson, {
      pointToLayer: (feature, latlng) => {
        return L.marker(latlng, { icon: iconAlmacen })
      },
      onEachFeature: (feature, layer) => {
        const props = feature.properties
        layer.bindPopup(`
          <strong>Almacén: ${props.nombre}</strong><br>
          Dirección: ${props.direccion}
        `)
      }
    }).addTo(map!)
  } catch (error) {
    console.error('Error obteniendo almacenes:', error)
  }
}

// ── Zonas residenciales protegidas — toggle independiente ───
// Capa opcional (desactivada por defecto), no interfiere con el toggle
// Comuna/Distrito: ambas pueden verse a la vez.

const estiloZonaProtegida = (): L.PathOptions => ({
  fillColor: '#7c3aed', // morado — distinto de la paleta del choropleth y del azul del contorno RM
  fillOpacity: 0.45,
  color: '#5b21b6',
  weight: 2,
  dashArray: '6 4', // patrón rayado, para no confundirse con el fill sólido del choropleth
})

const construirPopupZonaProtegida = (props: Record<string, any>) => {
  const div = document.createElement('div')
  div.innerHTML = `
    <strong>Zona residencial protegida</strong><br>
    ${props.nombreUv ?? 'Sin nombre'}<br>
    <button type="button" class="btn-desmarcar-zona">Desmarcar zona protegida</button>
  `
  const boton = div.querySelector<HTMLButtonElement>('.btn-desmarcar-zona')
  boton?.addEventListener('click', async () => {
    try {
      await httpClient.patch(`/api/admin/unidades-vecinales/${props.id}/zona-protegida`, {
        esZonaProtegida: false,
      })
      await cargarCapaZonasProtegidas()
    } catch (error) {
      console.error('Error desmarcando zona protegida:', error)
    }
  })
  return div
}

const cargarCapaZonasProtegidas = async () => {
  if (!map) return
  try {
    const res = await httpClient.get('/api/admin/unidades-vecinales/protegidas')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    const nuevaCapa = L.geoJSON(geojson, {
      style: estiloZonaProtegida,
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        layer.bindPopup(() => construirPopupZonaProtegida(props))
      },
    })

    if (capaZonasProtegidas) {
      map.removeLayer(capaZonasProtegidas)
    }
    capaZonasProtegidas = nuevaCapa.addTo(map)
    mantenerContornoAlFrente() // el contorno RM debe quedar por encima de esta capa también
  } catch (error) {
    console.error('Error obteniendo zonas protegidas:', error)
  }
}

const toggleZonasProtegidas = () => {
  mostrarZonasProtegidas.value = !mostrarZonasProtegidas.value
  if (!map) return
  if (mostrarZonasProtegidas.value) {
    cargarCapaZonasProtegidas()
  } else if (capaZonasProtegidas) {
    map.removeLayer(capaZonasProtegidas)
    capaZonasProtegidas = null
  }
}

// ── Verificar dirección — simulador de pin único ────────────
// Reemplaza al antiguo "Mostrar clientes (pines)": ya no se listan todas
// las direcciones a la vez, solo se puede colocar UN pin de prueba y
// consultar POST /api/admin/direccion/verificar para ese punto.

const construirPopupVerificacion = (resultado: any): string => {
  if (!resultado.dentroCobertura) {
    return `<strong>Fuera del área de cobertura</strong>`
  }

  let html = `
    <strong>Dentro del área de cobertura</strong><br>
    Comuna: ${resultado.comunaNombre ?? '—'}<br>
    Distrito postal: ${resultado.distritoPostal ?? '—'}<br>
  `

  if (resultado.zonaProtegida) {
    const nombresCategorias = (resultado.categoriasRestringidas ?? [])
      .map((c: { id: number; nombre: string }) => c.nombre)
      .join(', ')
    html += `
      <strong style="color:#7c3aed">Zona residencial protegida: ${resultado.nombreZonaProtegida}</strong><br>
      Categorías restringidas: ${nombresCategorias || 'ninguna activa'}
    `
  } else {
    html += `Sin restricciones de categoría en esta zona.`
  }

  return html
}

const verificarPunto = async (marker: L.Marker) => {
  const { lat, lng } = marker.getLatLng()
  try {
    const res = await httpClient.post('/api/admin/direccion/verificar', { lat, lng })
    marker.bindPopup(construirPopupVerificacion(res.data)).openPopup()
  } catch (error) {
    console.error('Error verificando dirección:', error)
    marker.bindPopup('<strong>Error al verificar la dirección</strong>').openPopup()
  }
}

// Coloca (o reemplaza) el único pin de verificación — nunca se acumulan.
// Compartido entre el click manual en el mapa y la selección de un
// resultado del buscador de direcciones (misma lógica de marker, sin duplicar).
const posicionarMarcador = (latlng: L.LatLng) => {
  if (!map) return
  if (markerVerificacion) {
    map.removeLayer(markerVerificacion)
  }
  markerVerificacion = L.marker(latlng, { draggable: true }).addTo(map)
  markerVerificacion.on('dragend', () => {
    if (markerVerificacion) verificarPunto(markerVerificacion)
  })
  verificarPunto(markerVerificacion)
}

const colocarPin = (e: L.LeafletMouseEvent) => {
  posicionarMarcador(e.latlng)
  desarmarModoVerificar()
}

const desarmarModoVerificar = () => {
  modoVerificarArmado = false
  if (map) {
    map.getContainer().style.cursor = ''
    map.off('click', colocarPin)
  }
  botonVerificarEl?.classList.remove('activo')
}

// ── Buscador de direcciones (geocodificación) ───────────────
// Mecanismo adicional para posicionar el mismo pin de verificación —
// el click manual en el mapa sigue funcionando en paralelo, sin cambios.

type ResultadoGeocodificacion = { displayName: string; lat: number; lon: number }

const escapeHtml = (texto: string): string =>
  texto
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

// Nominatim entrega display_name como una sola cadena separada por comas
// (más específico primero, más general al final). Se parte en 2 líneas
// con jerarquía visual: los primeros 2 segmentos (típicamente calle +
// número) en negrita arriba, el resto (comuna/región/país) tenue abajo.
const partirDisplayName = (displayName: string): { primaria: string; secundaria: string } => {
  const partes = displayName.split(',').map((p) => p.trim()).filter(Boolean)
  if (partes.length <= 2) {
    return { primaria: partes.join(', '), secundaria: '' }
  }
  return { primaria: partes.slice(0, 2).join(', '), secundaria: partes.slice(2).join(', ') }
}

const construirTarjetaResultado = (resultado: ResultadoGeocodificacion, idx: number): string => {
  const { primaria, secundaria } = partirDisplayName(resultado.displayName)
  return `
    <li data-idx="${idx}" class="resultado-item">
      <span class="resultado-pin">📍</span>
      <span class="resultado-texto">
        <span class="resultado-primaria">${escapeHtml(primaria)}</span>
        ${secundaria ? `<span class="resultado-secundaria">${escapeHtml(secundaria)}</span>` : ''}
      </span>
    </li>
  `
}

const limpiarResultadosBusqueda = () => {
  if (listaResultadosEl) listaResultadosEl.innerHTML = ''
}

const mostrarSpinnerBusqueda = (mostrar: boolean) => {
  if (spinnerBusquedaEl) spinnerBusquedaEl.style.display = mostrar ? '' : 'none'
  // Mientras busca, oculta el botón de limpiar para no solaparse con el
  // spinner (ambos viven en la misma esquina del input); al terminar,
  // lo restaura solo si sigue habiendo texto escrito.
  if (botonLimpiarBusquedaEl) {
    botonLimpiarBusquedaEl.style.display = mostrar ? 'none' : inputBusquedaEl?.value ? '' : 'none'
  }
}

const limpiarBusquedaCompleta = () => {
  if (inputBusquedaEl) inputBusquedaEl.value = ''
  if (botonLimpiarBusquedaEl) botonLimpiarBusquedaEl.style.display = 'none'
  if (temporizadorBusqueda) clearTimeout(temporizadorBusqueda)
  mostrarSpinnerBusqueda(false)
  limpiarResultadosBusqueda()
  inputBusquedaEl?.focus()
}

const seleccionarResultadoBusqueda = (resultado: ResultadoGeocodificacion) => {
  if (!map) return
  const latlng = L.latLng(resultado.lat, resultado.lon)
  map.flyTo(latlng, 16)
  posicionarMarcador(latlng)
  limpiarResultadosBusqueda()
  if (inputBusquedaEl) inputBusquedaEl.value = resultado.displayName
  if (botonLimpiarBusquedaEl) botonLimpiarBusquedaEl.style.display = ''
}

const renderizarResultadosBusqueda = (resultados: ResultadoGeocodificacion[]) => {
  if (!listaResultadosEl) return
  if (resultados.length === 0) {
    listaResultadosEl.innerHTML = `<li class="sin-resultados">Sin resultados</li>`
    return
  }
  listaResultadosEl.innerHTML = resultados
    .map((r, i) => construirTarjetaResultado(r, i))
    .join('')
  listaResultadosEl.querySelectorAll<HTMLLIElement>('li').forEach((li) => {
    const idx = Number(li.dataset.idx)
    const resultado = resultados[idx]
    if (resultado) li.addEventListener('click', () => seleccionarResultadoBusqueda(resultado))
  })
}

const buscarDireccion = async (texto: string) => {
  if (!texto.trim()) {
    limpiarResultadosBusqueda()
    return
  }
  mostrarSpinnerBusqueda(true)
  try {
    const res = await httpClient.get('/api/admin/geocodificar', { params: { q: texto } })
    // Descarta respuestas fuera de orden: si el input ya cambió desde que
    // se disparó esta búsqueda, una más nueva ya está en curso (o ya se
    // renderizó) y no debe ser pisada por un resultado viejo que llegó tarde.
    if (!inputBusquedaEl || inputBusquedaEl.value !== texto) return
    renderizarResultadosBusqueda(res.data)
  } catch (error) {
    console.error('Error geocodificando dirección:', error)
  } finally {
    mostrarSpinnerBusqueda(false)
  }
}

const onInputBusqueda = (e: Event) => {
  const valor = (e.target as HTMLInputElement).value
  if (botonLimpiarBusquedaEl) botonLimpiarBusquedaEl.style.display = valor ? '' : 'none'
  if (temporizadorBusqueda) clearTimeout(temporizadorBusqueda)
  temporizadorBusqueda = setTimeout(() => buscarDireccion(valor), 500)
}

const armarModoVerificar = () => {
  if (!map) return
  if (modoVerificarArmado) {
    desarmarModoVerificar() // click de nuevo sobre el botón = cancelar el modo
    return
  }
  modoVerificarArmado = true
  map.getContainer().style.cursor = 'crosshair'
  map.once('click', colocarPin)
  botonVerificarEl?.classList.add('activo')
}

// ── Controles Leaflet (toggle de vista + leyenda) ───────────

const agregarControlToggleVista = (mapaInstancia: L.Map) => {
  const ToggleControl = L.Control.extend({
    onAdd() {
      const div = L.DomUtil.create('div', 'control-toggle-vista leaflet-bar')
      div.innerHTML = `
        <button type="button" data-vista="comuna" class="btn-vista activo">Ver por Comuna</button>
        <button type="button" data-vista="distrito" class="btn-vista">Ver por Distrito Postal</button>
        <button type="button" data-vista="ninguno" class="btn-vista">Ninguno</button>
        <label class="chk-zonas-protegidas"><input type="checkbox" /> Mostrar zonas protegidas</label>
      `
      L.DomEvent.disableClickPropagation(div)
      L.DomEvent.disableScrollPropagation(div)

      const botones = div.querySelectorAll<HTMLButtonElement>('.btn-vista')
      botones.forEach((btn) => {
        btn.addEventListener('click', () => {
          const vista = btn.dataset.vista as VistaMapa
          botones.forEach((b) => b.classList.remove('activo'))
          btn.classList.add('activo')
          cambiarVista(vista)
        })
      })

      const checkbox = div.querySelector<HTMLInputElement>('.chk-zonas-protegidas input')
      checkbox?.addEventListener('change', () => toggleZonasProtegidas())

      return div
    },
    onRemove() {},
  })

  new ToggleControl({ position: 'topright' }).addTo(mapaInstancia)
}

// Control separado (mismo patrón L.Control.extend, apilado debajo del
// anterior en 'topright'): el modo "verificar dirección" tiene su propio
// estado (armado/pin colocado) que no tiene relación con los toggles de
// vista, así que vive en su propio control en vez de mezclarse ahí.
const agregarControlVerificarDireccion = (mapaInstancia: L.Map) => {
  const VerificarControl = L.Control.extend({
    onAdd() {
      const div = L.DomUtil.create('div', 'control-verificar-direccion leaflet-bar')
      div.innerHTML = `
        <button type="button" class="btn-verificar-direccion">📍 Verificar dirección</button>
        <p class="ayuda-verificar">Clic en el mapa para colocar un pin de prueba (arrastrable).</p>
        <div class="buscador-direccion">
          <span class="icono-lupa">🔍</span>
          <input type="text" class="input-buscar-direccion" placeholder="Buscar dirección..." />
          <button type="button" class="btn-limpiar-busqueda" style="display:none" aria-label="Limpiar búsqueda">✕</button>
          <span class="spinner-busqueda" style="display:none"></span>
        </div>
        <ul class="resultados-buscar-direccion"></ul>
      `
      L.DomEvent.disableClickPropagation(div)
      L.DomEvent.disableScrollPropagation(div)

      botonVerificarEl = div.querySelector<HTMLButtonElement>('.btn-verificar-direccion')
      botonVerificarEl?.addEventListener('click', () => armarModoVerificar())

      inputBusquedaEl = div.querySelector<HTMLInputElement>('.input-buscar-direccion')
      listaResultadosEl = div.querySelector<HTMLUListElement>('.resultados-buscar-direccion')
      botonLimpiarBusquedaEl = div.querySelector<HTMLButtonElement>('.btn-limpiar-busqueda')
      spinnerBusquedaEl = div.querySelector<HTMLSpanElement>('.spinner-busqueda')
      inputBusquedaEl?.addEventListener('input', onInputBusqueda)
      botonLimpiarBusquedaEl?.addEventListener('click', limpiarBusquedaCompleta)

      return div
    },
    onRemove() {},
  })

  new VerificarControl({ position: 'topright' }).addTo(mapaInstancia)
}

const agregarControlLeyenda = (mapaInstancia: L.Map) => {
  const LeyendaControl = L.Control.extend({
    onAdd() {
      const div = L.DomUtil.create('div', 'control-leyenda-semaforo leaflet-bar')
      const niveles: { nivel: NivelSemaforo; etiqueta: string }[] = [
        { nivel: 'ALTO', etiqueta: etiquetaParaNivel('ALTO') },
        { nivel: 'MEDIO', etiqueta: etiquetaParaNivel('MEDIO') },
        { nivel: 'BAJO', etiqueta: etiquetaParaNivel('BAJO') },
        { nivel: 'SIN_VENTAS', etiqueta: etiquetaParaNivel('SIN_VENTAS') },
      ]
      div.innerHTML = `
        <strong>Nivel de ventas</strong>
        ${niveles.map(n => `
          <div class="fila-leyenda">
            <span class="swatch" style="background:${COLOR_NIVEL_SEMAFORO[n.nivel]}"></span>
            <span>${n.etiqueta}</span>
          </div>
        `).join('')}
      `
      return div
    },
    onRemove() {},
  })

  // 'topright': se apila debajo del control de toggle en la misma esquina
  // (Leaflet los agrupa automáticamente), así queda anclada arriba y nunca
  // se corta contra el borde inferior del mapa.
  new LeyendaControl({ position: 'topright' }).addTo(mapaInstancia)
}

onMounted(() => {
  // Ajuste pequeño para que los iconos por defecto funcionen bien en Vue3/Vite
  delete (L.Icon.Default.prototype as any)._getIconUrl
  L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png'
  })

  initMap()
})
</script>

<template>
  <div class="mapa-container">
    <div class="header">
      <h2>Logística y Cobertura</h2>
      <p>Mapa de almacenes (rojo) y volumen de ventas por comuna/distrito postal (polígonos), con toggle dentro del mapa.</p>
    </div>

    <div class="map-wrapper">
      <div ref="mapContainer" class="map-view"></div>
    </div>
  </div>
</template>

<style scoped>
.mapa-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.header {
  margin-bottom: 20px;
}

.header h2 {
  font-size: 1.5rem;
  color: #1f2937;
  margin-bottom: 5px;
}

.header p {
  color: #6b7280;
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

/* Controles Leaflet propios (no scoped porque Leaflet los inyecta fuera del árbol Vue) */
:global(.control-toggle-vista) {
  background: white;
  padding: 8px;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.8rem;
}

:global(.control-toggle-vista .btn-vista) {
  border: 1px solid #d1d5db;
  background: #f9fafb;
  border-radius: 4px;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 0.8rem;
}

:global(.control-toggle-vista .btn-vista.activo) {
  background: #374151;
  color: white;
  border-color: #374151;
}

:global(.control-toggle-vista .chk-zonas-protegidas) {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: #374151;
  cursor: pointer;
}

:global(.control-verificar-direccion) {
  background: white;
  padding: 8px;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  font-size: 0.8rem;
  max-width: 220px;
}

:global(.control-verificar-direccion .btn-verificar-direccion) {
  width: 100%;
  border: 1px solid #d1d5db;
  background: #f9fafb;
  border-radius: 4px;
  padding: 6px 8px;
  cursor: pointer;
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
}

:global(.control-verificar-direccion .btn-verificar-direccion.activo) {
  background: #1d4ed8;
  color: white;
  border-color: #1d4ed8;
}

:global(.control-verificar-direccion .ayuda-verificar) {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 0.72rem;
  line-height: 1.3;
}

:global(.control-verificar-direccion .buscador-direccion) {
  position: relative;
  margin-top: 8px;
}

:global(.control-verificar-direccion .icono-lupa) {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 0.75rem;
  pointer-events: none;
}

:global(.control-verificar-direccion .input-buscar-direccion) {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  padding: 6px 26px 6px 26px;
  font-size: 0.8rem;
}

:global(.control-verificar-direccion .btn-limpiar-busqueda) {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.72rem;
  color: #6b7280;
  padding: 2px;
  line-height: 1;
}

:global(.control-verificar-direccion .spinner-busqueda) {
  position: absolute;
  right: 8px;
  top: 50%;
  width: 11px;
  height: 11px;
  margin-top: -6px;
  border: 2px solid #d1d5db;
  border-top-color: #1d4ed8;
  border-radius: 50%;
  animation: girar-spinner-busqueda 0.6s linear infinite;
}

@keyframes girar-spinner-busqueda {
  to {
    transform: rotate(360deg);
  }
}

:global(.control-verificar-direccion .resultados-buscar-direccion) {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  max-height: 220px;
  overflow-y: auto;
}

:global(.control-verificar-direccion .resultado-item) {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 8px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}

:global(.control-verificar-direccion .resultado-item:hover) {
  background: #f3f4f6;
}

:global(.control-verificar-direccion .resultado-item:last-child) {
  border-bottom: none;
}

:global(.control-verificar-direccion .resultado-pin) {
  font-size: 0.78rem;
  line-height: 1.4;
}

:global(.control-verificar-direccion .resultado-texto) {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

:global(.control-verificar-direccion .resultado-primaria) {
  font-size: 0.76rem;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.3;
}

:global(.control-verificar-direccion .resultado-secundaria) {
  font-size: 0.68rem;
  color: #9ca3af;
  line-height: 1.3;
}

:global(.control-verificar-direccion .sin-resultados) {
  padding: 8px;
  text-align: center;
  color: #9ca3af;
  font-style: italic;
  font-size: 0.75rem;
}

/* Botón dentro del popup de una zona protegida — Leaflet inyecta el popup
   fuera del árbol Vue, mismo motivo que el resto de estilos :global aquí. */
:global(.btn-desmarcar-zona) {
  margin-top: 6px;
  border: 1px solid #5b21b6;
  background: #f5f3ff;
  color: #5b21b6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}

:global(.control-leyenda-semaforo) {
  background: white;
  padding: 10px 12px;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  font-size: 0.8rem;
  color: #1f2937;
  max-height: 260px;
  overflow-y: auto;
}

:global(.control-leyenda-semaforo .fila-leyenda) {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

:global(.control-leyenda-semaforo .swatch) {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  display: inline-block;
  border: 1px solid rgba(0, 0, 0, 0.2);
}
</style>
