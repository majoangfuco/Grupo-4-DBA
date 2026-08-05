package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.ConfiguracionEnvioEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConfiguracionEnvioRepositorio {

    private final JdbcTemplate jdbc;

    public ConfiguracionEnvioRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ConfiguracionEnvioEntidad> rowMapper = (rs, rowNum) -> {
        ConfiguracionEnvioEntidad c = new ConfiguracionEnvioEntidad();
        c.setConfigId(rs.getLong("config_id"));
        c.setValorKm(rs.getDouble("valor_km"));
        return c;
    };

    // Devuelve la única fila de configuración; la crea con valor 0 si no existe.
    public ConfiguracionEnvioEntidad obtener() {
        List<ConfiguracionEnvioEntidad> result = jdbc.query(
                "SELECT config_id, valor_km FROM configuracion_envio_entidad ORDER BY config_id LIMIT 1",
                rowMapper
        );
        if (!result.isEmpty()) {
            return result.get(0);
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO configuracion_envio_entidad (valor_km) VALUES (0) RETURNING config_id",
                Long.class
        );
        return new ConfiguracionEnvioEntidad(id, 0.0);
    }

    // Actualiza (o crea) el valor por km. Mantiene una sola fila.
    public ConfiguracionEnvioEntidad actualizarValorKm(double valorKm) {
        ConfiguracionEnvioEntidad actual = obtener();
        jdbc.update(
                "UPDATE configuracion_envio_entidad SET valor_km = ? WHERE config_id = ?",
                valorKm, actual.getConfigId()
        );
        actual.setValorKm(valorKm);
        return actual;
    }
}
