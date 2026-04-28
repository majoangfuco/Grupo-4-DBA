<script setup lang="ts">
// =====================================================
// FormularioAgregarCliente.vue
// Formulario para registrar un nuevo cliente.
// Incluye validación de campos y manejo de errores
// provenientes de la API.
// =====================================================

import { ref, reactive } from 'vue'

// --- Evento que emite al padre cuando se agrega un cliente ---
const emit = defineEmits<{
  (e: 'clienteAgregado'): void
}>()

// --- Estado del formulario ---
const nombre    = ref('')
const email     = ref('')
const telefono  = ref('')
const rut       = ref('')

// --- Errores de validación por campo ---
const errores = reactive<Record<string, string>>({})

// --- Alerta de resultado (éxito o error general) ---
const alerta = reactive({
  visible: false,
  mensaje: '',
  tipo: 'exito' as 'exito' | 'error'
})

// --- Limpia todos los campos del formulario ---
const limpiarFormulario = () => {
  nombre.value   = ''
  email.value    = ''
  telefono.value = ''
  rut.value      = ''
  Object.keys(errores).forEach(k => delete errores[k])
}

// --- Validación local de los campos ---
const validarFormulario = (): boolean => {
  Object.keys(errores).forEach(k => delete errores[k])

  if (!rut.value) {
    errores.rut = 'El RUT es obligatorio.'
  } else if (!/\d{7,8}-\d/.test(rut.value)) {
    errores.rut = 'El formato del RUT es inválido. Ej: 12345678-9'
  }

  if (!nombre.value) errores.nombre = 'El nombre es obligatorio.'

  if (!email.value) {
    errores.email = 'El correo es obligatorio.'
  } else if (!/\S+@\S+\.\S+/.test(email.value)) {
    errores.email = 'El formato del correo es inválido.'
  }

  if (!telefono.value) errores.telefono = 'El teléfono es obligatorio.'

  return Object.keys(errores).length === 0
}

// --- Detecta a qué campo apunta un error de la API ---
const obtenerCampoDesdeError = (mensaje: string): string | null => {
  const msg = mensaje.toLowerCase()
  if (msg.includes('rut'))                              return 'rut'
  if (msg.includes('correo') || msg.includes('email'))  return 'email'
  if (msg.includes('teléfono') || msg.includes('telefono')) return 'telefono'
  return null
}

// --- Envío del formulario ---
const manejarEnvio = async () => {
  if (!validarFormulario()) return

  const datosCliente = {
    nombre:   nombre.value,
    email:    email.value,
    telefono: telefono.value,
    rut:      rut.value,
  }

  try {
    // TODO: reemplazar por la llamada real al servicio cuando el backend esté listo
    // await clienteServicio.crearCliente(datosCliente)
    console.log('Cliente a registrar:', datosCliente)

    alerta.visible = true
    alerta.mensaje = 'Cliente agregado con éxito.'
    alerta.tipo    = 'exito'
    limpiarFormulario()
    emit('clienteAgregado')
  } catch (error: unknown) {
    console.error('Error al registrar el cliente:', error)
    const axiosError = error as { response?: { data?: { message?: string } | string } }
    const mensajeApi = axiosError.response?.data
      ? (typeof axiosError.response.data === 'string'
          ? axiosError.response.data
          : axiosError.response.data?.message ?? '')
      : ''

    if (mensajeApi) {
      const campo = obtenerCampoDesdeError(mensajeApi)
      if (campo) {
        errores[campo] = mensajeApi
      } else {
        alerta.visible = true
        alerta.mensaje = mensajeApi
        alerta.tipo    = 'error'
      }
    } else {
      alerta.visible = true
      alerta.mensaje = 'Hubo un error de conexión al agregar al cliente.'
      alerta.tipo    = 'error'
    }
  }
}
</script>

<template>
  <div class="formulario-contenedor">
    <form class="formulario" @submit.prevent="manejarEnvio" novalidate>
      <h3 class="formulario-titulo">Agregar Nuevo Cliente</h3>

      <!-- Alerta de resultado -->
      <div v-if="alerta.visible" :class="['alerta', `alerta-${alerta.tipo}`]">
        {{ alerta.mensaje }}
        <button class="alerta-cerrar" type="button" @click="alerta.visible = false">✕</button>
      </div>

      <!-- RUT -->
      <div class="campo">
        <label for="cliente-rut" class="etiqueta">
          RUT
          <span class="tooltip-icono" title="Formato esperado: 12345678-9 (con guión)">ⓘ</span>
        </label>
        <input
          id="cliente-rut"
          class="entrada"
          :class="{ 'entrada-error': errores.rut }"
          type="text"
          placeholder="12345678-9"
          v-model="rut"
          @input="delete errores.rut"
        />
        <span v-if="errores.rut" class="mensaje-error">{{ errores.rut }}</span>
      </div>

      <!-- Nombre -->
      <div class="campo">
        <label for="cliente-nombre" class="etiqueta">Nombre</label>
        <input
          id="cliente-nombre"
          class="entrada"
          :class="{ 'entrada-error': errores.nombre }"
          type="text"
          placeholder="Nombre completo"
          v-model="nombre"
          @input="delete errores.nombre"
        />
        <span v-if="errores.nombre" class="mensaje-error">{{ errores.nombre }}</span>
      </div>

      <!-- Email -->
      <div class="campo">
        <label for="cliente-email" class="etiqueta">Email</label>
        <input
          id="cliente-email"
          class="entrada"
          :class="{ 'entrada-error': errores.email }"
          type="email"
          placeholder="ejemplo@correo.com"
          v-model="email"
          @input="delete errores.email"
        />
        <span v-if="errores.email" class="mensaje-error">{{ errores.email }}</span>
      </div>

      <!-- Teléfono -->
      <div class="campo">
        <label for="cliente-telefono" class="etiqueta">Teléfono</label>
        <input
          id="cliente-telefono"
          class="entrada"
          :class="{ 'entrada-error': errores.telefono }"
          type="text"
          placeholder="+56912345678"
          v-model="telefono"
          @input="delete errores.telefono"
        />
        <span v-if="errores.telefono" class="mensaje-error">{{ errores.telefono }}</span>
      </div>

      <!-- Botón enviar -->
      <div class="formulario-acciones">
        <button type="submit" class="btn-agregar">Agregar Cliente</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
/* ===== CONTENEDOR ===== */
.formulario-contenedor {
  padding: 8px;
  min-width: 320px;
}

.formulario {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.formulario-titulo {
  font-size: 1.1rem;
  font-weight: 700;
  color: #156895;
  margin-bottom: 4px;
}

/* ===== CAMPOS ===== */
.campo {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.etiqueta {
  font-size: 0.875rem;
  font-weight: 600;
  color: #444;
  display: flex;
  align-items: center;
  gap: 4px;
}

.tooltip-icono {
  font-size: 0.85rem;
  color: #888;
  cursor: help;
}

.entrada {
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 0.9rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  outline: none;
}

.entrada:focus {
  border-color: #156895;
  box-shadow: 0 0 0 3px rgba(21, 104, 149, 0.15);
}

.entrada-error {
  border-color: #d32f2f;
}

.entrada-error:focus {
  box-shadow: 0 0 0 3px rgba(211, 47, 47, 0.15);
}

.mensaje-error {
  font-size: 0.78rem;
  color: #d32f2f;
}

/* ===== ALERTA ===== */
.alerta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.875rem;
}

.alerta-exito {
  background-color: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}

.alerta-error {
  background-color: #ffebee;
  color: #c62828;
  border: 1px solid #ef9a9a;
}

.alerta-cerrar {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  color: inherit;
  opacity: 0.7;
  padding: 0 4px;
}

.alerta-cerrar:hover { opacity: 1; }

/* ===== BOTÓN ===== */
.formulario-acciones {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.btn-agregar {
  padding: 10px 28px;
  background-color: #156895;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s, transform 0.1s;
}

.btn-agregar:hover {
  background-color: #0f5070;
}

.btn-agregar:active {
  transform: scale(0.98);
}
</style>
