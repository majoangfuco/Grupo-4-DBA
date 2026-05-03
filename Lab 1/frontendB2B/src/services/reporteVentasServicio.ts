import api from '@/http-common'

/**
 * Servicio para consultar reportes de ventas desde el backend
 * Utiliza la vista materializada vw_ventas_mensuales_por_categoria
 */
export const reporteVentasServicio = {
  
  /**
   * Obtiene todos los reportes de ventas mensuales consolidados por categoría
   * @returns Lista de reportes con mes/año, categoría, cantidad de órdenes y total
   */
  async obtenerTodosLosReportes() {
    try {
      const response = await api.get('/reportes/ventas')
      return response.data
    } catch (error) {
      console.error('Error al obtener reportes:', error)
      throw error
    }
  },

  /**
   * Obtiene reportes de un mes específico
   * @param mesAno Formato YYYY-MM (ej: 2024-03)
   * @returns Reportes filtrados por mes y año
   */
  async obtenerPorMesAno(mesAno: string) {
    try {
      const response = await api.get('/reportes/ventas/mes', {
        params: { mesAno }
      })
      return response.data
    } catch (error) {
      console.error('Error al obtener reportes por mes:', error)
      throw error
    }
  },

  /**
   * Obtiene reportes de una categoría específica
   * @param nombre Nombre de la categoría
   * @returns Reportes filtrados por categoría
   */
  async obtenerPorCategoria(nombre: string) {
    try {
      const response = await api.get('/reportes/ventas/categoria', {
        params: { nombre }
      })
      return response.data
    } catch (error) {
      console.error('Error al obtener reportes por categoría:', error)
      throw error
    }
  },

  /**
   * Obtiene reportes de un año específico
   * @param anio Año (ej: 2024)
   * @returns Reportes filtrados por año
   */
  async obtenerPorAnio(anio: number) {
    try {
      const response = await api.get('/reportes/ventas/anio', {
        params: { anio }
      })
      return response.data
    } catch (error) {
      console.error('Error al obtener reportes por año:', error)
      throw error
    }
  },

  /**
   * Obtiene el total consolidado de todas las ventas
   * Útil para KPIs y métricas generales
   * @returns Objeto con totales: cantidadOrdenes, cantidadProductos, totalVendido, precioPromedio
   */
  async obtenerTotalConsolidado() {
    try {
      const response = await api.get('/reportes/ventas/total')
      return response.data
    } catch (error) {
      console.error('Error al obtener total consolidado:', error)
      throw error
    }
  },

  /**
   * Refresca la vista materializada (endpoint administrativo)
   * Ejecutar después de cambios significativos en órdenes o productos
   */
  async refrescarReportes() {
    try {
      const response = await api.post('/reportes/refrescar')
      return response.data
    } catch (error) {
      console.error('Error al refrescar reportes:', error)
      throw error
    }
  }
}

/**
 * Tipo de dato para los reportes
 */
export interface ReporteVentas {
  mesAno: string              // Formato: YYYY-MM
  anio: number               // Año
  mes: number                // Mes (1-12)
  nombreCategoria: string    // Nombre de la categoría
  cantidadOrdenes: number    // Cantidad de órdenes en el mes
  cantidadProductos: number  // Total de productos vendidos
  totalVendido: number       // Total de dinero vendido
  precioPromedio: number     // Precio promedio
}
