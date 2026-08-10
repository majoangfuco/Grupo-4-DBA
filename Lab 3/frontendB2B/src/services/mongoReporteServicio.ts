import api from '@/http-common'

// ─── Tarea 4: Aggregation Pipeline ────────────────────────────────────────
/**
 * Un bucket de volumen proyectado que devuelve el endpoint
 * GET /api/reportes/mongo/volumen-proyectado
 * Los límites son: BAJO [0-50k), MEDIO [50k-200k), ALTO (≥200k).
 */
export interface BucketVolumen {
  /** Límite inferior del bucket, o el string del default bucket ("ALTO (>= 200000)") */
  _id: number | string
  /** Cantidad de proyecciones que caen en este bucket */
  cantidad: number
  /** Lista de proyecciones individuales por cliente y categoría */
  proyecciones: ProyeccionVenta[]
}

export interface ProyeccionVenta {
  clienteId: number
  categoria: string
  volumen: number
}

// ─── Tarea 6: Vista Materializada / Change Streams ─────────────────────────
/**
 * Un producto en el ranking de más vendidos.
 * Shape emitido por el $project del pipeline con $merge.
 */
export interface ProductoMasVendido {
  _id: number
  productoId: number
  nombreProducto: string
  unidadesVendidas: number
  montoTotalVendido: number
  ordenesConfirmadas: number
  ultimaVentaEn?: string | null
  actualizadoEn: string
}

// ─── Servicio ──────────────────────────────────────────────────────────────
export const mongoReporteServicio = {
  /**
   * Tarea 4: Aggregation Pipeline
   * Llama a GET /api/reportes/mongo/volumen-proyectado
   * Retorna los buckets BAJO, MEDIO y ALTO con las proyecciones de ventas
   * agrupadas por cliente y categoría, usando $group, $bucket y $sort.
   */
  async obtenerVolumenProyectado(): Promise<BucketVolumen[]> {
    const response = await api.get('/api/reportes/mongo/volumen-proyectado')
    return response.data
  },

  /**
   * Tarea 6: Vista Materializada
   * Llama a GET /api/reportes/mongo/productos-mas-vendidos?limite=N
   * Lee directamente la colección `productos_mas_vendidos`, mantenida
   * al día por el worker de Change Streams.
   */
  async obtenerProductosMasVendidos(limite = 10): Promise<ProductoMasVendido[]> {
    const response = await api.get('/api/reportes/mongo/productos-mas-vendidos', {
      params: { limite },
    })
    return response.data
  },

  /**
   * Tarea 6: Recálculo manual de emergencia
   * Llama a POST /api/reportes/mongo/productos-mas-vendidos/recalcular
   * Ejecuta el pipeline $merge completo sobre todas las órdenes CONFIRMADAS.
   * En operación normal no es necesario: el Change Stream lo mantiene al día.
   */
  async recalcularProductosMasVendidos(): Promise<{ productosEnRanking: number; mensaje: string }> {
    const response = await api.post('/api/reportes/mongo/productos-mas-vendidos/recalcular')
    return response.data
  },
}
