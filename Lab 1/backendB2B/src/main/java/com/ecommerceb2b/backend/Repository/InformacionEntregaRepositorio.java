package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InformacionEntregaRepositorio {

    private final JdbcTemplate jdbc;

    public InformacionEntregaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<InformacionEntregaEntidad> rowMapper = (rs, rowNum) -> {
        InformacionEntregaEntidad e = new InformacionEntregaEntidad();
        e.setInfo_Entrega_ID(rs.getLong("info_entrega_id"));
        e.setUsuarioId(rs.getLong("usuario_usuario"));
        e.setOrdenId(rs.getLong("orden_orden_id"));
        e.setDireccion(rs.getString("direccion"));
        e.setNumero(rs.getString("numero"));
        e.setRut_Recibe_Entrega(rs.getString("rut_recibe_entrega"));
        e.setRut_Empresa(rs.getString("rut_empresa"));
        e.setEstado_Entrega(rs.getString("estado_entrega"));
        e.setActiva(rs.getBoolean("activa"));
        return e;
    };

    public List<InformacionEntregaEntidad> findAllActivas() {
        return jdbc.query(
                "SELECT * FROM informacion_entrega_entidad WHERE activa = true",
                rowMapper
        );
    }

    public Optional<InformacionEntregaEntidad> findById(Long id) {
        List<InformacionEntregaEntidad> result = jdbc.query(
                "SELECT * FROM informacion_entrega_entidad WHERE info_entrega_id = ? AND activa = true",
                rowMapper, id
        );
        return result.stream().findFirst();
    }

    public List<InformacionEntregaEntidad> findByUsuarioId(Long usuarioId) {
        return jdbc.query(
                "SELECT * FROM informacion_entrega_entidad WHERE usuario_usuario = ? AND activa = true",
                rowMapper, usuarioId
        );
    }

    public int save(InformacionEntregaEntidad e) {
        return jdbc.update(
                """
                INSERT INTO informacion_entrega_entidad
                    (usuario_usuario, orden_orden_id, direccion, numero,
                     rut_recibe_entrega, rut_empresa, estado_entrega, activa)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                e.getUsuarioId(), e.getOrdenId(), e.getDireccion(), e.getNumero(),
                e.getRut_Recibe_Entrega(), e.getRut_Empresa(),
                e.getEstado_Entrega() != null ? e.getEstado_Entrega() : "PENDIENTE",
                true
        );
    }

    public int update(InformacionEntregaEntidad e) {
        return jdbc.update(
                """
                UPDATE informacion_entrega_entidad
                SET direccion = ?, numero = ?, rut_recibe_entrega = ?,
                    rut_empresa = ?, estado_entrega = ?
                WHERE info_entrega_id = ? AND activa = true
                """,
                e.getDireccion(), e.getNumero(), e.getRut_Recibe_Entrega(),
                e.getRut_Empresa(), e.getEstado_Entrega(), e.getInfo_Entrega_ID()
        );
    }

    public int softDelete(Long id) {
        return jdbc.update(
                "UPDATE informacion_entrega_entidad SET activa = false WHERE info_entrega_id = ?",
                id
        );
    }
}