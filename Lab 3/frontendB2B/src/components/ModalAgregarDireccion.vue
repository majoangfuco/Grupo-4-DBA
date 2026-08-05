<script setup lang="ts">
import { ref, computed } from 'vue'
import { Map, MapPin, X } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import SelectorUbicacion, { type UbicacionSeleccionada } from '@/components/SelectorUbicacion.vue'
import {
  crearEntrega,
  obtenerDireccionDesdeCoordenadas,
  type InformacionEntregaEntidad,
} from '@/services/entregaServicio'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits(['close', 'saved', 'notificar'])
const authStore = useAuthStore()

const showAddressErrors = ref(false)
const showMapModal = ref(false)

const COMUNAS_CHILE = [
  'Algarrobo', 'Alhué', 'Alto Biobío', 'Alto del Carmen', 'Alto Hospicio',
  'Ancud', 'Andacollo', 'Angol', 'Antofagasta', 'Antuco', 'Antártica',
  'Arauco', 'Arica', 'Aysén', 'Buin', 'Bulnes', 'Cabildo', 'Cabo de Hornos',
  'Cabrero', 'Calama', 'Calbuco', 'Caldera', 'Calera de Tango', 'Calle Larga',
  'Camarones', 'Camiña', 'Canela', 'Carahue', 'Cartagena', 'Casablanca',
  'Castro', 'Catemu', 'Cauquenes', 'Cañete', 'Cerrillos', 'Cerro Navia',
  'Chaitén', 'Chanco', 'Chañaral', 'Chiguayante', 'Chile Chico', 'Chillán',
  'Chillán Viejo', 'Chimbarongo', 'Cholchol', 'Chonchi', 'Chépica', 'Cisnes',
  'Cobquecura', 'Cochamó', 'Cochrane', 'Codegua', 'Coelemu', 'Coihueco',
  'Coinco', 'Colbún', 'Colchane', 'Colina', 'Collipulli', 'Coltauco',
  'Combarbalá', 'Concepción', 'Conchalí', 'Concón', 'Constitución',
  'Contulmo', 'Copiapó', 'Coquimbo', 'Coronel', 'Corral', 'Coyhaique',
  'Cunco', 'Curacautín', 'Curacaví', 'Curaco de Vélez', 'Curanilahue',
  'Curarrehue', 'Curepto', 'Curicó', 'Dalcahue', 'Diego de Almagro',
  'Doñihue', 'El Bosque', 'El Carmen', 'El Monte', 'El Quisco', 'El Tabo',
  'Empedrado', 'Ercilla', 'Estación Central', 'Florida', 'Freire',
  'Freirina', 'Fresia', 'Frutillar', 'Futaleufú', 'Futrono', 'Galvarino',
  'General Lagos', 'Gorbea', 'Graneros', 'Guaitecas', 'Hijuelas',
  'Hualaihué', 'Hualañé', 'Hualpén', 'Hualqui', 'Huara', 'Huasco',
  'Huechuraba', 'Illapel', 'Independencia', 'Iquique', 'Isla de Maipo',
  'Isla de Pascua', 'Juan Fernández', 'La Calera', 'La Cisterna', 'La Cruz',
  'La Estrella', 'La Florida', 'La Granja', 'La Higuera', 'La Ligua',
  'La Pintana', 'La Reina', 'La Serena', 'La Unión', 'Lago Ranco',
  'Lago Verde', 'Laguna Blanca', 'Laja', 'Lampa', 'Lanco', 'Las Cabras',
  'Las Condes', 'Lautaro', 'Lebu', 'Licantén', 'Limache', 'Linares',
  'Litueche', 'Llaillay', 'Llanquihue', 'Lo Barnechea', 'Lo Espejo',
  'Lo Prado', 'Lolol', 'Loncoche', 'Longaví', 'Lonquimay', 'Los Alamos',
  'Los Andes', 'Los Angeles', 'Los Lagos', 'Los Muermos', 'Los Sauces',
  'Los Vilos', 'Lota', 'Lumaco', 'Machalí', 'Macul', 'Maipú', 'Malloa',
  'Marchihue', 'Mariquina', 'María Elena', 'María Pinto', 'Maule',
  'Maullín', 'Mejillones', 'Melipeuco', 'Melipilla', 'Molina',
  'Monte Patria', 'Mostazal', 'Mulchén', 'Máfil', 'Nacimiento', 'Nancagua',
  'Natales', 'Navidad', 'Negrete', 'Ninhue', 'Nogales', 'Nueva Imperial',
  "O'Higgins", 'Olivar', 'Ollagüe', 'Olmué', 'Osorno', 'Ovalle',
  'Padre Hurtado', 'Padre Las Casas', 'Paihuano', 'Paillaco', 'Paine',
  'Palena', 'Palmilla', 'Panguipulli', 'Panquehue', 'Papudo', 'Paredones',
  'Parral', 'Pedro Aguirre Cerda', 'Pelarco', 'Pelluhue', 'Pemuco',
  'Pencahue', 'Penco', 'Peralillo', 'Perquenco', 'Petorca', 'Peumo',
  'Peñaflor', 'Peñalolén', 'Pica', 'Pichidegua', 'Pichilemu', 'Pinto',
  'Pirque', 'Pitrufquén', 'Placilla', 'Portezuelo', 'Porvenir',
  'Pozo Almonte', 'Primavera', 'Providencia', 'Puchuncaví', 'Pucón',
  'Pudahuel', 'Puente Alto', 'Puerto Montt', 'Puerto Octay', 'Puerto Varas',
  'Pumanque', 'Punitaqui', 'Punta Arenas', 'Puqueldón', 'Purranque',
  'Purén', 'Putaendo', 'Putre', 'Puyehue', 'Queilén', 'Quellón', 'Quemchi',
  'Quilaco', 'Quilicura', 'Quilleco', 'Quillota', 'Quillón', 'Quilpué',
  'Quinchao', 'Quinta de Tilcoco', 'Quinta Normal', 'Quintero', 'Quirihue',
  'Rancagua', 'Rauco', 'Recoleta', 'Renaico', 'Renca', 'Rengo', 'Requínoa',
  'Retiro', 'Rinconada', 'Romeral', 'Ránquil', 'Río Bueno', 'Río Claro',
  'Río Hurtado', 'Río Ibáñez', 'Río Negro', 'Río Verde', 'Saavedra',
  'Sagrada Familia', 'Salamanca', 'San Antonio', 'San Bernardo',
  'San Carlos', 'San Clemente', 'San Esteban', 'San Fabián', 'San Felipe',
  'San Fernando', 'San Gregorio', 'San Ignacio', 'San Javier',
  'San Joaquín', 'San José de Maipo', 'San Juan de la Costa', 'San Miguel',
  'San Nicolás', 'San Pablo', 'San Pedro', 'San Pedro de Atacama',
  'San Pedro de la Paz', 'San Rafael', 'San Ramón', 'San Rosendo',
  'San Vicente', 'Santa Bárbara', 'Santa Cruz', 'Santa Juana', 'Santa María',
  'Santiago', 'Santo Domingo', 'Sierra Gorda', 'Talagante', 'Talca',
  'Talcahuano', 'Taltal', 'Temuco', 'Teno', 'Teodoro Schmidt',
  'Tierra Amarilla', 'Tiltil', 'Timaukel', 'Tirúa', 'Tocopilla', 'Toltén',
  'Tomé', 'Torres del Paine', 'Tortel', 'Traiguén', 'Treguaco', 'Tucapel',
  'Valdivia', 'Vallenar', 'Valparaíso', 'Vichuquén', 'Victoria', 'Vicuña',
  'Vilcún', 'Villa Alegre', 'Villa Alemana', 'Villarrica', 'Vitacura',
  'Viña del Mar', 'Yerbas Buenas', 'Yumbel', 'Yungay', 'Zapallar',
  'Ñiquén', 'Ñuñoa',
] as const

const newAddress = ref<Partial<InformacionEntregaEntidad>>({
  direccion: '',
  numero: '',
  rut_Recibe_Entrega: '',
  comuna: '',
  latitud: undefined,
  longitud: undefined,
})

const mostrarSugerenciasComuna = ref(false)

const normalizarComuna = (valor: string): string =>
  valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()

const comunasFiltradas = computed(() => {
  const texto = normalizarComuna(newAddress.value.comuna ?? '')
  if (!texto) return COMUNAS_CHILE
  return COMUNAS_CHILE.filter((comuna) => normalizarComuna(comuna).includes(texto))
})

const seleccionarComuna = (comuna: string) => {
  newAddress.value.comuna = comuna
  mostrarSugerenciasComuna.value = false
}

const ocultarSugerenciasComuna = () => {
  window.setTimeout(() => { mostrarSugerenciasComuna.value = false }, 150)
}

const obteniendoUbicacion = ref(false)

const usarUbicacionActual = () => {
  if (!navigator.geolocation) {
    emit('notificar', 'Tu navegador no soporta geolocalización', 'error')
    return
  }
  obteniendoUbicacion.value = true
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      const latitud = Number(position.coords.latitude.toFixed(6))
      const longitud = Number(position.coords.longitude.toFixed(6))
      newAddress.value.latitud = latitud
      newAddress.value.longitud = longitud
      try {
        const direccionDetectada = await obtenerDireccionDesdeCoordenadas(latitud, longitud)
        newAddress.value.direccion = direccionDetectada.direccion
        newAddress.value.numero = direccionDetectada.numero
        newAddress.value.comuna = direccionDetectada.comuna.trim()
        if (!newAddress.value.direccion) {
          emit('notificar', 'Se detectaron las coordenadas, pero no fue posible identificar la calle.', 'error')
        } else if (!newAddress.value.numero) {
          emit('notificar', 'Ubicación detectada. Completa el número de la dirección.', 'error')
        } else if (!newAddress.value.comuna) {
          emit('notificar', 'Ubicación detectada. Selecciona la comuna correspondiente.', 'error')
        } else {
          emit('notificar', 'Dirección actual completada correctamente', 'ok')
        }
      } catch (err) {
        console.error('Error de geocodificación inversa:', err)
        emit('notificar', 'Se detectaron las coordenadas, pero no fue posible completar la dirección automáticamente.', 'error')
      } finally {
        obteniendoUbicacion.value = false
      }
    },
    (err) => {
      console.error('Error de geolocalización:', err)
      obteniendoUbicacion.value = false
      emit('notificar', 'No se pudo obtener tu ubicación. Revisa los permisos del navegador.', 'error')
    },
    { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
  )
}

const abrirSelectorMapa = () => { showMapModal.value = true }

const aplicarUbicacionSeleccionada = (ubicacion: UbicacionSeleccionada) => {
  newAddress.value.latitud = ubicacion.latitud
  newAddress.value.longitud = ubicacion.longitud
  if (ubicacion.direccion) newAddress.value.direccion = ubicacion.direccion
  if (ubicacion.numero) newAddress.value.numero = ubicacion.numero
  const comunaDetectada = ubicacion.comuna.trim()
  if (comunaDetectada) newAddress.value.comuna = comunaDetectada
  showMapModal.value = false

  if (!ubicacion.numero) {
    emit('notificar', 'Ubicación seleccionada. Completa el número de la dirección.', 'error')
  } else if (!comunaDetectada) {
    emit('notificar', 'Ubicación seleccionada. Selecciona la comuna correspondiente.', 'error')
  } else {
    emit('notificar', 'Ubicación seleccionada correctamente', 'ok')
  }
}

const extraerMensajeError = (err: unknown, mensajePorDefecto: string): string => {
  const axiosErr = err as { response?: { status?: number; data?: unknown } }
  const data = axiosErr.response?.data
  if (typeof data === 'string' && data.trim().length > 0) {
    return data
  }
  if (data && typeof data === 'object' && 'error' in data) {
    return String((data as Record<string, unknown>).error)
  }
  return mensajePorDefecto
}

const crearNuevaEntrega = async () => {
  showAddressErrors.value = true
  try {
    if (!authStore.userId) throw new Error('Usuario no validado')
    if (
      !newAddress.value.direccion?.trim() ||
      !newAddress.value.numero?.trim() ||
      !newAddress.value.rut_Recibe_Entrega?.trim() ||
      !newAddress.value.comuna?.trim()
    ) {
      emit('notificar', 'Completa dirección, número, RUT de quien recibe y comuna', 'error')
      return
    }
    if (
      newAddress.value.latitud === undefined ||
      newAddress.value.longitud === undefined ||
      !Number.isFinite(newAddress.value.latitud) ||
      !Number.isFinite(newAddress.value.longitud)
    ) {
      emit('notificar', 'Ingresa coordenadas válidas', 'error')
      return
    }
    if (newAddress.value.latitud < -90 || newAddress.value.latitud > 90) {
      emit('notificar', 'La latitud debe estar entre -90 y 90', 'error')
      return
    }
    if (newAddress.value.longitud < -180 || newAddress.value.longitud > 180) {
      emit('notificar', 'La longitud debe estar entre -180 y 180', 'error')
      return
    }
    const payload: Partial<InformacionEntregaEntidad> = {
      ...newAddress.value,
      usuarioId: Number(authStore.userId),
      activa: true,
      estado_Entrega: 'PENDIENTE'
    }
    await crearEntrega(payload)

    showAddressErrors.value = false
    newAddress.value = {
      direccion: '', numero: '', rut_Recibe_Entrega: '', comuna: '', latitud: undefined, longitud: undefined
    }
    emit('saved')
    emit('close')
  } catch (err: unknown) {
    console.error('Error creando entrega:', err)
    emit('notificar', extraerMensajeError(err, 'No se pudo guardar la dirección'), 'error')
  }
}

const closeModal = () => {
  showAddressErrors.value = false
  emit('close')
}
</script>

<template>
  <div v-if="show" class="modal-overlay" @click.self="closeModal">
    <div class="modal-box" role="dialog" aria-modal="true">
      <div class="modal-header">
        <h3 class="modal-title">Agregar nueva dirección</h3>
        <button class="modal-close" @click="closeModal" aria-label="Cerrar modal">
          <X :size="18" />
        </button>
      </div>
      <div class="modal-body">
        <div class="form-grid">
          <label>Dirección</label>
          <input type="text" v-model.trim="newAddress.direccion" placeholder="Ejemplo: Av. Providencia" />

          <label>Número</label>
          <input type="text" v-model.trim="newAddress.numero" placeholder="Ejemplo: 1234" />

          <label>RUT quien recibe</label>
          <input type="text" v-model.trim="newAddress.rut_Recibe_Entrega" placeholder="Ejemplo: 12.345.678-9" />

          <label>Comuna</label>
          <div class="comuna-autocompletar">
            <input type="text" v-model.trim="newAddress.comuna" placeholder="Escribe o selecciona una comuna" autocomplete="off" @focus="mostrarSugerenciasComuna = true" @input="mostrarSugerenciasComuna = true" @blur="ocultarSugerenciasComuna" />
            <div v-if="mostrarSugerenciasComuna" class="comuna-sugerencias">
              <button v-for="comuna in comunasFiltradas" :key="comuna" type="button" class="comuna-opcion" @mousedown.prevent="seleccionarComuna(comuna)">
                {{ comuna }}
              </button>
              <div v-if="comunasFiltradas.length === 0" class="comuna-sin-resultados">
                No se encontraron comunas
              </div>
            </div>
          </div>
        </div>

        <div class="form-grid" style="margin-top: 10px;">
          <label>Latitud</label>
          <div class="input-con-error">
            <input type="number" step="0.000001" v-model.number="newAddress.latitud" placeholder="Ej: -33.4489" :class="{ 'input-rojo': showAddressErrors && !Number.isFinite(newAddress.latitud) }" />
            <span v-if="showAddressErrors && !Number.isFinite(newAddress.latitud)" class="texto-error-inline">Campo obligatorio</span>
          </div>

          <label>Longitud</label>
          <div class="input-con-error">
            <input type="number" step="0.000001" v-model.number="newAddress.longitud" placeholder="Ej: -70.6693" :class="{ 'input-rojo': showAddressErrors && !Number.isFinite(newAddress.longitud) }" />
            <span v-if="showAddressErrors && !Number.isFinite(newAddress.longitud)" class="texto-error-inline">Campo obligatorio</span>
          </div>
        </div>

        <div class="coordenadas-acciones">
          <button type="button" class="btn-ubicacion" :disabled="obteniendoUbicacion" @click="usarUbicacionActual">
            <MapPin :size="16" />
            <span>{{ obteniendoUbicacion ? 'Ubicando y completando...' : 'Usar mi ubicación actual' }}</span>
          </button>
          <button type="button" class="btn-link" @click="abrirSelectorMapa">
            <Map :size="16" />
            <span>Seleccionar en mapa</span>
          </button>
        </div>

        <p class="ayuda-coordenadas">
          La ubicación actual completa automáticamente la calle, el número
          cuando está disponible, la comuna y las coordenadas. El RUT de quien
          recibe debe ingresarse manualmente.
        </p>
      </div>
      <div class="modal-actions">
        <button class="btn-link" @click="closeModal">Cancelar</button>
        <button class="btn-solid" @click="crearNuevaEntrega">Guardar dirección</button>
      </div>
    </div>
  </div>

  <div v-if="showMapModal" class="modal-overlay modal-mapa-overlay" @click.self="showMapModal = false">
    <div class="modal-box modal-mapa" role="dialog" aria-modal="true">
      <div class="modal-header">
        <h3 class="modal-title">Seleccionar ubicación</h3>
        <button class="modal-close" @click="showMapModal = false" aria-label="Cerrar modal">
          <X :size="18" />
        </button>
      </div>
      <div class="modal-body">
        <SelectorUbicacion :latitud-inicial="newAddress.latitud" :longitud-inicial="newAddress.longitud" @seleccionar="aplicarUbicacionSeleccionada" @cancelar="showMapModal = false" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; z-index: 300; }
.modal-box { background: #fff; border-radius: 14px; padding: 18px 20px; width: 520px; max-width: 92vw; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }
.modal-header { display: flex; align-items: center; gap: 10px; position: relative; }
.modal-title { font-size: 1rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.modal-close { position: absolute; right: 0; top: -4px; background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #777; }
.modal-body { margin-top: 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 16px; }
.btn-link { background: none; border: none; color: #156895; cursor: pointer; }
.btn-solid { background: #156895; color: #fff; border: none; border-radius: 22px; padding: 8px 16px; cursor: pointer; }
.btn-solid:hover { background: #1b76a5; }
.ayuda-coordenadas { font-size: 0.78rem; color: #888; margin-top: 8px; }

.coordenadas-acciones { display: flex; align-items: center; gap: 15px; margin-top: 12px; }
.btn-ubicacion { background: #eef7ff; border: 1px solid #cfe6f7; color: #0f4c75; border-radius: 8px; padding: 8px 12px; font-size: 0.85rem; cursor: pointer; }
.btn-ubicacion:hover { background: #dcecf9; }
.btn-ubicacion:disabled { opacity: 0.6; cursor: not-allowed; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.form-grid label { font-size: 0.85rem; color: #444; }

.input-con-error { display: flex; flex-direction: column; gap: 4px; }
.form-grid input, .form-grid select { width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 8px; box-sizing: border-box; }

.comuna-autocompletar { position: relative; width: 100%; }
.comuna-sugerencias { position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 360; max-height: 180px; overflow-y: auto; border: 1px solid #d1d5db; border-radius: 8px; background: #fff; box-shadow: 0 8px 20px rgba(0,0,0,0.15); }
.comuna-opcion { display: block; width: 100%; padding: 9px 11px; border: none; border-bottom: 1px solid #f0f0f0; background: #fff; color: #222; text-align: left; cursor: pointer; }
.comuna-opcion:hover { background: #eef7ff; }
.comuna-sin-resultados { padding: 10px 11px; color: #777; font-size: 0.8rem; }

.input-rojo { border-color: #b00020 !important; background-color: #fff9fa; }
.texto-error-inline { color: #b00020; font-size: 0.75rem; font-weight: 600; padding-left: 2px; }

.modal-box .modal-title { font-size: 1.05rem; }
.modal-mapa-overlay { z-index: 320; }
.modal-mapa { width: 650px; max-width: 94vw; max-height: 92vh; overflow-y: auto; }

@media (max-width: 600px) {
  .form-grid { grid-template-columns: 1fr; }
  .coordenadas-acciones { flex-wrap: wrap; }
}
</style>
