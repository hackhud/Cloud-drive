package com.hackhud.clouddrive.server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String username, String password) {
    }

    public record Session(String token, String username) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public Session register(@RequestBody Credentials c) {
        return new Session(auth.register(c.username(), c.password()), c.username());
    }

    @PostMapping("/login")
    public Session login(@RequestBody Credentials c) {
        return new Session(auth.login(c.username(), c.password()), c.username());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        auth.logout(AuthInterceptor.token(request));
    }
}
