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
        e.setEstado_Entrega(null);
        e.setActiva(true);
        return e;
    };

    public List<InformacionEntregaEntidad> findAllActivas() {
        return jdbc.query(
                "SELECT * FROM informacion_entrega_entidad",
                rowMapper
        );
    }

    public Optional<InformacionEntregaEntidad> findById(Long id) {
        List<InformacionEntregaEntidad> result = jdbc.query(
                "SELECT * FROM informacion_entrega_entidad WHERE info_entrega_id = ?",
                rowMapper, id
        );
        return result.stream().findFirst();
    }

    public List<InformacionEntregaEntidad> findByUsuarioId(Long usuarioId) {
        return jdbc.query(
                "SELECT * FROM informacion_entrega_entidad WHERE usuario_usuario = ?",
                rowMapper, usuarioId
        );
    }

    public int save(InformacionEntregaEntidad e) {
        return jdbc.update(
                """
                INSERT INTO informacion_entrega_entidad
                    (usuario_usuario, orden_orden_id, direccion, numero,
                     rut_recibe_entrega, rut_empresa)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                e.getUsuarioId(), e.getOrdenId(), e.getDireccion(), e.getNumero(),
                e.getRut_Recibe_Entrega(), e.getRut_Empresa()
        );
    }

    public int update(InformacionEntregaEntidad e) {
        return jdbc.update(
                """
                UPDATE informacion_entrega_entidad
                SET direccion = ?, numero = ?, rut_recibe_entrega = ?,
                    rut_empresa = ?
                WHERE info_entrega_id = ?
                """,
                e.getDireccion(), e.getNumero(), e.getRut_Recibe_Entrega(),
                e.getRut_Empresa(), e.getInfo_Entrega_ID()
        );
    }

    public int softDelete(Long id) {
        return jdbc.update(
                "DELETE FROM informacion_entrega_entidad WHERE info_entrega_id = ?",
                id
        );
    }
}