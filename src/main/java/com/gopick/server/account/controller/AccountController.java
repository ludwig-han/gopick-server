package com.gopick.server.account.controller;

import com.gopick.server.account.dto.CreateAccountRequest;
import com.gopick.server.account.dto.CreateAccountResponse;
import com.gopick.server.account.service.AccountService;
import jakarta.validation.Valid;
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
    public CreateAccountResponse createAccount(@Valid @RequestBody CreateAccountRequest createAccountRequest) {
        return accountService.createAccount(createAccountRequest.getEmail(), createAccountRequest.getPassword());
    }
}
