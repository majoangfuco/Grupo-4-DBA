// TODO (equipo): ajustar el import de abajo si '@/http-common' exporta
// distinto (default export vs. nombrado, u otro nombre de instancia).
import http from '@/http-common'

export interface ItemCarritoMongoRequest {
  productoId: number
  cantidad: number
}

export const carritoMongoServicio = {
  agregarItem(clienteId: number | string, item: ItemCarritoMongoRequest) {
    return http.post(`/api/mongo/carritos/${clienteId}/items`, item)
  },

  obtenerPorCliente(clienteId: number | string) {
    return http.get(`/api/mongo/carritos/${clienteId}`)
  },

  // Configuración de cantidad mínima B2B por producto (gestionada por el
  // Admin, 100% en Mongo — no toca producto_entidad de Postgres).
  establecerCantidadMinima(productoId: number, cantidadMinimaB2B: number) {
    return http.put(`/api/mongo/carritos/config-productos/${productoId}`, cantidadMinimaB2B)
  },

  obtenerCantidadMinima(productoId: number) {
    return http.get(`/api/mongo/carritos/config-productos/${productoId}`)
  },
}
