<script setup lang="ts">
import { ref, onMounted } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import httpClient from '@/http-common'
import { colorParaNivel, etiquetaParaNivel, COLOR_NIVEL_SEMAFORO, type NivelSemaforo } from '@/constants/nivelSemaforo'

const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null

type VistaMapa = 'comuna' | 'distrito'
const vistaActual = ref<VistaMapa>('comuna')

let capaPoligonos: L.GeoJSON | null = null
let capaEntregas: L.GeoJSON | null = null
const mostrarClientes = ref(false)

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

  agregarControlToggleVista(map)
  agregarControlLeyenda(map)

  await cargarCapaPoligonos(vistaActual.value)
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
  } catch (error) {
    console.error('Error obteniendo capa de ventas por', vista, error)
  }
}

const cambiarVista = (vista: VistaMapa) => {
  if (vista === vistaActual.value) return
  vistaActual.value = vista
  cargarCapaPoligonos(vista)
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

// ── Clientes (pines azules) — capa opcional, desactivada por defecto ──

const fetchAndDrawEntregas = async () => {
  if (!map) return
  try {
    const res = await httpClient.get('/api/entregas/geojson')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    const iconCliente = L.icon({
      iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
      shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41]
    })

    capaEntregas = L.geoJSON(geojson, {
      pointToLayer: (feature, latlng) => {
        return L.marker(latlng, { icon: iconCliente })
      },
      onEachFeature: (feature, layer) => {
        const props = feature.properties
        layer.bindPopup(`
          <strong>Cliente (RUT: ${props.rut_empresa})</strong><br>
          Dirección: ${props.direccion}
        `)
      }
    }).addTo(map)
  } catch (error) {
    console.error('Error obteniendo entregas:', error)
  }
}

const toggleClientes = () => {
  mostrarClientes.value = !mostrarClientes.value
  if (!map) return
  if (mostrarClientes.value) {
    fetchAndDrawEntregas()
  } else if (capaEntregas) {
    map.removeLayer(capaEntregas)
    capaEntregas = null
  }
}

// ── Controles Leaflet (toggle de vista + leyenda) ───────────

const agregarControlToggleVista = (mapaInstancia: L.Map) => {
  const ToggleControl = L.Control.extend({
    onAdd() {
      const div = L.DomUtil.create('div', 'control-toggle-vista leaflet-bar')
      div.innerHTML = `
        <button type="button" data-vista="comuna" class="btn-vista activo">Ver por Comuna</button>
        <button type="button" data-vista="distrito" class="btn-vista">Ver por Distrito Postal</button>
        <label class="chk-clientes"><input type="checkbox" /> Mostrar clientes (pines)</label>
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

      const checkbox = div.querySelector<HTMLInputElement>('.chk-clientes input')
      checkbox?.addEventListener('change', () => toggleClientes())

      return div
    },
    onRemove() {},
  })

  new ToggleControl({ position: 'topright' }).addTo(mapaInstancia)
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

:global(.control-toggle-vista .chk-clientes) {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: #374151;
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
