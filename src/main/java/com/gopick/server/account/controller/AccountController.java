package com.gopick.server.account.controller;

import com.gopick.server.account.dto.CreateAccountRequest;

import com.gopick.server.account.dto.CurrentUserResponse;
import com.gopick.server.account.dto.LoginAccountRequest;
import com.gopick.server.account.service.AccountService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody CreateAccountRequest createAccountRequest, HttpSession session) {

        CurrentUserResponse response = accountService.createAccount(
                createAccountRequest.getEmail(), createAccountRequest.getPassword()
        );

        session.setAttribute("accountId", response.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
