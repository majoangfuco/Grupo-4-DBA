package com.example.control2_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    //Configuración principal de las rutas y filtros
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Habilitamos CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Desactivamos CSRF
                // BORRAR DESPUÉS EXPLICACIÓN: Un ataque CSRF ocurre cuando un sitio malicioso engaña al navegador del usuario para que envíe una petición usando las Cookies de Sesión que el navegador guarda automáticamente. Como esta API REST que estamos construyendo está configurada como STATELESS (sin estado) y la autenticación se hará enviando el token JWT explícitamente en la cabecera Authorization: Bearer <token> (y no en cookies automáticas), la vulnerabilidad CSRF queda mitigada por diseño. Es el estándar de la industria desactivar la protección CSRF nativa de Spring cuando se usan tokens JWT de esta manera.
                .csrf(csrf -> csrf.disable())

                // Configuramos la aplicación para que no guarde estado (Stateless) porque usaremos JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // proctección de las rutas
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Login y Registro públicos
                        .anyRequest().authenticated() // Cualquier otra ruta (tareas, sectores) requiere estar logueado
                );

        // TO DO:  agregar el filtro JWT más adelante (http.addFilterBefore(...))

        return http.build();
    }

    //Encriptador de contraseñas (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //AuthenticationManager para poder inyectarlo en el AuthController
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // Configuración del CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // En desarrollo permitimos todos los orígenes, en producción pondrías la URL de tu Vue.js
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}