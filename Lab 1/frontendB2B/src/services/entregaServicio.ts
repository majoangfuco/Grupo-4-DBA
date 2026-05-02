import httpClient from '@/http-common'

export interface InformacionEntregaEntidad {
    info_Entrega_ID: number
    usuarioId: number
    ordenId: number
    direccion: string
    numero: string
    rut_Recibe_Entrega: string
    rut_Empresa: string
    estado_Entrega: string
    activa: boolean
}

export const obtenerEntregasPorUsuario = (usuarioId: string | number) => {
    return httpClient.get<InformacionEntregaEntidad[]>(`/api/entregas/usuario/${usuarioId}`)
}
