<template>
  <div class="page-container">
    <div class="dashboard-wrapper">

      <!-- Top Left: Profile Info -->
      <div class="card profile-card">
        <div class="acciones-perfil">
          <button class="edit-icon" title="Editar perfil" @click="abrirEdicion">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
            </svg>
          </button>
          <button class="delete-icon" title="Eliminar cuenta" @click="confirmarEliminacion = true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
          </button>
        </div>
        <div class="profile-content">
          <div class="avatar-container">
            <div class="avatar">{{ userInitial }}</div>
          </div>
          <div class="profile-details">
            <h2 class="user-name">{{ userName }}</h2>
            <div class="info-row">
              <span class="label">Rol asignado:</span>
              <span class="value">{{ userRole }}</span>
            </div>
            <div class="info-row">
              <span class="label">E-mail:</span>
              <span class="value">{{ userEmail }}</span>
            </div>
            <div class="info-row">
              <span class="label">RUT empresa:</span>
              <span class="value">{{ userRut }}</span>
            </div>
          </div>
        </div>
      </div>


      <!-- Top Right: Payment Data (solo para clientes) -->
      <div v-if="!isAdmin" class="card payment-card">
        <h3 class="card-title">Datos de pago</h3>

        <div v-if="cargandoPago" class="loading-state">
          <p>Cargando métodos de pago...</p>
        </div>

        <div v-else-if="datosPago.length === 0" class="empty-state">
          <p>No tienes métodos de pago registrados.</p>
        </div>

        <div v-else class="payment-list">
          <!-- Primer método siempre visible -->
          <div class="payment-item">
            <div class="payment-item-header">
              <span class="payment-method-badge">{{ primerPago.metodo_Pago }}</span>
              <div class="payment-item-actions">
                <span class="payment-expiry">Vence: {{ primerPago.fecha_Expiracion }}</span>
                <button class="icon-btn icon-btn-edit" title="Editar" @click="abrirEdicionPago(primerPago)">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                  </svg>
                </button>
                <button class="icon-btn icon-btn-delete" title="Eliminar" @click="confirmarEliminarPago(primerPago)">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                  </svg>
                </button>
              </div>
            </div>
            <div class="card-number">{{ maskCardNumber(primerPago.numero_Tarjeta) }}</div>
          </div>

          <!-- Botón y dropdown flotante (solo si hay más de 1) -->
          <div v-if="datosPago.length > 1" class="dropdown-pagos-wrapper">
            <button
              class="btn-expandir-pagos"
              @click.stop="pagosExpandidos = !pagosExpandidos"
            >
              <span>{{ pagosExpandidos ? 'Ocultar' : 'Ver' }} otros {{ datosPago.length - 1 }} método{{ datosPago.length - 1 > 1 ? 's' : '' }}</span>
              <svg
                class="chevron-icon"
                :class="{ rotated: pagosExpandidos }"
                width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
              >
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>

            <!-- Dropdown flotante -->
            <Transition name="dropdown-fade">
              <div v-if="pagosExpandidos" class="dropdown-pagos-panel" @click.stop>
                <div v-for="pago in datosPago.slice(1)" :key="pago.datos_Pago_ID" class="payment-item">
                  <div class="payment-item-header">
                    <span class="payment-method-badge">{{ pago.metodo_Pago }}</span>
                    <div class="payment-item-actions">
                      <span class="payment-expiry">Vence: {{ pago.fecha_Expiracion }}</span>
                      <button class="icon-btn icon-btn-edit" title="Editar" @click="abrirEdicionPago(pago)">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                        </svg>
                      </button>
                      <button class="icon-btn icon-btn-delete" title="Eliminar" @click="confirmarEliminarPago(pago)">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <polyline points="3 6 5 6 21 6"></polyline>
                          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                        </svg>
                      </button>
                    </div>
                  </div>
                  <div class="card-number">{{ maskCardNumber(pago.numero_Tarjeta) }}</div>
                </div>
              </div>
            </Transition>
          </div>
        </div>

        <button class="btn-agregar-pago" @click="abrirCrearPago">
          + Agregar método de pago
        </button>
      </div>


      <!-- Bottom Left: Delivery Addresses (solo para clientes) -->
      <div v-if="!isAdmin" class="card delivery-card">
        <h3 class="card-title">Mis Direcciones de Entrega</h3>

        <div v-if="cargandoEntregas" class="loading-state">
          <p>Cargando direcciones...</p>
        </div>

        <div v-else-if="entregas.length === 0" class="empty-state">
          <p>No tienes direcciones de entrega asociadas.</p>
        </div>

        <div v-else class="address-list">
          <div v-for="entrega in entregas" :key="entrega.info_Entrega_ID" class="address-item">
            <div class="address-icon">📍</div>
            <div class="address-content">
              <h4>{{ entrega.direccion }} {{ entrega.numero }}</h4>
              <p><strong>Recibe:</strong> {{ entrega.rut_Recibe_Entrega }} <br> <strong>Empresa:</strong> {{ entrega.rut_Empresa }}</p>
            </div>
            <div v-if="entrega.activa" class="active-badge">Activa</div>
          </div>
        </div>
      </div>

    </div>

    <!-- ===== MODAL: EDITAR CUENTA ===== -->
    <Teleport to="body">
      <div v-if="editando" class="modal-overlay" @click.self="editando = false">
        <div class="modal-box">
          <button class="modal-cerrar" @click="editando = false">✕</button>
          <h3 class="modal-titulo">Editar mi cuenta</h3>

          <form class="modal-form" @submit.prevent="guardarCambios">
            <label>Nombre
              <input v-model="form.nombre" type="text" required />
            </label>
            <label>Correo
              <input v-model="form.correo" type="email" required />
            </label>
            <label>RUT empresa
              <input v-model="form.rut_empresa" type="text" placeholder="76.123.456-7" required disabled />
            </label>
            <label>Nueva contraseña <small>(opcional)</small>
              <input v-model="form.contrasena" type="password" placeholder="Dejar en blanco para no cambiar" />
            </label>

            <p v-if="errorEdicion" class="msg-error">{{ errorEdicion }}</p>
            <p v-if="okEdicion" class="msg-ok">{{ okEdicion }}</p>

            <div class="modal-acciones">
              <button type="button" class="btn-secundario" @click="editando = false">Cancelar</button>
              <button type="submit" class="btn-primario" :disabled="guardando">
                {{ guardando ? 'Guardando...' : 'Guardar cambios' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- ===== MODAL: CONFIRMAR ELIMINACIÓN ===== -->
    <Teleport to="body">
      <div v-if="confirmarEliminacion" class="modal-overlay" @click.self="confirmarEliminacion = false">
        <div class="modal-box">
          <h3 class="modal-titulo">Eliminar cuenta</h3>
          <p class="modal-texto">
            ¿Seguro que deseas eliminar tu cuenta? Esta acción es permanente y
            borrará también tu historial de carritos, órdenes y facturas.
          </p>
          <p v-if="errorEdicion" class="msg-error">{{ errorEdicion }}</p>
          <div class="modal-acciones">
            <button class="btn-secundario" @click="confirmarEliminacion = false">Cancelar</button>
            <button class="btn-peligro" :disabled="eliminando" @click="eliminarCuenta">
              {{ eliminando ? 'Eliminando...' : 'Sí, eliminar' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===== MODAL: CREAR/EDITAR MÉTODO DE PAGO ===== -->
    <Teleport to="body">
      <div v-if="modalPago" class="modal-overlay" @click.self="modalPago = false">
        <div class="modal-box">
          <button class="modal-cerrar" @click="modalPago = false">✕</button>
          <h3 class="modal-titulo">{{ editandoPagoId ? 'Editar método de pago' : 'Agregar método de pago' }}</h3>

          <form class="modal-form" @submit.prevent="guardarPago">
            <label>Método de pago
              <select v-model="formPago.metodo_Pago" required>
                <option value="" disabled>Selecciona un método</option>
                <option value="Visa">Visa</option>
                <option value="MasterCard">MasterCard</option>
                <option value="Débito">Débito</option>
                <option value="Transferencia">Transferencia</option>
              </select>
            </label>
            <label>Número de tarjeta
              <input v-model="formPago.numero_Tarjeta" type="text" placeholder="1234 5678 9012 3456" required />
            </label>
            <label>Fecha de expiración
              <input v-model="formPago.fecha_Expiracion" type="text" placeholder="MM/AA" required />
            </label>

            <p v-if="errorPago" class="msg-error">{{ errorPago }}</p>
            <p v-if="okPago" class="msg-ok">{{ okPago }}</p>

            <div class="modal-acciones">
              <button type="button" class="btn-secundario" @click="modalPago = false">Cancelar</button>
              <button type="submit" class="btn-primario" :disabled="guardandoPago">
                {{ guardandoPago ? 'Guardando...' : (editandoPagoId ? 'Actualizar' : 'Agregar') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- ===== MODAL: CONFIRMAR ELIMINAR MÉTODO DE PAGO ===== -->
    <Teleport to="body">
      <div v-if="confirmarElimPago" class="modal-overlay" @click.self="confirmarElimPago = false">
        <div class="modal-box">
          <h3 class="modal-titulo">Eliminar método de pago</h3>
          <p class="modal-texto">
            ¿Seguro que deseas eliminar este método de pago?
            Esta acción no se puede deshacer.
          </p>
          <p v-if="errorPago" class="msg-error">{{ errorPago }}</p>
          <div class="modal-acciones">
            <button class="btn-secundario" @click="confirmarElimPago = false">Cancelar</button>
            <button class="btn-peligro" :disabled="eliminandoPago" @click="ejecutarEliminarPago">
              {{ eliminandoPago ? 'Eliminando...' : 'Sí, eliminar' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { obtenerEntregasPorUsuario, type InformacionEntregaEntidad } from '@/services/entregaServicio';
import { obtenerDatosPagoPorUsuario, crearDatosPago, actualizarDatosPago, eliminarDatosPago, type DatosDePagoEntidad } from '@/services/datosPagoServicio';
import { usuarioServicio } from '@/services/usuarioServicio';
import { validarRut } from '@/utils/rut';

const authStore = useAuthStore();
const router = useRouter();

const userName = computed(() => authStore.userName || 'Usuario Anónimo');
const userEmail = computed(() => authStore.userEmail || 'No disponible');
const userRole = computed(() => authStore.userRole || 'Sin Rol');
const userRut = computed(() => authStore.userRut || 'No disponible');

const isAdmin = computed(() => {
  const role = (authStore.userRole || '').toLowerCase();
  return role === 'admin' || role === 'administrador' || role === 'role_admin';
});

const userInitial = computed(() => {
  return userName.value.charAt(0).toUpperCase();
});

const entregas = ref<InformacionEntregaEntidad[]>([]);
const cargandoEntregas = ref(false);

const datosPago = ref<DatosDePagoEntidad[]>([]);
const cargandoPago = ref(false);
const pagosExpandidos = ref(false);

const primerPago = computed(() => datosPago.value[0] as DatosDePagoEntidad);

// ===================== EDICIÓN / ELIMINACIÓN DE CUENTA =====================
const editando = ref(false);
const guardando = ref(false);
const confirmarEliminacion = ref(false);
const eliminando = ref(false);
const errorEdicion = ref('');
const okEdicion = ref('');

const form = reactive({
  nombre: '',
  correo: '',
  rut_empresa: '',
  contrasena: '',
});

const abrirEdicion = () => {
  errorEdicion.value = '';
  okEdicion.value = '';
  form.nombre = authStore.userName ?? '';
  form.correo = authStore.userEmail ?? '';
  form.rut_empresa = authStore.userRut ?? '';
  form.contrasena = '';
  editando.value = true;
};

const guardarCambios = async () => {
  errorEdicion.value = '';
  okEdicion.value = '';

  if (!form.nombre.trim()) {
    errorEdicion.value = 'El nombre es obligatorio.';
    return;
  }
  if (!validarRut(form.rut_empresa)) {
    errorEdicion.value = 'El RUT de empresa no es válido. Ejemplo: 76.123.456-7';
    return;
  }

  const id = Number(authStore.userId);
  if (!id) {
    errorEdicion.value = 'No se pudo identificar la cuenta.';
    return;
  }

  guardando.value = true;
  try {
    await usuarioServicio.actualizarCuenta(id, {
      nombre: form.nombre.trim(),
      correo: form.correo.trim(),
      rut_empresa: form.rut_empresa.trim(),
      contrasena: form.contrasena.trim() || undefined,
    });

    // Refresca la sesión con los nuevos datos (conserva token, id y rol).
    authStore.setSession({
      token: authStore.token ?? '',
      userId: String(authStore.userId ?? ''),
      userEmail: form.correo.trim(),
      userName: form.nombre.trim(),
      userRole: authStore.userRole ?? '',
      userRut: form.rut_empresa.trim(),
    });

    okEdicion.value = 'Cuenta actualizada correctamente.';
    setTimeout(() => { editando.value = false; }, 800);
  } catch (err: unknown) {
    const axiosErr = err as { response?: { data?: { error?: string } } };
    errorEdicion.value = axiosErr.response?.data?.error ?? 'No se pudo actualizar la cuenta.';
  } finally {
    guardando.value = false;
  }
};

const eliminarCuenta = async () => {
  errorEdicion.value = '';
  const id = Number(authStore.userId);
  if (!id) {
    errorEdicion.value = 'No se pudo identificar la cuenta.';
    return;
  }

  eliminando.value = true;
  try {
    await usuarioServicio.eliminarCuenta(id);
    authStore.clearSession();
    router.push('/login');
  } catch (err: unknown) {
    const axiosErr = err as { response?: { data?: { error?: string } } };
    errorEdicion.value = axiosErr.response?.data?.error ?? 'No se pudo eliminar la cuenta.';
  } finally {
    eliminando.value = false;
  }
};

const maskCardNumber = (numero: string): string => {
  if (!numero) return '---- ---- ---- ----';
  const clean = numero.replace(/\s/g, '');
  const last4 = clean.slice(-4);
  const masked = clean.slice(0, -4).replace(/./g, '*');
  const full = masked + last4;
  return full.match(/.{1,4}/g)?.join(' ') ?? full;
};

// ===================== CRUD MÉTODOS DE PAGO =====================
const modalPago = ref(false);
const guardandoPago = ref(false);
const editandoPagoId = ref<number | null>(null);
const confirmarElimPago = ref(false);
const eliminandoPago = ref(false);
const pagoAEliminar = ref<DatosDePagoEntidad | null>(null);
const errorPago = ref('');
const okPago = ref('');

const formPago = reactive({
  metodo_Pago: '',
  numero_Tarjeta: '',
  fecha_Expiracion: '',
});

const cargarDatosPago = async () => {
  if (!authStore.userId || isAdmin.value) return;
  cargandoPago.value = true;
  try {
    const resp = await obtenerDatosPagoPorUsuario(authStore.userId);
    datosPago.value = resp.data;
  } catch (error) {
    console.error('Error cargando datos de pago:', error);
  } finally {
    cargandoPago.value = false;
  }
};

const abrirCrearPago = () => {
  editandoPagoId.value = null;
  formPago.metodo_Pago = '';
  formPago.numero_Tarjeta = '';
  formPago.fecha_Expiracion = '';
  errorPago.value = '';
  okPago.value = '';
  modalPago.value = true;
};

const abrirEdicionPago = (pago: DatosDePagoEntidad) => {
  editandoPagoId.value = pago.datos_Pago_ID;
  formPago.metodo_Pago = pago.metodo_Pago;
  formPago.numero_Tarjeta = pago.numero_Tarjeta;
  formPago.fecha_Expiracion = pago.fecha_Expiracion;
  errorPago.value = '';
  okPago.value = '';
  modalPago.value = true;
};

const guardarPago = async () => {
  errorPago.value = '';
  okPago.value = '';

  if (!formPago.metodo_Pago || !formPago.numero_Tarjeta || !formPago.fecha_Expiracion) {
    errorPago.value = 'Todos los campos son requeridos.';
    return;
  }

  guardandoPago.value = true;
  try {
    if (editandoPagoId.value) {
      await actualizarDatosPago(editandoPagoId.value, {
        usuario_ID: Number(authStore.userId),
        metodo_Pago: formPago.metodo_Pago,
        numero_Tarjeta: formPago.numero_Tarjeta,
        fecha_Expiracion: formPago.fecha_Expiracion,
      });
      okPago.value = 'Método de pago actualizado.';
    } else {
      await crearDatosPago({
        usuario_ID: Number(authStore.userId),
        metodo_Pago: formPago.metodo_Pago,
        numero_Tarjeta: formPago.numero_Tarjeta,
        fecha_Expiracion: formPago.fecha_Expiracion,
      });
      okPago.value = 'Método de pago agregado.';
    }
    await cargarDatosPago();
    setTimeout(() => { modalPago.value = false; }, 600);
  } catch (err: unknown) {
    const axiosErr = err as { response?: { data?: { error?: string } } };
    errorPago.value = axiosErr.response?.data?.error ?? 'No se pudo guardar el método de pago.';
  } finally {
    guardandoPago.value = false;
  }
};

const confirmarEliminarPago = (pago: DatosDePagoEntidad) => {
  pagoAEliminar.value = pago;
  errorPago.value = '';
  confirmarElimPago.value = true;
};

const ejecutarEliminarPago = async () => {
  if (!pagoAEliminar.value) return;
  errorPago.value = '';
  eliminandoPago.value = true;
  try {
    await eliminarDatosPago(pagoAEliminar.value.datos_Pago_ID);
    await cargarDatosPago();
    confirmarElimPago.value = false;
  } catch (err: unknown) {
    const axiosErr = err as { response?: { data?: { error?: string } } };
    errorPago.value = axiosErr.response?.data?.error ?? 'No se pudo eliminar el método de pago.';
  } finally {
    eliminandoPago.value = false;
  }
};

onMounted(async () => {
  if (authStore.userId) {
    // Cargar direcciones de entrega
    cargandoEntregas.value = true;
    try {
      const response = await obtenerEntregasPorUsuario(authStore.userId);
      entregas.value = response.data;
    } catch (error) {
      console.error('Error cargando direcciones de entrega:', error);
    } finally {
      cargandoEntregas.value = false;
    }

    // Cargar métodos de pago
    await cargarDatosPago();
  }
});

// Cerrar dropdown al hacer clic fuera
const cerrarDropdownPagos = () => { pagosExpandidos.value = false; };
onMounted(() => { document.addEventListener('click', cerrarDropdownPagos); });
onUnmounted(() => { document.removeEventListener('click', cerrarDropdownPagos); });
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

/* Main Background matching the image */
.page-container {
  height: 100%;
  width: 100%;
  background-color: #ffffff; /* Fondo blanco */
  font-family: 'Inter', sans-serif;
  color: #2b2d42;
}

.dashboard-wrapper {
  width: 100%;
  max-width: 100%;
  display: grid;
  grid-template-columns: 2fr 1fr;
  grid-template-rows: auto auto;
  gap: 20px;
}

/* Base Card Styles */
.card {
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  position: relative;
  border: 1px solid #f0f0f5; /* Borde sutil para que destaquen en el fondo blanco */
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.03);
}

.card-title {
  font-size: 1.15rem;
  font-weight: 700;
  margin-top: 0;
  margin-bottom: 16px;
  color: #2b2d42;
}

/* ===== Top Left: Profile Card ===== */
.profile-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.acciones-perfil {
  position: absolute;
  top: 24px;
  right: 24px;
  display: flex;
  gap: 4px;
}

.edit-icon {
  background: transparent;
  border: none;
  color: #6b4ba3;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
  transition: background 0.2s;
}
.edit-icon:hover {
  background: #f0f0f5;
}

.delete-icon {
  background: transparent;
  border: none;
  color: #d64545;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
  transition: background 0.2s;
}
.delete-icon:hover {
  background: #fdecec;
}

/* ===== MODALES DE CUENTA ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}
.modal-box {
  background: #fff;
  border-radius: 16px;
  padding: 28px;
  width: 100%;
  max-width: 420px;
  position: relative;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
}
.modal-cerrar {
  position: absolute;
  top: 14px;
  right: 16px;
  background: none;
  border: none;
  font-size: 1.1rem;
  cursor: pointer;
  color: #888;
}
.modal-titulo {
  margin: 0 0 16px;
  font-size: 1.2rem;
  font-weight: 700;
  color: #243b55;
}
.modal-texto {
  color: #55687d;
  line-height: 1.5;
  margin-bottom: 20px;
}
.modal-form label {
  display: block;
  margin-bottom: 14px;
  font-size: 0.9rem;
  font-weight: 500;
  color: #34495e;
}
.modal-form label small {
  color: #8a8a9d;
  font-weight: 400;
}
.modal-form input {
  width: 100%;
  margin-top: 6px;
  padding: 10px 12px;
  border: 1px solid #d6e2ed;
  border-radius: 10px;
  font-size: 0.95rem;
  box-sizing: border-box;
}
.msg-error { color: #b02a1f; font-size: 0.88rem; margin: 4px 0; }
.msg-ok { color: #1e5a25; font-size: 0.88rem; margin: 4px 0; }
.modal-acciones {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}
.btn-primario {
  padding: 10px 18px;
  background: #2b6cb0;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
}
.btn-primario:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secundario {
  padding: 10px 18px;
  background: #eef2f7;
  color: #34495e;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
}
.btn-peligro {
  padding: 10px 18px;
  background: #d64545;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
}
.btn-peligro:disabled { opacity: 0.6; cursor: not-allowed; }

.profile-content {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-container {
  flex-shrink: 0;
}

.avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: linear-gradient(135deg, #136692, #61b1dc);
  color: white;
  font-size: 3rem;
  font-weight: 700;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 6px 12px rgba(139, 92, 246, 0.2);
}

.profile-details {
  flex: 1;

}

.user-name {
  font-size: 1.3rem;
  font-weight: 700;
  margin: 0 0 12px 0;
  color: #1e1e2f;
}

.info-row {
  margin-bottom: 8px;
  font-size: 0.9rem;
}

.info-row .label {
  color: #8a8a9d;
  display: inline-block;
  width: 130px;
}

.info-row .value {
  color: #2b2d42;
  font-weight: 500;
}

.social-icons {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.social-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: #6350a2;
  color: white;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 1rem;
  cursor: pointer;
  transition: transform 0.2s;
}
.social-icon:hover {
  transform: scale(1.1);
}


/* ===== Top Right: Payment Card ===== */
.payment-card {
  display: flex;
  flex-direction: column;
}

.payment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.payment-item {
  border: 1px solid #f0f0f5;
  border-radius: 12px;
  padding: 14px 16px;
  background: #fdfdfd;
  transition: box-shadow 0.2s, transform 0.2s;
}

.payment-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transform: translateY(-2px);
}

.payment-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.payment-method-badge {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  background: linear-gradient(135deg, #136692, #61b1dc);
  color: white;
  padding: 4px 10px;
  border-radius: 20px;
  letter-spacing: 0.5px;
}

.payment-expiry {
  font-size: 0.8rem;
  color: #8a8a9d;
}

.card-number {
  background: #f4f5f9;
  padding: 10px 14px;
  border-radius: 10px;
  font-family: monospace;
  font-size: 1rem;
  color: #555;
  letter-spacing: 2px;
}


.payment-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.2s, transform 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.icon-btn:hover {
  transform: scale(1.1);
}

.icon-btn-edit {
  color: #6b4ba3;
}
.icon-btn-edit:hover {
  background: #f0eaff;
}

.icon-btn-delete {
  color: #d64545;
}
.icon-btn-delete:hover {
  background: #fdecec;
}

.dropdown-pagos-wrapper {
  position: relative;
}

.btn-expandir-pagos {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 8px;
  margin-top: 4px;
  background: #f8f9fb;
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  color: #5a6a7d;
  font-weight: 600;
  font-size: 0.82rem;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.btn-expandir-pagos:hover {
  background: #eef3f8;
  color: #136692;
}

.chevron-icon {
  transition: transform 0.3s ease;
}
.chevron-icon.rotated {
  transform: rotate(180deg);
}

.dropdown-pagos-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 50;
  background: #ffffff;
  border: 1px solid #e8ecf1;
  border-radius: 14px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 280px;
  overflow-y: auto;
}

/* Transición del dropdown */
.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.dropdown-fade-enter-from,
.dropdown-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.btn-agregar-pago {
  margin-top: 16px;
  width: 100%;
  padding: 10px;
  background: transparent;
  border: 2px dashed #c5ccd6;
  border-radius: 12px;
  color: #6b7a8d;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}
.btn-agregar-pago:hover {
  border-color: #136692;
  color: #136692;
  background: #f0f8ff;
}

.modal-form select {
  width: 100%;
  margin-top: 6px;
  padding: 10px 12px;
  border: 1px solid #d6e2ed;
  border-radius: 10px;
  font-size: 0.95rem;
  box-sizing: border-box;
  background: #fff;
  color: #2b2d42;
  appearance: auto;
}

/* ===== Bottom Left: Delivery Card ===== */
.delivery-card {
  grid-column: 1 / -1; /* Ocupa todo el ancho en esta fila */
}

.loading-state, .empty-state {
  color: #8a8a9d;
  padding: 20px 0;
  text-align: center;
  font-style: italic;
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.address-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  border: 1px solid #f0f0f5;
  border-radius: 12px;
  background: #fdfdfd;
  transition: box-shadow 0.2s, transform 0.2s;
  position: relative;
}

.address-item:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.04);
  transform: translateY(-2px);
}

.address-icon {
  font-size: 1.5rem;
  background: #f2eeff;
  width: 48px;
  height: 48px;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.address-content {
  flex: 1;
}

.address-content h4 {
  margin: 0 0 6px 0;
  font-size: 1.05rem;
  color: #1e1e2f;
}

.address-content p {
  margin: 0 0 10px 0;
  font-size: 0.85rem;
  color: #8a8a9d;
  line-height: 1.4;
}

.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  background: #e2e8f0;
  color: #475569;
}
.status-badge.completado, .status-badge.entregado { background: #dcfce7; color: #166534; }
.status-badge.pendiente, .status-badge.en_camino { background: #fef9c3; color: #854d0e; }
.status-badge.en_proceso { background: #dbeafe; color: #1e40af; }

.active-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 0.75rem;
  font-weight: 700;
  color: #10b981;
  background: #d1fae5;
  padding: 4px 8px;
  border-radius: 6px;
}

/* Responsive */
@media (max-width: 1024px) {
  .dashboard-wrapper {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .profile-content {
    flex-direction: column;
    text-align: center;
  }
  .info-row .label {
    width: auto;
    margin-right: 8px;
  }
  .social-icons {
    justify-content: center;
  }
  .timeline-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .arrow-btn {
    bottom: 10px;
  }
}
</style>
