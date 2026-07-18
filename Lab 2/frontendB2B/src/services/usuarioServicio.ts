import httpClient from '@/http-common'

export interface Cliente {
  usuario_ID: number
  nombre_Usuario: string
  correo: string
  rut_Empresa: string
  ultima_Compra: string | number | null
}

export interface ActualizarCuentaPayload {
  nombre: string
  correo: string
  rut_empresa: string
  contrasena?: string
}

const obtenerClientes = () => httpClient.get('/usuario/clientes')

const actualizarCuenta = (id: number, payload: ActualizarCuentaPayload) =>
  httpClient.put(`/usuario/${id}`, payload)

const eliminarCuenta = (id: number) => httpClient.delete(`/usuario/${id}`)

export const usuarioServicio = {
  obtenerClientes,
  actualizarCuenta,
  eliminarCuenta,
}
