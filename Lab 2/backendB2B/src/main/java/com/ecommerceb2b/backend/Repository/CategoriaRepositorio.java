package com.ecommerceb2b.backend.Repository;

import com.ecommerceb2b.backend.Entities.CategoriaEntidad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CategoriaRepositorio {

	private final JdbcTemplate jdbcTemplate;

	public CategoriaRepositorio(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private final RowMapper<CategoriaEntidad> rowMapper = (rs, rowNum) -> {
		CategoriaEntidad c = new CategoriaEntidad();
		c.setCategoria_ID(rs.getInt("categoria_id"));
		c.setNombre_Categoria(rs.getString("nombre_categoria"));
		c.setEstado_Categoria(rs.getBoolean("estado_categoria"));
		c.setRestringida_Zona_Residencial(rs.getBoolean("restringida_zona_residencial"));
		return c;
	};

	public int crear(CategoriaEntidad categoria) {
		String sql = """
			INSERT INTO categoria_entidad (nombre_categoria, estado_categoria)
			VALUES (?, ?)
			""";
		return jdbcTemplate.update(sql, categoria.getNombre_Categoria(), categoria.isEstado_Categoria());
	}

	public List<CategoriaEntidad> encontrarTodas() {
		String sql = "SELECT * FROM categoria_entidad WHERE estado_categoria = true";
		return jdbcTemplate.query(sql, rowMapper);
	}

	public List<CategoriaEntidad> encontrarTodasIncluyendoInactivas() {
		String sql = "SELECT * FROM categoria_entidad";
		return jdbcTemplate.query(sql, rowMapper);
	}

	public Optional<CategoriaEntidad> encontrarPorId(Long id) {
		String sql = "SELECT * FROM categoria_entidad WHERE categoria_id = ?";
		List<CategoriaEntidad> result = jdbcTemplate.query(sql, rowMapper, id);
		return result.stream().findFirst();
	}

	public int actualizar(CategoriaEntidad categoria) {
		String sql = """
			UPDATE categoria_entidad
			SET nombre_categoria = ?, estado_categoria = ?
			WHERE categoria_id = ?
			""";
		return jdbcTemplate.update(sql,
				categoria.getNombre_Categoria(),
				categoria.isEstado_Categoria(),
				categoria.getCategoria_ID());
	}

	public int eliminar(Long id) {
		String sql = """
			UPDATE categoria_entidad
			SET estado_categoria = false
			WHERE categoria_id = ?
			""";
		return jdbcTemplate.update(sql, id);
	}

	public List<CategoriaEntidad> buscarPorNombre(String nombre) {
		String sql = """
			SELECT * FROM categoria_entidad
			WHERE estado_categoria = true
			  AND nombre_categoria ILIKE ?
			""";
		String patron = "%" + nombre + "%";
		return jdbcTemplate.query(sql, rowMapper, patron);
	}

	public List<CategoriaEntidad> buscarPorNombreIncluyendoInactivas(String nombre) {
		String sql = """
			SELECT * FROM categoria_entidad
			WHERE nombre_categoria ILIKE ?
			""";
		String patron = "%" + nombre + "%";
		return jdbcTemplate.query(sql, rowMapper, patron);
	}

	public int actualizarRestringidaZonaResidencial(Long id, boolean restringida) {
		String sql = """
			UPDATE categoria_entidad
			SET restringida_zona_residencial = ?
			WHERE categoria_id = ?
			""";
		return jdbcTemplate.update(sql, restringida, id);
	}

	// Categorías activas marcadas como restringidas en zona residencial —
	// usado por la verificación de dirección (Parte 4) para listar qué
	// categorías quedarían bloqueadas si el punto cae en una zona protegida.
	public List<CategoriaEntidad> listarRestringidasZonaResidencial() {
		String sql = """
			SELECT * FROM categoria_entidad
			WHERE restringida_zona_residencial = true
			  AND estado_categoria = true
			""";
		return jdbcTemplate.query(sql, rowMapper);
	}
}
