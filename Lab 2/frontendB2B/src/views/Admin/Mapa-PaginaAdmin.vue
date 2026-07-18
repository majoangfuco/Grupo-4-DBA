<script setup lang="ts">
import { ref, onMounted } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import httpClient from '@/http-common'

const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null

const initMap = async () => {
  if (mapContainer.value) {
    map = L.map(mapContainer.value).setView([-33.4489, -70.6693], 10) // Centrado en Santiago por defecto

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map)

    await fetchAndDrawVentasPorComuna()
    await fetchAndDrawAlmacenes()
    await fetchAndDrawEntregas()
  }
}

const getColorForVentas = (volumen: number) => {
  return volumen > 10000000 ? '#800026' :
         volumen > 5000000  ? '#BD0026' :
         volumen > 1000000  ? '#E31A1C' :
         volumen > 500000   ? '#FC4E2A' :
         volumen > 100000   ? '#FD8D3C' :
         volumen > 10000    ? '#FEB24C' :
         volumen > 0        ? '#FED976' :
                              '#FFEDA0';
}

const fetchAndDrawVentasPorComuna = async () => {
  try {
    const res = await httpClient.get('/api/reportes/ventas-por-comuna/geojson')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    L.geoJSON(geojson, {
      style: (feature) => {
        const props = feature?.properties || {}
        return {
          fillColor: getColorForVentas(props.volumen_ventas || 0),
          weight: 2,
          opacity: 1,
          color: 'white',
          dashArray: '3',
          fillOpacity: 0.7
        }
      },
      onEachFeature: (feature, layer) => {
        const props = feature.properties
        layer.bindPopup(`
          <strong>Comuna: ${props.comuna}</strong><br>
          Órdenes Totales: ${props.cantidad_ordenes}<br>
          Volumen de Ventas: $${props.volumen_ventas}
        `)
      }
    }).addTo(map!)
  } catch (error) {
    console.error('Error obteniendo ventas por comuna:', error)
  }
}

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

const fetchAndDrawEntregas = async () => {
  try {
    const res = await httpClient.get('/api/entregas/geojson')
    const geojson = typeof res.data === 'string' ? JSON.parse(res.data) : res.data

    // Icono para clientes
    const iconCliente = L.icon({
      iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
      shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41]
    })

    L.geoJSON(geojson, {
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
    }).addTo(map!)
  } catch (error) {
    console.error('Error obteniendo entregas:', error)
  }
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
      <p>Mapa de almacenes (rojo), direcciones de entrega de clientes (azul), y volumen proyectado de ventas por comuna (polígonos de calor).</p>
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
</style>
