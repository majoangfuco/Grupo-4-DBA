<script setup lang="ts">
import axios from 'axios'
import L from 'leaflet'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

interface ResultadoBusqueda {
  display_name: string
  lat: string
  lon: string
}

export interface UbicacionSeleccionada {
  latitud: number
  longitud: number
  direccion: string
  numero: string
  comuna: string
  textoCompleto: string
}

const props = defineProps<{
  latitudInicial?: number
  longitudInicial?: number
}>()

const emit = defineEmits<{
  seleccionar: [ubicacion: UbicacionSeleccionada]
  cancelar: []
}>()

const mapContainer = ref<HTMLDivElement | null>(null)
const consulta = ref('')
const buscando = ref(false)
const mensaje = ref('Haz clic en el mapa o busca una dirección.')
const resultados = ref<ResultadoBusqueda[]>([])
const seleccion = ref<UbicacionSeleccionada | null>(null)

let map: L.Map | null = null
let marcador: L.CircleMarker | null = null

const CENTRO_SANTIAGO: L.LatLngExpression = [-33.4489, -70.6693]

const obtenerDireccion = async (
  latitud: number,
  longitud: number,
): Promise<UbicacionSeleccionada> => {
  const response = await axios.get(
    'https://nominatim.openstreetmap.org/reverse',
    {
      params: {
        format: 'jsonv2',
        lat: latitud,
        lon: longitud,
        zoom: 18,
        addressdetails: 1,
        'accept-language': 'es',
      },
      timeout: 12000,
    },
  )

  const address = response.data?.address ?? {}

  const direccion =
    address.road ??
    address.pedestrian ??
    address.footway ??
    address.residential ??
    address.neighbourhood ??
    response.data?.display_name?.split(',')?.[0]?.trim() ??
    ''

  const comuna =
    address.municipality ??
    address.city_district ??
    address.suburb ??
    address.city ??
    address.town ??
    address.village ??
    address.county ??
    ''

  return {
    latitud,
    longitud,
    direccion,
    numero: address.house_number ?? '',
    comuna,
    textoCompleto: response.data?.display_name ?? '',
  }
}

const mostrarPunto = async (
  latitud: number,
  longitud: number,
  moverMapa = true,
) => {
  const punto = L.latLng(latitud, longitud)

  if (marcador) {
    marcador.setLatLng(punto)
  } else if (map) {
    marcador = L.circleMarker(punto, {
      radius: 9,
      weight: 3,
      fillOpacity: 0.75,
    }).addTo(map)
  }

  if (moverMapa) {
    map?.setView(punto, 17)
  }

  mensaje.value = 'Obteniendo datos de la dirección...'

  try {
    seleccion.value = await obtenerDireccion(latitud, longitud)
    mensaje.value =
      seleccion.value.textoCompleto ||
      'Ubicación seleccionada. Confirma para usarla.'
  } catch (error) {
    console.error('Error al obtener la dirección:', error)
    seleccion.value = {
      latitud,
      longitud,
      direccion: '',
      numero: '',
      comuna: '',
      textoCompleto: '',
    }
    mensaje.value =
      'Se seleccionaron las coordenadas, pero no se pudo obtener la dirección automáticamente.'
  }
}

const buscarDireccion = async () => {
  const texto = consulta.value.trim()
  if (!texto) {
    mensaje.value = 'Escribe una dirección para buscar.'
    return
  }

  buscando.value = true
  resultados.value = []
  mensaje.value = 'Buscando dirección...'

  try {
    const response = await axios.get<ResultadoBusqueda[]>(
      'https://nominatim.openstreetmap.org/search',
      {
        params: {
          format: 'jsonv2',
          q: texto,
          countrycodes: 'cl',
          limit: 5,
          addressdetails: 1,
          'accept-language': 'es',
        },
        timeout: 12000,
      },
    )

    resultados.value = response.data

    if (resultados.value.length === 0) {
      mensaje.value = 'No se encontraron resultados.'
    } else {
      mensaje.value = 'Selecciona uno de los resultados.'
    }
  } catch (error) {
    console.error('Error al buscar dirección:', error)
    mensaje.value = 'No fue posible buscar la dirección.'
  } finally {
    buscando.value = false
  }
}

const elegirResultado = async (resultado: ResultadoBusqueda) => {
  resultados.value = []
  consulta.value = resultado.display_name

  await mostrarPunto(
    Number(resultado.lat),
    Number(resultado.lon),
  )
}

const usarUbicacionActual = () => {
  if (!navigator.geolocation) {
    mensaje.value = 'Tu navegador no soporta geolocalización.'
    return
  }

  mensaje.value = 'Obteniendo ubicación actual...'

  navigator.geolocation.getCurrentPosition(
    (position) => {
      void mostrarPunto(
        Number(position.coords.latitude.toFixed(6)),
        Number(position.coords.longitude.toFixed(6)),
      )
    },
    (error) => {
      console.error('Error de geolocalización:', error)
      mensaje.value =
        'No se pudo obtener tu ubicación. Revisa los permisos del navegador.'
    },
    {
      enableHighAccuracy: true,
      timeout: 12000,
      maximumAge: 0,
    },
  )
}

const confirmar = () => {
  if (seleccion.value) {
    emit('seleccionar', seleccion.value)
  }
}

onMounted(async () => {
  await nextTick()

  if (!mapContainer.value) return

  map = L.map(mapContainer.value).setView(
    props.latitudInicial !== undefined &&
      props.longitudInicial !== undefined
      ? [props.latitudInicial, props.longitudInicial]
      : CENTRO_SANTIAGO,
    props.latitudInicial !== undefined ? 16 : 11,
  )

  L.tileLayer(
    'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    {
      maxZoom: 19,
      attribution:
        '&copy; OpenStreetMap contributors',
    },
  ).addTo(map)

  map.on('click', (event: L.LeafletMouseEvent) => {
    void mostrarPunto(
      Number(event.latlng.lat.toFixed(6)),
      Number(event.latlng.lng.toFixed(6)),
      false,
    )
  })

  if (
    props.latitudInicial !== undefined &&
    props.longitudInicial !== undefined
  ) {
    await mostrarPunto(
      props.latitudInicial,
      props.longitudInicial,
      false,
    )
  }

  window.setTimeout(() => {
    map?.invalidateSize()
  }, 100)
})

onBeforeUnmount(() => {
  map?.remove()
  map = null
  marcador = null
})
</script>

<template>
  <div class="selector-ubicacion">
    <div class="barra-busqueda">
      <input
        v-model="consulta"
        type="text"
        placeholder="Ejemplo: Av. Providencia 1234"
        @keyup.enter="buscarDireccion"
      />
      <button
        type="button"
        class="btn-mapa"
        :disabled="buscando"
        @click="buscarDireccion"
      >
        {{ buscando ? 'Buscando...' : 'Buscar' }}
      </button>
      <button
        type="button"
        class="btn-secundario"
        @click="usarUbicacionActual"
      >
        Mi ubicación
      </button>
    </div>

    <div v-if="resultados.length" class="resultados">
      <button
        v-for="resultado in resultados"
        :key="`${resultado.lat}-${resultado.lon}`"
        type="button"
        class="resultado"
        @click="elegirResultado(resultado)"
      >
        {{ resultado.display_name }}
      </button>
    </div>

    <p class="mensaje">{{ mensaje }}</p>

    <div ref="mapContainer" class="mapa"></div>

    <div v-if="seleccion" class="datos-seleccion">
      <span>
        Latitud:
        <strong>{{ seleccion.latitud }}</strong>
      </span>
      <span>
        Longitud:
        <strong>{{ seleccion.longitud }}</strong>
      </span>
      <span v-if="seleccion.direccion">
        Dirección:
        <strong>
          {{ seleccion.direccion }}
          {{ seleccion.numero }}
        </strong>
      </span>
      <span v-if="seleccion.comuna">
        Comuna:
        <strong>{{ seleccion.comuna }}</strong>
      </span>
    </div>

    <div class="acciones">
      <button
        type="button"
        class="btn-cancelar"
        @click="emit('cancelar')"
      >
        Cancelar
      </button>
      <button
        type="button"
        class="btn-confirmar"
        :disabled="!seleccion"
        @click="confirmar"
      >
        Usar esta ubicación
      </button>
    </div>
  </div>
</template>

<style scoped>
.selector-ubicacion {
  display: grid;
  gap: 12px;
}

.barra-busqueda {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
}

.barra-busqueda input {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
}

.btn-mapa,
.btn-confirmar {
  border: none;
  border-radius: 8px;
  padding: 9px 14px;
  background: #156895;
  color: #fff;
  cursor: pointer;
}

.btn-secundario,
.btn-cancelar {
  border: 1px solid #cfe6f7;
  border-radius: 8px;
  padding: 9px 14px;
  background: #eef7ff;
  color: #0f4c75;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.resultados {
  display: grid;
  max-height: 150px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.resultado {
  padding: 9px 10px;
  border: none;
  border-bottom: 1px solid #eef0f2;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.resultado:hover {
  background: #f5f9fc;
}

.mensaje {
  margin: 0;
  min-height: 20px;
  color: #555;
  font-size: 0.85rem;
}

.mapa {
  width: 100%;
  height: 390px;
  border: 1px solid #d8dde3;
  border-radius: 10px;
  overflow: hidden;
}

.datos-seleccion {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f6f8fa;
  font-size: 0.85rem;
}

.acciones {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 650px) {
  .barra-busqueda {
    grid-template-columns: 1fr;
  }

  .mapa {
    height: 320px;
  }
}
</style>
