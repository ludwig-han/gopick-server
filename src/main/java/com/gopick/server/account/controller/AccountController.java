package com.gopick.server.account.controller;

import com.gopick.server.account.dto.CreateAccountRequest;

import com.gopick.server.account.dto.CurrentUserResponse;
import com.gopick.server.account.dto.LoginAccountRequest;
import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.account.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<CurrentUserResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest createAccountRequest,
            HttpServletRequest request) {

        CurrentUserResponse response = accountService.createAccount(
                createAccountRequest.getEmail(), createAccountRequest.getPassword()
        );

        HttpSession session = request.getSession();
        session.setAttribute("accountId", response.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getMe(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("accountId") == null) {
            throw new UnauthenticatedException();
        }

        Long accountId = (Long) session.getAttribute("accountId");

        CurrentUserResponse response = accountService.getCurrentUser(accountId);

        return ResponseEntity.ok(response);
    }
}
