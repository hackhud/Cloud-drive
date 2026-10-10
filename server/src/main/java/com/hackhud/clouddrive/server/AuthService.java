package com.hackhud.clouddrive.server;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public String register(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Логін: 3–32 символи (латиниця, цифри, _ . -)");
        }
        if (password == null || password.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль: мінімум 4 символи");
        }
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Користувач уже існує");
        }
        users.save(new UserAccount(username, encoder.encode(password)));
        return openSession(username);
    }

    public String login(String username, String password) {
        UserAccount user = users.findByUsername(username)
                .filter(u -> password != null && encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Невірний логін або пароль"));
        return openSession(user.getUsername());
    }

    public void logout(String token) {
        sessions.remove(token);
    }

    public Optional<String> userByToken(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sessions.get(token));
    }

    private String openSession(String username) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, username);
        return token;
    }
}
