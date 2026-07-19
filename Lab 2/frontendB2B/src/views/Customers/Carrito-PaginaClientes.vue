<script setup lang="ts">
// =====================================================
// Carrito-PaginaClientes.vue
// Muestra el carrito activo/abandonado del cliente.
// =====================================================
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
import SelectorUbicacion, {
  type UbicacionSeleccionada,
} from '@/components/SelectorUbicacion.vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { carritoServicio, type CarritoEntidad } from '@/services/carritoServicio'
import { carritoProductoServicio, type CarritoProductoEntidad } from '@/services/carritoProductoServicio'
import {
  obtenerEntregasPorUsuario,
  crearEntrega,
  obtenerDireccionDesdeCoordenadas,
  type InformacionEntregaEntidad,
} from '@/services/entregaServicio'
import { obtenerDatosPagoPorUsuario, crearDatosPago, type DatosDePagoEntidad } from '@/services/datosPagoServicio'
import { ordenesServicio } from '@/services/ordenesServicio'

type SelectOptionId = number | 'new' | null

const router = useRouter()
const authStore = useAuthStore()

const carrito = ref<CarritoEntidad | null>(null)
const items = ref<CarritoProductoEntidad[]>([])
const subtotal = ref<number>(0)
const entregas = ref<InformacionEntregaEntidad[]>([])
const datosPago = ref<DatosDePagoEntidad[]>([])
const selectedEntregaId = ref<SelectOptionId>(null)
const selectedPagoId = ref<SelectOptionId>(null)
const cargando = ref(false)
const error = ref<string | null>(null)
const toastMensaje = ref<string | null>(null)
const toastTipo = ref<'ok' | 'error'>('ok')
let toastTimer: number | null = null
const modalAbierto = ref(false)
const modalAccion = ref<'agregar' | 'eliminar'>('eliminar')
const modalItem = ref<CarritoProductoEntidad | null>(null)

// Mostrar al cliente la confirmacion del pedido y logística asignada.
const almacenAsignado = ref<string | null>(null)

// New: modals for crear entrega / datos de pago
const showAddressModal = ref(false)
const showMapModal = ref(false)
const showPaymentModal = ref(false)
const showAddressErrors = ref(false)

const COMUNAS_CHILE = [
  'Algarrobo',
  'Alhué',
  'Alto Biobío',
  'Alto del Carmen',
  'Alto Hospicio',
  'Ancud',
  'Andacollo',
  'Angol',
  'Antofagasta',
  'Antuco',
  'Antártica',
  'Arauco',
  'Arica',
  'Aysén',
  'Buin',
  'Bulnes',
  'Cabildo',
  'Cabo de Hornos',
  'Cabrero',
  'Calama',
  'Calbuco',
  'Caldera',
  'Calera de Tango',
  'Calle Larga',
  'Camarones',
  'Camiña',
  'Canela',
  'Carahue',
  'Cartagena',
  'Casablanca',
  'Castro',
  'Catemu',
  'Cauquenes',
  'Cañete',
  'Cerrillos',
  'Cerro Navia',
  'Chaitén',
  'Chanco',
  'Chañaral',
  'Chiguayante',
  'Chile Chico',
  'Chillán',
  'Chillán Viejo',
  'Chimbarongo',
  'Cholchol',
  'Chonchi',
  'Chépica',
  'Cisnes',
  'Cobquecura',
  'Cochamó',
  'Cochrane',
  'Codegua',
  'Coelemu',
  'Coihueco',
  'Coinco',
  'Colbún',
  'Colchane',
  'Colina',
  'Collipulli',
  'Coltauco',
  'Combarbalá',
  'Concepción',
  'Conchalí',
  'Concón',
  'Constitución',
  'Contulmo',
  'Copiapó',
  'Coquimbo',
  'Coronel',
  'Corral',
  'Coyhaique',
  'Cunco',
  'Curacautín',
  'Curacaví',
  'Curaco de Vélez',
  'Curanilahue',
  'Curarrehue',
  'Curepto',
  'Curicó',
  'Dalcahue',
  'Diego de Almagro',
  'Doñihue',
  'El Bosque',
  'El Carmen',
  'El Monte',
  'El Quisco',
  'El Tabo',
  'Empedrado',
  'Ercilla',
  'Estación Central',
  'Florida',
  'Freire',
  'Freirina',
  'Fresia',
  'Frutillar',
  'Futaleufú',
  'Futrono',
  'Galvarino',
  'General Lagos',
  'Gorbea',
  'Graneros',
  'Guaitecas',
  'Hijuelas',
  'Hualaihué',
  'Hualañé',
  'Hualpén',
  'Hualqui',
  'Huara',
  'Huasco',
  'Huechuraba',
  'Illapel',
  'Independencia',
  'Iquique',
  'Isla de Maipo',
  'Isla de Pascua',
  'Juan Fernández',
  'La Calera',
  'La Cisterna',
  'La Cruz',
  'La Estrella',
  'La Florida',
  'La Granja',
  'La Higuera',
  'La Ligua',
  'La Pintana',
  'La Reina',
  'La Serena',
  'La Unión',
  'Lago Ranco',
  'Lago Verde',
  'Laguna Blanca',
  'Laja',
  'Lampa',
  'Lanco',
  'Las Cabras',
  'Las Condes',
  'Lautaro',
  'Lebu',
  'Licantén',
  'Limache',
  'Linares',
  'Litueche',
  'Llaillay',
  'Llanquihue',
  'Lo Barnechea',
  'Lo Espejo',
  'Lo Prado',
  'Lolol',
  'Loncoche',
  'Longaví',
  'Lonquimay',
  'Los Alamos',
  'Los Andes',
  'Los Angeles',
  'Los Lagos',
  'Los Muermos',
  'Los Sauces',
  'Los Vilos',
  'Lota',
  'Lumaco',
  'Machalí',
  'Macul',
  'Maipú',
  'Malloa',
  'Marchihue',
  'Mariquina',
  'María Elena',
  'María Pinto',
  'Maule',
  'Maullín',
  'Mejillones',
  'Melipeuco',
  'Melipilla',
  'Molina',
  'Monte Patria',
  'Mostazal',
  'Mulchén',
  'Máfil',
  'Nacimiento',
  'Nancagua',
  'Natales',
  'Navidad',
  'Negrete',
  'Ninhue',
  'Nogales',
  'Nueva Imperial',
  "O'Higgins",
  'Olivar',
  'Ollagüe',
  'Olmué',
  'Osorno',
  'Ovalle',
  'Padre Hurtado',
  'Padre Las Casas',
  'Paihuano',
  'Paillaco',
  'Paine',
  'Palena',
  'Palmilla',
  'Panguipulli',
  'Panquehue',
  'Papudo',
  'Paredones',
  'Parral',
  'Pedro Aguirre Cerda',
  'Pelarco',
  'Pelluhue',
  'Pemuco',
  'Pencahue',
  'Penco',
  'Peralillo',
  'Perquenco',
  'Petorca',
  'Peumo',
  'Peñaflor',
  'Peñalolén',
  'Pica',
  'Pichidegua',
  'Pichilemu',
  'Pinto',
  'Pirque',
  'Pitrufquén',
  'Placilla',
  'Portezuelo',
  'Porvenir',
  'Pozo Almonte',
  'Primavera',
  'Providencia',
  'Puchuncaví',
  'Pucón',
  'Pudahuel',
  'Puente Alto',
  'Puerto Montt',
  'Puerto Octay',
  'Puerto Varas',
  'Pumanque',
  'Punitaqui',
  'Punta Arenas',
  'Puqueldón',
  'Purranque',
  'Purén',
  'Putaendo',
  'Putre',
  'Puyehue',
  'Queilén',
  'Quellón',
  'Quemchi',
  'Quilaco',
  'Quilicura',
  'Quilleco',
  'Quillota',
  'Quillón',
  'Quilpué',
  'Quinchao',
  'Quinta de Tilcoco',
  'Quinta Normal',
  'Quintero',
  'Quirihue',
  'Rancagua',
  'Rauco',
  'Recoleta',
  'Renaico',
  'Renca',
  'Rengo',
  'Requínoa',
  'Retiro',
  'Rinconada',
  'Romeral',
  'Ránquil',
  'Río Bueno',
  'Río Claro',
  'Río Hurtado',
  'Río Ibáñez',
  'Río Negro',
  'Río Verde',
  'Saavedra',
  'Sagrada Familia',
  'Salamanca',
  'San Antonio',
  'San Bernardo',
  'San Carlos',
  'San Clemente',
  'San Esteban',
  'San Fabián',
  'San Felipe',
  'San Fernando',
  'San Gregorio',
  'San Ignacio',
  'San Javier',
  'San Joaquín',
  'San José de Maipo',
  'San Juan de la Costa',
  'San Miguel',
  'San Nicolás',
  'San Pablo',
  'San Pedro',
  'San Pedro de Atacama',
  'San Pedro de la Paz',
  'San Rafael',
  'San Ramón',
  'San Rosendo',
  'San Vicente',
  'Santa Bárbara',
  'Santa Cruz',
  'Santa Juana',
  'Santa María',
  'Santiago',
  'Santo Domingo',
  'Sierra Gorda',
  'Talagante',
  'Talca',
  'Talcahuano',
  'Taltal',
  'Temuco',
  'Teno',
  'Teodoro Schmidt',
  'Tierra Amarilla',
  'Tiltil',
  'Timaukel',
  'Tirúa',
  'Tocopilla',
  'Toltén',
  'Tomé',
  'Torres del Paine',
  'Tortel',
  'Traiguén',
  'Treguaco',
  'Tucapel',
  'Valdivia',
  'Vallenar',
  'Valparaíso',
  'Vichuquén',
  'Victoria',
  'Vicuña',
  'Vilcún',
  'Villa Alegre',
  'Villa Alemana',
  'Villarrica',
  'Vitacura',
  'Viña del Mar',
  'Yerbas Buenas',
  'Yumbel',
  'Yungay',
  'Zapallar',
  'Ñiquén',
  'Ñuñoa',
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
  valor
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()

const comunasFiltradas = computed(() => {
  const texto = normalizarComuna(
    newAddress.value.comuna ?? '',
  )

  if (!texto) {
    return COMUNAS_CHILE
  }

  return COMUNAS_CHILE.filter((comuna) =>
    normalizarComuna(comuna).includes(texto),
  )
})

const seleccionarComuna = (comuna: string) => {
  newAddress.value.comuna = comuna
  mostrarSugerenciasComuna.value = false
}

const ocultarSugerenciasComuna = () => {
  window.setTimeout(() => {
    mostrarSugerenciasComuna.value = false
  }, 150)
}
const newPayment = ref<Partial<DatosDePagoEntidad>>({ metodo_Pago: '', numero_Tarjeta: '', fecha_Expiracion: '' })
const newPaymentMonth = ref<string>('')
const newPaymentYear = ref<string>('')

// Autocompletado de coordenadas y dirección mediante geolocalización.
const obteniendoUbicacion = ref(false)

const usarUbicacionActual = () => {
  if (!navigator.geolocation) {
    notificar('Tu navegador no soporta geolocalización', 'error')
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
        const direccionDetectada =
          await obtenerDireccionDesdeCoordenadas(latitud, longitud)

        newAddress.value.direccion = direccionDetectada.direccion
        newAddress.value.numero = direccionDetectada.numero
        newAddress.value.comuna =
          direccionDetectada.comuna.trim()

        if (!newAddress.value.direccion) {
          notificar(
            'Se detectaron las coordenadas, pero no fue posible identificar la calle.',
            'error',
          )
        } else if (!newAddress.value.numero) {
          notificar(
            'Ubicación detectada. Completa el número de la dirección.',
            'error',
          )
        } else if (!newAddress.value.comuna) {
          notificar(
            'Ubicación detectada. Selecciona la comuna correspondiente.',
            'error',
          )
        } else {
          notificar('Dirección actual completada correctamente', 'ok')
        }
      } catch (err) {
        console.error('Error de geocodificación inversa:', err)
        notificar(
          'Se detectaron las coordenadas, pero no fue posible completar la dirección automáticamente.',
          'error',
        )
      } finally {
        obteniendoUbicacion.value = false
      }
    },
    (err) => {
      console.error('Error de geolocalización:', err)
      obteniendoUbicacion.value = false
      notificar(
        'No se pudo obtener tu ubicación. Revisa los permisos del navegador.',
        'error',
      )
    },
    {
      enableHighAccuracy: true,
      timeout: 12000,
      maximumAge: 0,
    },
  )
}

const abrirSelectorMapa = () => {
  showMapModal.value = true
}

const aplicarUbicacionSeleccionada = (
  ubicacion: UbicacionSeleccionada,
) => {
  newAddress.value.latitud = ubicacion.latitud
  newAddress.value.longitud = ubicacion.longitud

  if (ubicacion.direccion) {
    newAddress.value.direccion = ubicacion.direccion
  }

  if (ubicacion.numero) {
    newAddress.value.numero = ubicacion.numero
  }

  const comunaDetectada = ubicacion.comuna.trim()

  if (comunaDetectada) {
    newAddress.value.comuna = comunaDetectada
  }

  showMapModal.value = false

  if (!ubicacion.numero) {
    notificar(
      'Ubicación seleccionada. Completa el número de la dirección.',
      'error',
    )
  } else if (!comunaDetectada) {
    notificar(
      'Ubicación seleccionada. Selecciona la comuna correspondiente.',
      'error',
    )
  } else {
    notificar('Ubicación seleccionada correctamente', 'ok')
  }
}

const crearNuevaEntrega = async () => {
  showAddressErrors.value = true // Activar la validación visual de los campos
  
  try {
if (!authStore.userId) {
      throw new Error('Usuario no validado')
    }

    // 1. Validar campos de texto requeridos
    if (
      !newAddress.value.direccion?.trim() ||
      !newAddress.value.numero?.trim() ||
      !newAddress.value.rut_Recibe_Entrega?.trim() ||
      !newAddress.value.comuna?.trim()
    ) {
      notificar(
        'Completa dirección, número, RUT de quien recibe y comuna',
        'error',
      )
      return
    }

    // 2. Validar que las coordenadas existan y sean números
    if (
      newAddress.value.latitud === undefined || 
      newAddress.value.longitud === undefined || 
      !Number.isFinite(newAddress.value.latitud) ||
      !Number.isFinite(newAddress.value.longitud)
    ) {
      notificar('Ingresa coordenadas válidas', 'error')
      return
    }

    // 3. Validar límites geográficos reales
    if (newAddress.value.latitud < -90 || newAddress.value.latitud > 90) {
      notificar('La latitud debe estar entre -90 y 90', 'error')
      return
    }

    if (newAddress.value.longitud < -180 || newAddress.value.longitud > 180) {
      notificar('La longitud debe estar entre -180 y 180', 'error')
      return
    }

    // 4. Armar el payload con los valores por defecto
    const payload: Partial<InformacionEntregaEntidad> = {
      ...newAddress.value,
      usuarioId: Number(authStore.userId),
      activa: true,
      estado_Entrega: 'PENDIENTE'
    }

    await crearEntrega(payload)
    
    // 5. Resetear formulario y variables
    showAddressModal.value = false
    showAddressErrors.value = false // <- Tu aporte para limpiar la UI
    newAddress.value = {
      direccion: '',
      numero: '',
      rut_Recibe_Entrega: '',
      comuna: '',
      latitud: undefined,
      longitud: undefined,
    }
    await cargarCarrito()
    notificar('Dirección guardada', 'ok')
  } catch (err: unknown) {
    console.error('Error creando entrega:', err)
    notificar(
      extraerMensajeError(err, 'No se pudo guardar la dirección'),
      'error',
    )
  }
}

const crearNuevoPago = async () => {
  try {
    if (!authStore.userId) throw new Error('Usuario no validado')
    // build fecha_Expiracion from selects as MM/YYYY
    if (newPaymentMonth.value && newPaymentYear.value) {
      newPayment.value.fecha_Expiracion = `${newPaymentMonth.value}/${newPaymentYear.value}`
    }
    const payload = { ...newPayment.value, usuario_ID: Number(authStore.userId) }
    await crearDatosPago(payload)
    showPaymentModal.value = false
    // reset selects
    newPaymentMonth.value = ''
    newPaymentYear.value = ''
    newPayment.value = { metodo_Pago: '', numero_Tarjeta: '', fecha_Expiracion: '' }
    await cargarCarrito()
    notificar('Método de pago guardado', 'ok')
  } catch (err) {
    console.error('Error creando pago:', err)
    notificar('No se pudo guardar el método de pago', 'error')
  }
}

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
  }, tipo === 'error' ? 4500 : 1800)
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
    const [carritoResp, entregasResp, pagoResp] = await Promise.all([
      carritoServicio.obtenerOCrearPorCliente(userId),
      obtenerEntregasPorUsuario(userId),
      obtenerDatosPagoPorUsuario(userId),
    ])

    carrito.value = carritoResp.data
    entregas.value = entregasResp.data
    datosPago.value = pagoResp.data
    selectedEntregaId.value = (entregas.value && entregas.value.length) ? entregas.value[0]!.info_Entrega_ID : null
    selectedPagoId.value = (datosPago.value && datosPago.value.length) ? datosPago.value[0]!.datos_Pago_ID : null

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

const total = computed(() => subtotal.value)

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(value)
}

const navigateToPerfil = () => {
  router.push({ name: 'Perfil' })
}

const handleEntregaChange = () => {
  if (selectedEntregaId.value === 'new') {
    selectedEntregaId.value = null
    showAddressErrors.value = false // Resetear errores al abrir modal
    showAddressModal.value = true
  }
}

const handlePagoChange = () => {
  if (selectedPagoId.value === 'new') {
    selectedPagoId.value = null
    showPaymentModal.value = true
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

const solicitarOrden = async () => {
  if (!carrito.value?.carrito_ID) return

  if (
    !selectedEntregaId.value ||
    !selectedPagoId.value ||
    selectedEntregaId.value === 'new' ||
    selectedPagoId.value === 'new'
  ) {
    notificar(
      'Selecciona dirección de envío y datos de pago',
      'error'
    )
    return
  }

  try {
    cargando.value = true
    almacenAsignado.value = null
    
    // 1. Ejecutamos el checkout asegurando el tipado numérico (aporte de tus compañeros)
    const resp = await carritoServicio.checkout(carrito.value.carrito_ID, {
      infoEntregaId: Number(selectedEntregaId.value),
      datosPagoId: Number(selectedPagoId.value),
    })

    // 2. Buscamos el almacén asignado a la orden (tu aporte)
    const ordenId = (resp.data as { ordenId?: number })?.ordenId
    if (ordenId) {
      try {
        const ordenResp = await ordenesServicio.obtenerPorId(ordenId)
        almacenAsignado.value = ordenResp.data.almacen_Nombre ?? null
      } catch (e) {
        console.warn('No se pudo obtener el almacén asignado:', e)
      }
    }

    // 3. Mostramos la notificación dinámica (tu aporte)
    notificar(
      almacenAsignado.value
        ? `Orden solicitada. Se despachará desde ${almacenAsignado.value}.`
        : 'Orden solicitada correctamente',
      'ok'
    )
    await cargarCarrito()

  } catch (err: unknown) {
    console.error('Error al solicitar orden:', err)

    let mensaje = 'No se pudo procesar la orden'

    if (axios.isAxiosError(err)) {
      const respuesta = err.response?.data

      if (typeof respuesta === 'string') {
        mensaje = respuesta
      } else if (
        respuesta &&
        typeof respuesta === 'object'
      ) {
        if ('message' in respuesta) {
          mensaje = String(respuesta.message)
        } else if ('error' in respuesta) {
          mensaje = String(respuesta.error)
        }
      }
    }

    notificar(mensaje, 'error')

  } finally {
    cargando.value = false
  }
}

onMounted(async () => {
  await cargarCarrito()
})
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
    </div>

    <div v-if="cargando" class="estado">Cargando carrito...</div>
    <div v-else-if="error" class="estado error">{{ error }}</div>

    <div v-if="carrito" class="panel principal grid-columnas">
      <section class="panel-subpanel">
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
              <td>{{ formatCurrency(item.producto?.precio ?? 0) }}</td>
              <td>
                <div class="cantidad">
                  <button class="btn-cantidad" @click="disminuirUnidad(item)">−</button>
                  <span class="cantidad-valor">{{ item.unidad_producto }}</span>
                  <button class="btn-cantidad" @click="aumentarUnidad(item)">+</button>
                </div>
              </td>
              <td>{{ formatCurrency((item.producto?.precio ?? 0) * item.unidad_producto) }}</td>
            </tr>
            <tr v-if="items.length === 0">
              <td colspan="4" class="vacio">Carrito sin productos</td>
            </tr>
          </tbody>
        </table>
        <div class="subtotal">
          <span>Subtotal</span>
          <strong>{{ formatCurrency(total) }}</strong>
        </div>
      </section>

      <section class="panel-subpanel resumen-compra">
        <h2 class="subtitulo">Resumen de compra</h2>
            <div class="campo">
          <label for="entrega">Dirección de entrega</label>
          <select id="entrega" v-model="selectedEntregaId" @change="handleEntregaChange">
            <option v-if="entregas.length" disabled value="">Selecciona una dirección</option>
            <option v-for="entrega in entregas" :key="entrega.info_Entrega_ID" :value="entrega.info_Entrega_ID">
              {{ entrega.direccion }} - {{ entrega.numero }}
            </option>
            <option value="new">+ Añadir nueva dirección</option>
            <option v-if="entregas.length === 0" disabled>No hay direcciones registradas</option>
          </select>
        </div>

        <div class="campo">
          <label for="pago">Método de pago</label>
          <select id="pago" v-model="selectedPagoId" @change="handlePagoChange">
            <option v-if="datosPago.length" disabled value="">Selecciona un método</option>
            <option v-for="pago in datosPago" :key="pago.datos_Pago_ID" :value="pago.datos_Pago_ID">
              {{ pago.metodo_Pago }} · **** {{ pago.numero_Tarjeta.slice(-4) }}
            </option>
            <option value="new">+ Añadir nuevo método</option>
            <option v-if="datosPago.length === 0" disabled>No hay datos de pago guardados</option>
          </select>
        </div>

        <div class="resumen-info">
          <div>
            <span>Subtotal</span>
            <strong>{{ formatCurrency(total) }}</strong>
          </div>
          <div>
            <span>Total</span>
            <strong>{{ formatCurrency(total) }}</strong>
          </div>
        </div>

        <div v-if="almacenAsignado" class="almacen-asignado">
          Su pedido fue realizado correctamente. <br />
          📦 Se despachará desde: <strong>{{ almacenAsignado }}</strong>
        </div>

        <button class="btn-primario btn-amplio" :disabled="!items.length || !selectedEntregaId || !selectedPagoId" @click="solicitarOrden">
          Solicitar orden
        </button>
      </section>
    </div>

    <div v-else class="estado">No hay carrito activo.</div>
  </div>

  <!-- Modal: Crear dirección -->
  <div v-if="showAddressModal" class="modal-overlay" @click.self="showAddressModal = false; showAddressErrors = false">
    <div class="modal-box" role="dialog" aria-modal="true">
      <div class="modal-header">
        <h3 class="modal-title">Agregar nueva dirección</h3>
        <button class="modal-close" @click="showAddressModal = false; showAddressErrors = false">×</button>
      </div>
      <div class="modal-body">
        <div class="form-grid">
          <label>Dirección</label>
          <input
            type="text"
            v-model.trim="newAddress.direccion"
            placeholder="Ejemplo: Av. Providencia"
          />

          <label>Número</label>
          <input
            type="text"
            v-model.trim="newAddress.numero"
            placeholder="Ejemplo: 1234"
          />

          <label>RUT quien recibe</label>
          <input
            type="text"
            v-model.trim="newAddress.rut_Recibe_Entrega"
            placeholder="Ejemplo: 12.345.678-9"
          />

          <label>Comuna</label>
          <div class="comuna-autocompletar">
            <input
              type="text"
              v-model.trim="newAddress.comuna"
              placeholder="Escribe o selecciona una comuna"
              autocomplete="off"
              @focus="mostrarSugerenciasComuna = true"
              @input="mostrarSugerenciasComuna = true"
              @blur="ocultarSugerenciasComuna"
            />

            <div
              v-if="mostrarSugerenciasComuna"
              class="comuna-sugerencias"
            >
              <button
                v-for="comuna in comunasFiltradas"
                :key="comuna"
                type="button"
                class="comuna-opcion"
                @mousedown.prevent="seleccionarComuna(comuna)"
              >
                {{ comuna }}
              </button>

              <div
                v-if="comunasFiltradas.length === 0"
                class="comuna-sin-resultados"
              >
                No se encontraron comunas
              </div>
            </div>
          </div>
        </div>

        <div class="form-grid" style="margin-top: 10px;">
          <!-- LATITUD MODIFICADA CON MENSAJE DE ERROR -->
          <label>Latitud</label>
          <div class="input-con-error">
            <input 
              type="number" 
              step="0.000001" 
              v-model.number="newAddress.latitud" 
              placeholder="Ej: -33.4489" 
              :class="{ 'input-rojo': showAddressErrors && !Number.isFinite(newAddress.latitud) }" 
            />
            <span v-if="showAddressErrors && !Number.isFinite(newAddress.latitud)" class="texto-error-inline">
              Campo obligatorio
            </span>
          </div>

          <!-- LONGITUD MODIFICADA CON MENSAJE DE ERROR -->
          <label>Longitud</label>
          <div class="input-con-error">
            <input 
              type="number" 
              step="0.000001" 
              v-model.number="newAddress.longitud" 
              placeholder="Ej: -70.6693" 
              :class="{ 'input-rojo': showAddressErrors && !Number.isFinite(newAddress.longitud) }" 
            />
            <span v-if="showAddressErrors && !Number.isFinite(newAddress.longitud)" class="texto-error-inline">
              Campo obligatorio
            </span>
          </div>
        </div>

        <!-- Botones liberados de la grilla -->
        <div class="coordenadas-acciones">
          <button type="button" class="btn-ubicacion" :disabled="obteniendoUbicacion" @click="usarUbicacionActual">
            📍 {{ obteniendoUbicacion ? 'Ubicando y completando...' : 'Usar mi ubicación actual' }}
          </button>
          <button type="button" class="btn-link" @click="abrirSelectorMapa">
            🗺️ Seleccionar en mapa
          </button>
        </div>
        
        <p class="ayuda-coordenadas">
          La ubicación actual completa automáticamente la calle, el número
          cuando está disponible, la comuna y las coordenadas. El RUT de quien
          recibe debe ingresarse manualmente.
        </p>
      </div>
      <div class="modal-actions">
        <button class="btn-link" @click="showAddressModal = false; showAddressErrors = false">Cancelar</button>
        <button class="btn-solid" @click="crearNuevaEntrega">Guardar dirección</button>
      </div>
    </div>
  </div>


  <!-- Modal: Seleccionar ubicación en mapa -->
  <div
    v-if="showMapModal"
    class="modal-overlay modal-mapa-overlay"
    @click.self="showMapModal = false"
  >
    <div
      class="modal-box modal-mapa"
      role="dialog"
      aria-modal="true"
    >
      <div class="modal-header">
        <h3 class="modal-title">Seleccionar ubicación</h3>
        <button
          class="modal-close"
          @click="showMapModal = false"
        >
          ×
        </button>
      </div>

      <div class="modal-body">
        <SelectorUbicacion
          :latitud-inicial="newAddress.latitud"
          :longitud-inicial="newAddress.longitud"
          @seleccionar="aplicarUbicacionSeleccionada"
          @cancelar="showMapModal = false"
        />
      </div>
    </div>
  </div>

  <!-- Modal: Crear método de pago -->
  <div v-if="showPaymentModal" class="modal-overlay" @click.self="showPaymentModal = false">
    <div class="modal-box" role="dialog" aria-modal="true">
      <div class="modal-header">
        <h3 class="modal-title">Agregar método de pago</h3>
        <button class="modal-close" @click="showPaymentModal = false">×</button>
      </div>
      <div class="modal-body">
        <div class="form-grid">
          <label>Método</label>
          <input type="text" v-model="newPayment.metodo_Pago" placeholder="Tarjeta Crédito / Débito" />
          <label>Número de tarjeta</label>
          <input type="text" v-model="newPayment.numero_Tarjeta" placeholder="1234 5678 9012 3456" />
          <label>Fecha expiración</label>
          <div style="display:flex;gap:8px;align-items:center">
            <select v-model="newPaymentMonth">
              <option value="" disabled>Mes</option>
              <option value="01">01 - Ene</option>
              <option value="02">02 - Feb</option>
              <option value="03">03 - Mar</option>
              <option value="04">04 - Abr</option>
              <option value="05">05 - May</option>
              <option value="06">06 - Jun</option>
              <option value="07">07 - Jul</option>
              <option value="08">08 - Ago</option>
              <option value="09">09 - Sep</option>
              <option value="10">10 - Oct</option>
              <option value="11">11 - Nov</option>
              <option value="12">12 - Dic</option>
            </select>
            <select v-model="newPaymentYear">
              <option value="" disabled>Año</option>
              <!-- generate years from current to +10 -->
              <option v-for="y in (new Array(11).fill(0).map((_,i)=> new Date().getFullYear()+i))" :key="y" :value="String(y)">{{ y }}</option>
            </select>
          </div>
        </div>
      </div>
      <div class="modal-actions">
        <button class="btn-link" @click="showPaymentModal = false">Cancelar</button>
        <button class="btn-solid" @click="crearNuevoPago">Guardar método</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pagina { display: flex; flex-direction: column; gap: 20px; }
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
  max-width: 360px;
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
.panel { background: #fff; border: 1px solid #e6e6e6; border-radius: 12px; padding: 16px; width: 100%; }
.panel.principal { max-width: none; }
.grid-columnas { display: grid; grid-template-columns: 2.3fr 1fr; gap: 20px; width: 100%; }
.panel-subpanel { background: #fff; border-radius: 12px; padding: 16px; border: 1px solid #ececec; }
.resumen-compra { display: flex; flex-direction: column; gap: 16px; }
.campo { display: grid; gap: 8px; }
.campo label { color: #444; font-size: 0.95rem; font-weight: 600; }
.campo select { width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 10px; background: #fff; color: #222; }
.resumen-info { display: grid; gap: 10px; padding: 14px 0; border-top: 1px solid #f2f2f2; border-bottom: 1px solid #f2f2f2; }
.resumen-info div { display: flex; justify-content: space-between; align-items: center; }
.almacen-asignado {
  background: #eef7ff;
  border: 1px solid #cfe6f7;
  color: #0f4c75;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.9rem;
}
.btn-amplio { width: 100%; padding: 12px 16px; }
.btn-primario { background: #156895; color: #fff; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; }
.btn-primario:hover { background: #1b76a5; }
.btn-primario:disabled { background: #98abd2; cursor: not-allowed; }
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
.ayuda-coordenadas { font-size: 0.78rem; color: #888; margin-top: 8px; }

.coordenadas-acciones { 
  display: flex; 
  align-items: center;
  gap: 15px; 
  margin-top: 12px; 
}
.btn-ubicacion {
  background: #eef7ff;
  border: 1px solid #cfe6f7;
  color: #0f4c75;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 0.85rem;
  cursor: pointer;
}
.btn-ubicacion:hover { background: #dcecf9; }
.btn-ubicacion:disabled { opacity: 0.6; cursor: not-allowed; }

/* ======== CSS MODIFICADO PARA LOS ERRORES ======== */
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.form-grid label { font-size: 0.85rem; color: #444; }

/* Contenedor que agrupa el input y su mensaje de error */
.input-con-error {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* Se aseguró que el input ocupe el 100% de la celda de la grilla */
.form-grid input, .form-grid select { 
  width: 100%; 
  padding: 10px 12px; 
  border: 1px solid #d1d5db; 
  border-radius: 8px; 
  box-sizing: border-box;
}
.form-grid input[type="month"] { padding: 8px 10px; }

.comuna-autocompletar {
  position: relative;
  width: 100%;
}

.comuna-sugerencias {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 360;
  max-height: 180px;
  overflow-y: auto;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(0,0,0,0.15);
}

.comuna-opcion {
  display: block;
  width: 100%;
  padding: 9px 11px;
  border: none;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  color: #222;
  text-align: left;
  cursor: pointer;
}

.comuna-opcion:hover {
  background: #eef7ff;
}

.comuna-sin-resultados {
  padding: 10px 11px;
  color: #777;
  font-size: 0.8rem;
}

/* Clases para pintar de rojo y mostrar el texto */
.input-rojo {
  border-color: #b00020 !important;
  background-color: #fff9fa;
}
.texto-error-inline {
  color: #b00020;
  font-size: 0.75rem;
  font-weight: 600;
  padding-left: 2px;
}

.modal-box .modal-title { font-size: 1.05rem; }

.modal-mapa-overlay {
  z-index: 320;
}
.modal-mapa {
  width: 650px;
  max-width: 94vw;
  max-height: 92vh;
  overflow-y: auto;
}

@media (max-width: 600px) {
  .form-grid { grid-template-columns: 1fr; }
  .coordenadas-acciones { flex-wrap: wrap; }
}
</style>