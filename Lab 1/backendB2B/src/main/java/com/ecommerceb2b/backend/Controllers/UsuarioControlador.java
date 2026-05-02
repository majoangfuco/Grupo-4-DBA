package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Config.JwtMiddlewareService;
import com.ecommerceb2b.backend.Entities.UsuarioEntidad;
import com.ecommerceb2b.backend.Services.UsuarioServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/usuario")
@CrossOrigin(origins = "*")
public class UsuarioControlador {

    private final UsuarioServicio usuarioServicio;
    private final JwtMiddlewareService jwtMiddlewareService;

    public UsuarioControlador(UsuarioServicio usuarioServicio, JwtMiddlewareService jwtMiddlewareService) {
        this.usuarioServicio = usuarioServicio;
        this.jwtMiddlewareService = jwtMiddlewareService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credenciales) {
        Map<String, Object> response = new HashMap<>();
        
        String correo = credenciales.get("correo");
        String contrasena = credenciales.get("contrasena");

        System.out.println("[LOGIN] correo=" + correo + " contraseña_proporcionada=" + (contrasena != null ? "****" : "null"));

        if (correo == null || contrasena == null) {
            response.put("error", "Correo y contraseña son requeridos");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<String> token = usuarioServicio.login(correo, contrasena);

        if (token.isPresent()) {
            response.put("token", token.get());
            response.put("mensaje", "Login exitoso");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Credenciales inválidas");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody Map<String, String> datosRegistro) {
        Map<String, Object> response = new HashMap<>();
        
        String nombre = datosRegistro.get("nombre");
        String correo = datosRegistro.get("correo");
        String contrasena = datosRegistro.get("contrasena");
        String rutEmpresa = datosRegistro.get("rut_empresa");

        if (nombre == null || correo == null || contrasena == null || rutEmpresa == null) {
            response.put("error", "Todos los campos son requeridos");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            usuarioServicio.registrar(nombre, correo, contrasena, rutEmpresa);
            response.put("mensaje", "Usuario registrado exitosamente");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<Map<String, Object>> buscarUsuario(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("error", "Token no proporcionado o formato inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = authHeader.substring(7);

        if (!jwtMiddlewareService.validateToken(token)) {
            response.put("error", "Token inválido o expirado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Long usuarioId = jwtMiddlewareService.getUsuarioIdFromToken(token);
        Optional<UsuarioEntidad> usuario = usuarioServicio.obtenerUsuarioPorId(usuarioId);

        if (usuario.isPresent()) {
            UsuarioEntidad u = usuario.get();
            response.put("usuario_id", u.getUsuario_ID());
            response.put("nombre", u.getNombre_Usuario());
            response.put("correo", u.getCorreo());
            response.put("rol", u.getRol());
            response.put("rut_empresa", u.getRut_Empresa());
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Usuario no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/clientes")
    public ResponseEntity<Map<String, Object>> obtenerClientes(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("error", "Token no proporcionado o formato inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = authHeader.substring(7);

        if (!jwtMiddlewareService.validateToken(token)) {
            response.put("error", "Token inválido o expirado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            List<UsuarioEntidad> clientes = usuarioServicio.obtenerUsuariosPorRol("CLIENTE");
            response.put("clientes", clientes.stream().map(u -> Map.of(
                "usuario_ID", u.getUsuario_ID(),
                "nombre_Usuario", u.getNombre_Usuario(),
                "correo", u.getCorreo(),
                "rut_Empresa", u.getRut_Empresa()
            )).toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Error al obtener clientes");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
