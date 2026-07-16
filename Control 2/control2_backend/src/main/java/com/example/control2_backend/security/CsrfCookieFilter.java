package com.example.control2_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que fuerza la escritura del token CSRF como cookie en CADA respuesta.
 *
 * Problema que resuelve:
 *   Spring Security 6 usa "deferred CSRF tokens" por defecto: el token solo se
 *   genera y la cookie solo se escribe cuando alguien ACCEDE al token durante
 *   el procesamiento de la petición. Sin este filtro, la cookie XSRF-TOKEN
 *   nunca llegaría al cliente en peticiones GET normales.
 *
 * Cómo funciona el patrón Double-Submit Cookie:
 *   1. Spring genera un token CSRF y lo envía como cookie "XSRF-TOKEN" (legible por JS).
 *   2. Axios lo lee de document.cookie y lo reenvía en el header "X-XSRF-TOKEN".
 *   3. Spring compara cookie vs. header → si coinciden, la petición es legítima.
 *   4. Un atacante no puede leer la cookie de otro dominio (Same-Origin Policy),
 *      por lo tanto no puede incluir el header correcto → ataque bloqueado.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Acceder al token fuerza que Spring lo genere y escriba la cookie XSRF-TOKEN
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // Materializa el token diferido → escribe la cookie
        }

        filterChain.doFilter(request, response);
    }
}
