import httpClient from '@/http-common'

export interface Cliente {
  usuario_ID: number
  nombre_Usuario: string
  correo: string
  rut_Empresa: string
  ultima_Compra: string | number | null
}

const obtenerClientes = () => httpClient.get('/usuario/clientes')

export const usuarioServicio = {
  obtenerClientes,
}
