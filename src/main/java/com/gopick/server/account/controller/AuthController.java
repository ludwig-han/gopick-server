package com.gopick.server.account.controller;

import com.gopick.server.account.dto.CurrentUserResponse;
import com.gopick.server.account.dto.LoginAccountRequest;
import com.gopick.server.account.service.AuthService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<CurrentUserResponse> login(
            @Valid @RequestBody LoginAccountRequest loginAccountRequest, HttpSession session) {
        CurrentUserResponse response = authService.login(loginAccountRequest.getEmail(), loginAccountRequest.getPassword());

        session.setAttribute("accountId", response.getAccountId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if  (session != null) {
            session.invalidate();
        }

        return ResponseEntity.noContent().build();
    }
}
