<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/services/api'
import AlertBanner from '@/components/AlertBanner.vue'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const latitud = ref<number | null>(null)
const longitud = ref<number | null>(null)

const errors = ref({
  username: '',
  currentPassword: '',
  newPassword: '',
  latitud: '',
  longitud: '',
})

const errorGlobal = ref('')
const successMsg = ref('')
const loading = ref(false)
const showDeleteModal = ref(false)
const deleteConfirmPassword = ref('')

function openDeleteModal() {
  deleteConfirmPassword.value = ''
  errorGlobal.value = ''
  showDeleteModal.value = true
}

function loadUserProfile() {
  if (auth.user) {
    username.value = auth.user.username
    latitud.value = auth.user.latitud
    longitud.value = auth.user.longitud
  }
}

onMounted(() => {
  loadUserProfile()
})

function useCurrentLocation() {
  if (!navigator.geolocation) {
    errorGlobal.value = 'Tu navegador no soporta geolocalización'
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      latitud.value = parseFloat(pos.coords.latitude.toFixed(6))
      longitud.value = parseFloat(pos.coords.longitude.toFixed(6))
      errors.value.latitud = ''
      errors.value.longitud = ''
    },
    () => {
      errorGlobal.value = 'No se pudo obtener la ubicación'
    },
  )
}

function clearErrors() {
  errorGlobal.value = ''
  successMsg.value = ''
  deleteConfirmPassword.value = ''
  errors.value = {
    username: '',
    currentPassword: '',
    newPassword: '',
    latitud: '',
    longitud: '',
  }
}

async function handleUpdateProfile() {
  clearErrors()
  let hasErrors = false

  if (!username.value.trim()) {
    errors.value.username = 'El nombre de usuario es obligatorio'
    hasErrors = true
  }
  if (!currentPassword.value) {
    errors.value.currentPassword = 'Debes ingresar tu contraseña actual para confirmar los cambios'
    hasErrors = true
  }
  if (latitud.value === null || isNaN(Number(latitud.value))) {
    errors.value.latitud = 'La latitud es obligatoria y debe ser un número válido'
    hasErrors = true
  }
  if (longitud.value === null || isNaN(Number(longitud.value))) {
    errors.value.longitud = 'La longitud es obligatoria y debe ser un número válido'
    hasErrors = true
  }

  if (hasErrors) {
    return
  }

  loading.value = true
  try {
    const payload = {
      username: username.value.trim(),
      currentPassword: currentPassword.value,
      newPassword: newPassword.value ? newPassword.value : null,
      latitud: latitud.value,
      longitud: longitud.value,
    }

    const response = await authApi.updateProfile(payload)
    const { user: updatedUser, token: newToken } = response.data

    auth.updateSession(updatedUser, newToken)
    successMsg.value = 'Perfil actualizado correctamente'
    currentPassword.value = '' // clear confirm field
    newPassword.value = '' // clear new password field
    setTimeout(() => {
      successMsg.value = ''
    }, 4000)
  } catch (e: any) {
    const backendMsg = e.response?.data?.message || 'Error al actualizar el perfil'
    if (backendMsg.toLowerCase().includes('contraseña')) {
      errors.value.currentPassword = backendMsg
    } else {
      errorGlobal.value = backendMsg
    }
  } finally {
    loading.value = false
  }
}

async function handleDeleteAccount() {
  if (!deleteConfirmPassword.value) {
    errorGlobal.value = 'Debes ingresar tu contraseña para confirmar'
    return
  }
  loading.value = true
  errorGlobal.value = ''
  try {
    await authApi.deleteAccount(deleteConfirmPassword.value)
    showDeleteModal.value = false
    auth.logout()
    alert('Tu cuenta y todas tus tareas han sido eliminadas permanentemente. ¡Lamentamos verte partir!')
    router.push('/login')
  } catch (e: any) {
    errorGlobal.value = e.response?.data?.message || 'Error al eliminar la cuenta (verifica tu contraseña)'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="perfil-page">
    <div class="page-header">
      <div class="header-left">
        <h1><Icon icon="lucide:user" class="icon" /> Mi Perfil</h1>
        <p class="subtitle">Administra los detalles de tu cuenta y ubicación geográfica</p>
      </div>
    </div>

    <!-- AlertBanner: componente reutilizable en src/components/AlertBanner.vue -->
    <AlertBanner :message="successMsg" type="success" @close="successMsg = ''" />
    <AlertBanner :message="errorGlobal" type="error" @close="errorGlobal = ''" />

    <div class="perfil-container">
      <!-- Tarjeta Formulario Perfil -->
      <div class="profile-card">
        <h3>Editar Información de Cuenta</h3>
        <form @submit.prevent="handleUpdateProfile">
          <div class="form-group">
            <label for="username">Nombre de Usuario *</label>
            <input
              id="username"
              v-model="username"
              type="text"
              placeholder="Nombre de usuario"
              :class="{ 'input-error': errors.username }"
              @input="errors.username = ''"
              required
            />
            <span v-if="errors.username" class="field-error">{{ errors.username }}</span>
          </div>

          <div class="form-group">
            <label for="currentPassword">Contraseña Actual *</label>
            <input
              id="currentPassword"
              v-model="currentPassword"
              type="password"
              placeholder="Introduce tu contraseña actual para confirmar"
              :class="{ 'input-error': errors.currentPassword }"
              @input="errors.currentPassword = ''"
              required
            />
            <span v-if="errors.currentPassword" class="field-error">{{ errors.currentPassword }}</span>
          </div>

          <div class="form-group">
            <label for="newPassword">Nueva Contraseña</label>
            <input
              id="newPassword"
              v-model="newPassword"
              type="password"
              placeholder="Nueva contraseña (dejar en blanco para no cambiar)"
              autocomplete="new-password"
              :class="{ 'input-error': errors.newPassword }"
              @input="errors.newPassword = ''"
            />
            <p class="field-hint"> Ingresa una contraseña solo si deseas cambiar la actual.</p>
            <span v-if="errors.newPassword" class="field-error">{{ errors.newPassword }}</span>
          </div>

          <div class="form-group">
            <label>Ubicación Geográfica Asignada *</label>
            <button type="button" class="btn-geo" @click="useCurrentLocation">
              <Icon icon="lucide:locate" class="icon" /> Detectar mi ubicación actual
            </button>
            <div class="coords-grid">
              <div>
                <label for="latitud" class="sub-label">Latitud</label>
                <input
                  id="latitud"
                  v-model.number="latitud"
                  type="number"
                  step="any"
                  placeholder="Ej: -33.45"
                  :class="{ 'input-error': errors.latitud }"
                  @input="errors.latitud = ''"
                  required
                />
              </div>
              <div>
                <label for="longitud" class="sub-label">Longitud</label>
                <input
                  id="longitud"
                  v-model.number="longitud"
                  type="number"
                  step="any"
                  placeholder="Ej: -70.64"
                  :class="{ 'input-error': errors.longitud }"
                  @input="errors.longitud = ''"
                  required
                />
              </div>
            </div>
            <span v-if="errors.latitud" class="field-error">{{ errors.latitud }}</span>
            <span v-if="errors.longitud" class="field-error">{{ errors.longitud }}</span>
          </div>

          <button type="submit" :disabled="loading" class="btn-primary">
            {{ loading ? 'Guardando cambios...' : 'Actualizar Perfil' }}
          </button>
        </form>
      </div>

      <!-- Tarjeta Información Espacial & Zona de Peligro -->
      <div class="sidebar-container">
        <!-- Tarjeta Info Geográfica -->
        <div class="profile-card info-card">
          <h3> Tu Geolocalización</h3>
          <div class="coords-display" v-if="latitud && longitud">
            <div class="coord-block">
              <span class="coord-label">Latitud</span>
              <span class="coord-value">{{ latitud.toFixed(6) }}</span>
            </div>
            <div class="coord-block">
              <span class="coord-label">Longitud</span>
              <span class="coord-value">{{ longitud.toFixed(6) }}</span>
            </div>
            <a
              :href="`https://www.google.com/maps?q=${latitud},${longitud}`"
              target="_blank"
              class="maps-link"
            >
              Ver mi posición en Google Maps ↗
            </a>
          </div>
          <div v-else class="coords-display-empty">
            No se han registrado coordenadas geográficas para tu cuenta.
          </div>
          <p class="geo-desc">
            Las consultas de proximidad espacial (como la tarea más cercana y las distancias promedio) se calculan en base a estas coordenadas exactas.
          </p>
        </div>

        <!-- Danger Zone Card -->
        <div class="profile-card danger-zone-card">
          <h3>Zona de Peligro</h3>
          <p class="danger-desc">
            Al eliminar tu cuenta, todos tus datos y tareas asociadas serán borrados de forma permanente. Esta acción no se puede deshacer.
          </p>
          <button @click="openDeleteModal" class="btn-danger">
            Eliminar mi cuenta permanentemente
          </button>
        </div>
      </div>
    </div>

    <!-- ===== MODAL CONFIRMAR ELIMINACIÓN DE CUENTA ===== -->
    <div v-if="showDeleteModal" class="modal-overlay" @click.self="showDeleteModal = false">
      <div class="modal modal-confirm" role="dialog" aria-modal="true">
        <div class="confirm-icon"><Icon icon="lucide:triangle-alert" class="icon" style="font-size: 3rem; color: #e74c3c" /></div>
        <h3>¿Eliminar tu cuenta definitivamente?</h3>
        <p class="confirm-desc">
          Esta acción es irreversible. Se eliminará permanentemente tu usuario <strong>{{ username }}</strong>,
          así como todas tus tareas creadas y datos del sistema.
        </p>
        <p class="confirm-alert-box">
          <Icon icon="lucide:shield-alert" class="icon" /> Esta acción requiere confirmación de seguridad. Por favor ingresa tu contraseña actual para proceder con el borrado físico en cascada.
        </p>

        <div class="form-group" style="text-align: left; margin-bottom: 1.5rem;">
          <label for="delete-password" style="color: #c0392b;">Contraseña actual *</label>
          <input
            id="delete-password"
            v-model="deleteConfirmPassword"
            type="password"
            placeholder="Introduce tu contraseña actual"
            required
          />
        </div>

        <p v-if="errorGlobal" class="field-error" style="margin-bottom: 1rem;">{{ errorGlobal }}</p>

        <div class="modal-footer">
          <button @click="showDeleteModal = false" class="btn-cancel">Cancelar</button>
          <button
            @click="handleDeleteAccount"
            :disabled="loading || !deleteConfirmPassword.trim()"
            class="btn-delete-confirm"
          >
            {{ loading ? 'Eliminando...' : 'Sí, eliminar cuenta y datos' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.perfil-page {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

.page-header {
  margin-bottom: 2rem;
}

.header-left h1 {
  font-size: 1.7rem;
  color: #2c3e50;
  margin: 0 0 0.2rem 0;
}

.subtitle {
  color: #7f8c8d;
  font-size: 0.9rem;
  margin: 0;
}

/* Banners */
.success-banner {
  background: linear-gradient(135deg, #d5f5e3, #a9dfbf);
  border: 1px solid #27ae60;
  color: #1e8449;
  padding: 0.8rem 1.2rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
  font-weight: 500;
}

.error-banner {
  background: #fdecea;
  border: 1px solid #e74c3c;
  color: #c0392b;
  padding: 0.8rem 1.2rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  color: #c0392b;
}

/* Layout */
.perfil-container {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 2rem;
}

@media (max-width: 768px) {
  .perfil-container {
    grid-template-columns: 1fr;
  }
}

.profile-card {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
  border-top: 4px solid #3498db;
}

.sidebar-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.info-card {
  border-top-color: #2ecc71;
}

.danger-zone-card {
  border-top-color: #e74c3c;
  background-color: #fdf2f2;
}

h3 {
  color: #2c3e50;
  margin-bottom: 1.5rem;
  font-size: 1.15rem;
  font-weight: 700;
}

.form-group {
  margin-bottom: 1.2rem;
}

label {
  display: block;
  font-size: 0.85rem;
  font-weight: 700;
  color: #555;
  margin-bottom: 0.4rem;
}

.sub-label {
  display: block;
  font-size: 0.78rem;
  color: #7f8c8d;
  margin-bottom: 0.25rem;
}

input[type='text'],
input[type='password'],
input[type='number'] {
  width: 100%;
  padding: 0.65rem 0.9rem;
  border: 1.5px solid #dde1e7;
  border-radius: 8px;
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

input:focus {
  border-color: #3498db;
}

.field-hint {
  font-size: 0.78rem;
  color: #7f8c8d;
  margin-top: 0.3rem;
}

.field-error {
  color: #e74c3c;
  font-size: 0.8rem;
  margin-top: 0.25rem;
  display: block;
}

.input-error {
  border-color: #e74c3c !important;
  background-color: #fdf2f2 !important;
}

.btn-geo {
  width: 100%;
  background: linear-gradient(135deg, #27ae60, #2ecc71);
  color: white;
  border: none;
  padding: 0.55rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 0.8rem;
  transition: opacity 0.2s;
}

.btn-geo:hover {
  opacity: 0.95;
}

.coords-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.btn-primary {
  width: 100%;
  background: linear-gradient(135deg, #2c3e50, #3498db);
  color: white;
  border: none;
  padding: 0.75rem;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  margin-top: 1rem;
  transition: opacity 0.2s;
  box-shadow: 0 3px 10px rgba(52, 152, 219, 0.2);
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Geolocalización Sidebar */
.coords-display {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  border-left: 4px solid #2ecc71;
  margin-bottom: 1rem;
}

.coords-display-empty {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  color: #7f8c8d;
  font-size: 0.88rem;
  text-align: center;
  margin-bottom: 1rem;
}

.coord-block {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.coord-label {
  font-size: 0.78rem;
  text-transform: uppercase;
  color: #95a5a6;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.coord-value {
  font-family: 'Courier New', monospace;
  font-weight: 700;
  color: #2c3e50;
  font-size: 0.95rem;
}

.maps-link {
  color: #3498db;
  text-decoration: none;
  font-size: 0.85rem;
  font-weight: 600;
  text-align: center;
  display: block;
  margin-top: 0.5rem;
}

.maps-link:hover {
  text-decoration: underline;
}

.geo-desc, .danger-desc {
  font-size: 0.82rem;
  color: #7f8c8d;
  line-height: 1.4;
}

.danger-desc {
  color: #c0392b;
  margin-bottom: 1.2rem;
}

.btn-danger {
  width: 100%;
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: white;
  border: none;
  padding: 0.65rem;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
  box-shadow: 0 3px 8px rgba(231, 76, 60, 0.15);
}

.btn-danger:hover {
  opacity: 0.9;
}

/* Modal confirmación */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 200;
  padding: 1rem;
}

.modal {
  background: white;
  border-radius: 14px;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  padding: 2rem 1.8rem;
  text-align: center;
}

.confirm-icon {
  font-size: 3rem;
  margin-bottom: 0.8rem;
}

.modal-confirm h3 {
  color: #c0392b;
  font-size: 1.25rem;
  margin-bottom: 0.6rem;
}

.confirm-desc {
  color: #555;
  font-size: 0.88rem;
  line-height: 1.5;
  margin-bottom: 1rem;
}

.confirm-alert-box {
  background-color: #fdf2f2;
  border-left: 4px solid #e74c3c;
  padding: 0.6rem 0.8rem;
  font-size: 0.82rem;
  color: #c0392b;
  text-align: left;
  line-height: 1.4;
  border-radius: 4px;
  margin-bottom: 1.5rem;
}

.modal-footer {
  display: flex;
  justify-content: center;
  gap: 0.8rem;
}

.btn-cancel {
  background: #f0f3f4;
  color: #555;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
}

.btn-cancel:hover {
  background: #e0e6e8;
}

.btn-delete-confirm {
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: white;
  border: none;
  padding: 0.6rem 1.2rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
}

.btn-delete-confirm:hover {
  opacity: 0.9;
}
.icon {
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
  flex-shrink: 0;
  display: inline-block;
}
</style>
