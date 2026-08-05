import httpClient from '@/http-common'

export interface ConfiguracionEnvio {
  configId: number
  valorKm: number
}

const obtener = () => httpClient.get<ConfiguracionEnvio>(`/api/config/envio`)

const actualizar = (valorKm: number) =>
  httpClient.put<ConfiguracionEnvio>(`/api/config/envio`, { valorKm })

export const configEnvioServicio = {
  obtener,
  actualizar,
}
