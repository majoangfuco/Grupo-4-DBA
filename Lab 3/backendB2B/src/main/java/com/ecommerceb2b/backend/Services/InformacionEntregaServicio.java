package com.ecommerceb2b.backend.Services;

import com.ecommerceb2b.backend.Entities.InformacionEntregaEntidad;
import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import com.ecommerceb2b.backend.Repository.InformacionEntregaRepositorio;
import com.ecommerceb2b.backend.Repository.UsuarioRepositorio;
import com.ecommerceb2b.backend.Util.CoordenadasNormalizador;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InformacionEntregaServicio {

    private final InformacionEntregaRepositorio repositorio;
    private final UsuarioRepositorio usuarioRepositorio;

    public InformacionEntregaServicio(
            InformacionEntregaRepositorio repositorio,
            UsuarioRepositorio usuarioRepositorio
    ) {
        this.repositorio = repositorio;
        this.usuarioRepositorio = usuarioRepositorio;
    }

    public List<InformacionEntregaEntidad> obtenerTodas() {
        return repositorio.findAllActivas();
    }

    public InformacionEntregaEntidad obtenerPorId(Long id) {
        return repositorio.findById(id)
                .orElseThrow(
                        () -> new RuntimeException(
                                "Entrega no encontrada con ID: " + id
                        )
                );
    }

    public List<InformacionEntregaEntidad> obtenerPorUsuario(Long usuarioId) {
        return repositorio.findByUsuarioId(usuarioId);
    }

    public List<String> obtenerComunasDisponibles() {
        return repositorio.findComunasDisponibles();
    }

    @Transactional
    public String crear(InformacionEntregaEntidad entrega) {
        prepararYValidarEntrega(entrega);

        try {
            int filas = repositorio.save(entrega);
            if (filas == 0) {
                throw new IllegalStateException(
                        "No se pudo guardar la información de entrega"
                );
            }
        } catch (DataAccessException e) {
            throw new IllegalArgumentException(extraerMensajeBaseDatos(e));
        }

        return "Información de entrega creada correctamente";
    }

    @Transactional
    public String actualizar(
            Long id,
            InformacionEntregaEntidad entrega
    ) {
        InformacionEntregaEntidad actual = repositorio.findById(id)
                .orElseThrow(
                        () -> new RuntimeException(
                                "No existe la entrega con ID: " + id
                        )
                );

        entrega.setInfo_Entrega_ID(id);
        entrega.setUsuarioId(actual.getUsuarioId());
        entrega.setOrdenId(actual.getOrdenId());

        if (entrega.getEstado_Entrega() == null) {
            entrega.setEstado_Entrega(actual.getEstado_Entrega());
        }
        if (entrega.getActiva() == null) {
            entrega.setActiva(actual.getActiva());
        }

        prepararYValidarEntrega(entrega);

        try {
            int filas = repositorio.update(entrega);
            if (filas == 0) {
                throw new IllegalStateException(
                        "No se pudo actualizar la información de entrega"
                );
            }
        } catch (DataAccessException e) {
            throw new IllegalArgumentException(extraerMensajeBaseDatos(e));
        }

        return "Información de entrega actualizada correctamente";
    }

    public String eliminar(Long id) {
        repositorio.findById(id)
                .orElseThrow(
                        () -> new RuntimeException(
                                "No existe la entrega con ID: " + id
                        )
                );

        repositorio.softDelete(id);
        return "Entrega desactivada correctamente";
    }

    public String findAllAsGeoJson() {
        return repositorio.findAllAsGeoJson();
    }

    private void prepararYValidarEntrega(
            InformacionEntregaEntidad entrega
    ) {
        if (entrega == null) {
            throw new IllegalArgumentException(
                    "La información de entrega es obligatoria"
            );
        }

        if (entrega.getUsuarioId() == null || entrega.getUsuarioId() <= 0) {
            throw new IllegalArgumentException(
                    "El usuario de la entrega es obligatorio"
            );
        }

        UsuarioEntidad usuario = usuarioRepositorio
                .findById(entrega.getUsuarioId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "El usuario de la entrega no existe"
                        )
                );

        // El RUT de empresa ya no se solicita en el formulario.
        // Se obtiene desde el usuario registrado para evitar manipulación.
        entrega.setRut_Empresa(usuario.getRut_Empresa());

        if (entrega.getDireccion() == null
                || entrega.getDireccion().isBlank()) {
            throw new IllegalArgumentException(
                    "La dirección es obligatoria"
            );
        }

        if (entrega.getNumero() == null
                || entrega.getNumero().isBlank()) {
            throw new IllegalArgumentException(
                    "El número de la dirección es obligatorio"
            );
        }

        if (entrega.getRut_Recibe_Entrega() == null
                || entrega.getRut_Recibe_Entrega().isBlank()) {
            throw new IllegalArgumentException(
                    "El RUT de quien recibe es obligatorio"
            );
        }

        if (entrega.getComuna() == null
                || entrega.getComuna().isBlank()) {
            throw new IllegalArgumentException(
                    "La comuna es obligatoria"
            );
        }

        // Acepta tanto { latitud, longitud } plano como GeoJSON Point
        // { type: "Point", coordinates: [lon, lat] } antes de validar rango.
        CoordenadasNormalizador.Coordenadas coords = CoordenadasNormalizador.normalizar(
                entrega.getLatitud(),
                entrega.getLongitud(),
                entrega.getType(),
                entrega.getCoordinates()
        );
        entrega.setLatitud(coords.latitud());
        entrega.setLongitud(coords.longitud());

        validarCoordenadas(
                entrega.getLongitud(),
                entrega.getLatitud()
        );

        if (entrega.getActiva() == null) {
            entrega.setActiva(Boolean.TRUE);
        }

        if (entrega.getEstado_Entrega() == null
                || entrega.getEstado_Entrega().isBlank()) {
            entrega.setEstado_Entrega("PENDIENTE");
        }
    }

    private void validarCoordenadas(
            Double longitud,
            Double latitud
    ) {
        if (longitud == null || latitud == null) {
            throw new IllegalArgumentException(
                    "La ubicación geográfica es obligatoria"
            );
        }

        if (!Double.isFinite(longitud)
                || longitud < -180
                || longitud > 180) {
            throw new IllegalArgumentException(
                    "La longitud debe estar entre -180 y 180"
            );
        }

        if (!Double.isFinite(latitud)
                || latitud < -90
                || latitud > 90) {
            throw new IllegalArgumentException(
                    "La latitud debe estar entre -90 y 90"
            );
        }
    }

    private String extraerMensajeBaseDatos(Throwable error) {
        Throwable causa = error;

        while (causa.getCause() != null) {
            causa = causa.getCause();
        }

        String mensaje = causa.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            return "No se pudo guardar la información de entrega";
        }

        int posicionError = mensaje.indexOf("ERROR:");
        if (posicionError >= 0) {
            mensaje = mensaje
                    .substring(posicionError + "ERROR:".length())
                    .split("\\n")[0]
                    .trim();
        }

        return mensaje;
    }
}
