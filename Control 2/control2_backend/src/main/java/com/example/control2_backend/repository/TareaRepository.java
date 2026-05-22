package com.example.control2_backend.repository;

import com.example.control2_backend.entity.TareaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TareaRepository extends JpaRepository<TareaEntity, Long> {

    List<TareaEntity> findByUsuarioId(Long usuarioId);

    List<TareaEntity> findByUsuarioIdAndEstadoCompletada(Long usuarioId, Boolean estado);

    @Query("SELECT t FROM TareaEntity t WHERE t.usuario.id = :usuarioId AND " +
           "(LOWER(t.titulo) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.descripcion) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<TareaEntity> findByUsuarioIdAndKeyword(@Param("usuarioId") Long usuarioId,
                                                @Param("keyword") String keyword);

    @Query("SELECT t FROM TareaEntity t WHERE t.usuario.id = :usuarioId AND " +
           "t.estadoCompletada = :estado AND " +
           "(LOWER(t.titulo) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.descripcion) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<TareaEntity> findByUsuarioIdAndEstadoAndKeyword(@Param("usuarioId") Long usuarioId,
                                                         @Param("estado") Boolean estado,
                                                         @Param("keyword") String keyword);

    List<TareaEntity> findByUsuarioIdAndFechaVencimientoBetween(Long usuarioId,
                                                                 LocalDateTime start,
                                                                 LocalDateTime end);

    // Q1: Cuántas tareas ha hecho el usuario por sector
    @Query(nativeQuery = true, value = """
            SELECT s.nombre, COUNT(t.id) AS total
            FROM tareas t
            JOIN sectores s ON t.sector_id = s.id
            WHERE t.usuario_id = :usuarioId
            GROUP BY s.id, s.nombre
            ORDER BY total DESC
            """)
    List<Object[]> countTareasBySectorForUser(@Param("usuarioId") Long usuarioId);

    // Q2: Tarea más cercana al usuario que esté pendiente
    @Query(nativeQuery = true, value = """
            SELECT t.id, t.titulo, t.descripcion, t.fecha_vencimiento, t.estado_completada,
                   t.sector_id, t.usuario_id, s.nombre AS sector_nombre,
                   ST_Distance(
                       geography(s.ubicacion_espacial),
                       geography(u.ubicacion_geografica)
                   ) AS distancia_metros
            FROM tareas t
            JOIN sectores s ON t.sector_id = s.id
            JOIN usuarios u ON u.id = :usuarioId
            WHERE t.estado_completada = false AND t.usuario_id = :usuarioId
            ORDER BY ST_Distance(
                geography(s.ubicacion_espacial),
                geography(u.ubicacion_geografica)
            )
            LIMIT 1
            """)
    List<Object[]> findTareaMasCercana(@Param("usuarioId") Long usuarioId);

    // Q3: Sector con más tareas completadas en radio de 2 km
    @Query(nativeQuery = true, value = """
            SELECT s.nombre, COUNT(t.id) AS total
            FROM tareas t
            JOIN sectores s ON t.sector_id = s.id
            JOIN usuarios u ON u.id = :usuarioId
            WHERE t.estado_completada = true
              AND ST_DWithin(
                  geography(s.ubicacion_espacial),
                  geography(u.ubicacion_geografica),
                  2000
              )
            GROUP BY s.id, s.nombre
            ORDER BY total DESC
            LIMIT 1
            """)
    List<Object[]> findSectorMasTareasCompletadasRadio2km(@Param("usuarioId") Long usuarioId);

    // Q4 & Q8: Promedio de distancia de tareas completadas respecto al usuario
    @Query(nativeQuery = true, value = """
            SELECT AVG(ST_Distance(
                geography(s.ubicacion_espacial),
                geography(u.ubicacion_geografica)
            ))
            FROM tareas t
            JOIN sectores s ON t.sector_id = s.id
            JOIN usuarios u ON u.id = :usuarioId
            WHERE t.estado_completada = true AND t.usuario_id = :usuarioId
            """)
    Double findPromedioDistanciaTareasCompletadas(@Param("usuarioId") Long usuarioId);

    // Q5: Concentración espacial de tareas pendientes por grupos de sectores cercanos
    @Query(nativeQuery = true, value = """
            WITH pendientes AS (
                SELECT t.id, s.nombre, s.ubicacion_espacial
                FROM tareas t
                JOIN sectores s ON t.sector_id = s.id
                WHERE t.estado_completada = false
            ),
            clusters AS (
                SELECT id, nombre,
                       ST_ClusterDBSCAN(
                           ST_Transform(ubicacion_espacial, 3857),
                           eps := 250,
                           minpoints := 1
                       ) OVER () AS cluster_id
                FROM pendientes
            )
            SELECT 'Grupo espacial ' || (cluster_id + 1) || ': ' ||
                   STRING_AGG(DISTINCT nombre, ', ' ORDER BY nombre) AS sector,
                   COUNT(id) AS total
            FROM clusters
            GROUP BY cluster_id
            ORDER BY total DESC
            """)
    List<Object[]> findSectoresConTareasPendientes();

    // Q6: Cuántas tareas ha realizado cada usuario por sector
    @Query(nativeQuery = true, value = """
            SELECT u.username, s.nombre, COUNT(t.id) AS total
            FROM tareas t
            JOIN usuarios u ON t.usuario_id = u.id
            JOIN sectores s ON t.sector_id = s.id
            GROUP BY u.id, u.username, s.id, s.nombre
            ORDER BY u.username, s.nombre
            """)
    List<Object[]> countTareasByUsuarioAndSector();

    // Q7: Sector con más tareas completadas dentro de radio de 5 km
    @Query(nativeQuery = true, value = """
            SELECT s.nombre, COUNT(t.id) AS total
            FROM tareas t
            JOIN sectores s ON t.sector_id = s.id
            JOIN usuarios u ON u.id = :usuarioId
            WHERE t.estado_completada = true
              AND ST_DWithin(
                  geography(s.ubicacion_espacial),
                  geography(u.ubicacion_geografica),
                  5000
              )
            GROUP BY s.id, s.nombre
            ORDER BY total DESC
            LIMIT 1
            """)
    List<Object[]> findSectorMasTareasCompletadasRadio5km(@Param("usuarioId") Long usuarioId);
}
