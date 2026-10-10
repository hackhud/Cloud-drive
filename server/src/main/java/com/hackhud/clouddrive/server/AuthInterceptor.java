package com.hackhud.clouddrive.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Пропускає до /api/files лише авторизованих користувачів.
 * Токен береться з заголовка Authorization: Bearer ... або з параметра token
 * (потрібно для посилань на завантаження у браузері).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER = "user";

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getParameter("token");
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }
        return auth.userByToken(token).map(user -> {
            request.setAttribute(USER, user);
            return true;
        }).orElseGet(() -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        });
    }

    public static String token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
